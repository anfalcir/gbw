from __future__ import annotations

import html
import logging
import re
import statistics
import unicodedata
import urllib.parse
import urllib.request
from difflib import SequenceMatcher

from gbw.core.runner import CommandRunner, CommandError
from gbw.core.system import tool_path
from gbw.models import SourceCandidate


def norm_text(value: str) -> str:
    s = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def text_similarity(a: str, b: str) -> float:
    aa, bb = norm_text(a), norm_text(b)
    return SequenceMatcher(None, aa, bb).ratio() if aa and bb else 0.0


TITLE_STOPWORDS = {
    "the", "a", "an", "of", "and", "or", "to", "in", "on", "at", "for", "from", "with",
    "feat", "ft", "featuring", "de", "da", "do", "das", "dos", "e", "o", "os", "as", "um", "uma",
}


def _meaningful_tokens(value: str) -> list[str]:
    tokens = norm_text(value).split()
    meaningful = [tok for tok in tokens if tok not in TITLE_STOPWORDS]
    return meaningful or tokens


def title_matches_song(song: str, title: str) -> bool:
    """Gate eliminatório: primeiro prova que o resultado é a música pedida.

    - títulos de uma palavra exigem o token exato (Enemy != Eclipse/Enemies);
    - títulos com várias palavras exigem todos os tokens significativos, em qualquer ordem;
    - descritores extras como Official Audio/Live/Remix não impedem a correspondência.
    """
    wanted = _meaningful_tokens(song)
    got = set(norm_text(title).split())
    if not wanted or not got:
        return False
    if len(wanted) == 1:
        return wanted[0] in got
    return all(tok in got for tok in wanted)


def artist_matches_request(artist: str, title: str, uploader: str) -> bool:
    """Confirma o artista quando ele foi informado, sem exigir um uploader idêntico.

    Aceita o nome do artista no próprio título (comum em uploads de gravadoras/Topic)
    ou uma boa cobertura de tokens no uploader/título combinado.
    """
    wanted = _meaningful_tokens(artist)
    if not wanted:
        return True
    combined_tokens = set(norm_text(f"{title} {uploader}").split())
    if all(tok in combined_tokens for tok in wanted):
        return True
    uploader_n = norm_text(uploader)
    artist_n = norm_text(artist)
    if uploader_n and (artist_n in uploader_n or uploader_n in artist_n):
        return True
    coverage = sum(1 for tok in wanted if tok in combined_tokens) / max(1, len(wanted))
    return coverage >= 0.67


def duration_label(seconds: float | int | None) -> str:
    try:
        sec = int(round(float(seconds or 0)))
    except Exception:
        return "duração desconhecida"
    if sec <= 0:
        return "duração desconhecida"
    return f"{sec // 60}:{sec % 60:02d}"


class SourceSearchService:
    """Busca e ranqueia fontes preservando qualidade e evitando previews/truncamentos.

    Regras importantes:
    - lossless/download original têm prioridade estrutural;
    - formatos explicitamente marcados como preview nunca são recomendados;
    - durações muito divergentes da mediana dos candidatos equivalentes sofrem penalidade;
    - a seleção registra um format_id real quando possível, para baixar exatamente o formato inspecionado.
    """

    def __init__(self, runner: CommandRunner, logger: logging.Logger):
        self.runner = runner
        self.logger = logger

    def ytdlp_common(self) -> list[str]:
        args = ["--ignore-config"]
        deno = tool_path("deno")
        if deno:
            args += ["--js-runtimes", f"deno:{deno}"]
        return args

    def _flat_search(self, prefix: str, query: str, count: int) -> list[dict]:
        ytdlp = tool_path("yt-dlp")
        if not ytdlp:
            raise CommandError("A pesquisa online não está disponível. Verifique a página Sistema.")
        data = self.runner.capture_json([
            ytdlp, *self.ytdlp_common(), "--no-warnings", "--skip-download", "--flat-playlist",
            "--dump-single-json", f"{prefix}{count}:{query}"
        ], timeout=120)
        return [e for e in (data.get("entries") or []) if isinstance(e, dict)]

    def _entry_url(self, entry: dict, source: str) -> str:
        url = entry.get("webpage_url") or entry.get("original_url") or entry.get("url") or ""
        if source == "YouTube" and url and not str(url).startswith("http"):
            vid = entry.get("id") or url
            return f"https://www.youtube.com/watch?v={vid}"
        return str(url)

    def inspect_url(self, url: str) -> dict:
        ytdlp = tool_path("yt-dlp")
        if not ytdlp:
            raise CommandError("A pesquisa online não está disponível. Verifique a página Sistema.")
        return self.runner.capture_json([
            ytdlp, *self.ytdlp_common(), "--no-warnings", "--skip-download", "--no-playlist", "--dump-single-json", url
        ], timeout=150)

    def _bandcamp_urls(self, query: str, limit: int) -> list[str]:
        req = urllib.request.Request(
            "https://bandcamp.com/search?q=" + urllib.parse.quote_plus(query),
            headers={"User-Agent": "Mozilla/5.0 GuitarBackingWizard/5.23"},
        )
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                body = r.read().decode("utf-8", errors="replace")
        except Exception as exc:
            self.logger.debug("Bandcamp indisponível: %s", exc)
            return []
        body = html.unescape(body)
        urls = re.findall(r"https?://[^\"'<> ]+\.bandcamp\.com/track/[^\"'<>?&# ]+", body, flags=re.I)
        out, seen = [], set()
        for u in urls:
            u = u.rstrip("/.,)")
            if u not in seen:
                seen.add(u)
                out.append(u)
            if len(out) >= limit:
                break
        return out

    @staticmethod
    def _is_preview_format(fmt: dict) -> bool:
        blob = " ".join(str(fmt.get(k) or "") for k in ("format_id", "format", "format_note", "url")).lower()
        return any(token in blob for token in ("preview", "sample", "excerpt", "snippet"))

    @classmethod
    def _quality(cls, info: dict) -> tuple[str, str, int, bool]:
        formats = [
            f for f in (info.get("formats") or [])
            if isinstance(f, dict) and f.get("vcodec") in (None, "none") and str(f.get("acodec") or "none") != "none"
        ]
        non_preview = [f for f in formats if not cls._is_preview_format(f)]
        preview_only = bool(formats) and not non_preview
        usable = non_preview or formats

        lossless_exts = {"flac", "wav", "alac", "ape", "aiff", "aif"}
        lossless, originals = [], []
        for f in usable:
            ext = str(f.get("ext") or "").lower()
            acodec = str(f.get("acodec") or "").lower()
            fid = str(f.get("format_id") or "")
            note = str(f.get("format_note") or "").lower()
            if ext in lossless_exts or acodec.startswith("pcm") or acodec in {"flac", "alac"}:
                lossless.append(f)
            if fid == "download" or "original" in note:
                originals.append(f)

        if preview_only:
            f = max(usable, key=lambda x: (x.get("quality") or -99, x.get("abr") or x.get("tbr") or 0)) if usable else {}
            ext = str(f.get("ext") or "áudio").upper()
            return f"PREVIEW/TRUNCADO {ext}", str(f.get("format_id") or "bestaudio/best"), -60, True

        if lossless:
            pref = {"flac": 0, "alac": 1, "wav": 2, "aiff": 3, "aif": 3, "ape": 4}
            lossless.sort(key=lambda f: (pref.get(str(f.get("ext") or "").lower(), 9), -(f.get("filesize") or 0)))
            f = lossless[0]
            return f"LOSSLESS {str(f.get('ext') or '').upper()}", str(f.get("format_id") or "bestaudio/best"), 40, False

        if originals:
            f = originals[0]
            return f"download ORIGINAL ({str(f.get('ext') or 'arquivo').upper()})", str(f.get("format_id") or "download"), 32, False

        if usable:
            f = max(usable, key=lambda x: (x.get("quality") or -99, x.get("abr") or x.get("tbr") or 0))
            br = f.get("abr") or f.get("tbr")
            detail = f"stream {str(f.get('ext') or 'áudio').upper()}" + (f" ~{int(br)} kbps" if isinstance(br, (int, float)) and br > 0 else "")
            return detail, str(f.get("format_id") or "bestaudio/best"), 0, False

        return "áudio disponível", "bestaudio/best", 0, False

    def _candidate(self, info: dict, source: str, url: str, artist: str, song: str) -> SourceCandidate:
        title = str(info.get("track") or info.get("title") or "")
        uploader = str(info.get("artist") or info.get("uploader") or "")
        artists = info.get("artists")
        if isinstance(artists, list) and artists:
            uploader = ", ".join(str(x) for x in artists if x)

        quality, fmt, bonus, preview_only = self._quality(info)
        title_n, uploader_n, req_n, artist_n = map(norm_text, (title, uploader, song, artist))
        song_sim, artist_sim = text_similarity(song, title), text_similarity(artist, uploader)
        if req_n and req_n in title_n:
            song_sim = max(song_sim, .95)
        if artist_n and artist_n in uploader_n:
            artist_sim = max(artist_sim, .95)

        base = {"Bandcamp": 45, "SoundCloud": 48, "YouTube": 50}.get(source, 45)
        score = base + bonus + int(song_sim * 12) + int(artist_sim * 10)
        bad = ("cover", "karaoke", "reaction", "tutorial", "slowed", "sped up", "nightcore", "8d", "live")
        for token in bad:
            if token in title_n and token not in req_n:
                score -= 18 if token != "live" else 10
        if "remix" in title_n and "remix" not in req_n:
            score -= 14

        duration = float(info.get("duration") or 0.0)
        short_title_exempt = any(tok in req_n for tok in ("intro", "interlude", "outro", "short"))
        duration_warning = bool(duration and duration < 60 and not short_title_exempt)
        if duration_warning:
            score -= 30

        official = False
        reason: list[str] = []
        if bonus >= 40:
            reason.append("áudio de boa qualidade")
        elif bonus >= 30:
            reason.append("fonte direta")
        if preview_only:
            reason.append("trecho curto")
            score = min(score, 5)
        elif duration_warning:
            reason.append(f"duração curta ({duration_label(duration)})")

        if source == "YouTube":
            if "topic" in uploader_n or "official audio" in title_n or "official music" in title_n:
                score += 10
                official = True
                reason.append("canal oficial")
            elif artist_sim >= .82:
                score += 6
                reason.append("canal compatível")
        elif artist_sim >= .80:
            score += 6
            official = True
            reason.append("perfil compatível")
        if song_sim >= .82:
            reason.append("título compatível")
        if duration > 0 and not preview_only:
            reason.append(f"duração {duration_label(duration)}")

        return SourceCandidate(
            score=max(0, min(100, score)), source=source, quality=quality, title=title, uploader=uploader,
            url=url, format_id=fmt, official=official,
            reason=", ".join(reason) or "melhor resultado encontrado",
            quality_bonus=bonus, duration=duration, preview_only=preview_only, duration_warning=duration_warning,
        )

    @staticmethod
    def _candidate_matches_request(candidate: SourceCandidate, artist: str, song: str) -> bool:
        if not title_matches_song(song, candidate.title):
            return False
        if artist.strip() and not artist_matches_request(artist, candidate.title, candidate.uploader):
            return False
        return True

    def _apply_duration_consensus(self, candidates: list[SourceCandidate], song: str) -> None:
        """Penaliza versões truncadas usando a mediana das correspondências plausíveis.

        Isso resolve casos em que um serviço retorna um preview de ~30 s ao lado de uploads completos
        de 3-5 min. A regra só entra quando há pelo menos duas referências de duração plausíveis.
        """
        plausible = [
            c for c in candidates
            if not c.preview_only and c.duration >= 60 and text_similarity(song, c.title) >= .72
        ]
        if len(plausible) < 2:
            return
        med = statistics.median(c.duration for c in plausible)
        if med <= 0:
            return
        for c in candidates:
            if c.preview_only or c.duration <= 0:
                continue
            ratio = c.duration / med
            if ratio < .68:
                c.score = max(0, c.score - 35)
                c.duration_warning = True
                c.reason += f", duração muito menor que versões equivalentes (mediana {duration_label(med)})"
            elif ratio > 1.55:
                c.score = max(0, c.score - 15)
                c.duration_warning = True
                c.reason += f", duração muito maior que versões equivalentes (mediana {duration_label(med)})"
            elif .92 <= ratio <= 1.08:
                c.score = min(100, c.score + 3)

    def search(self, artist: str, song: str, depth: str = "Robusta") -> list[SourceCandidate]:
        if not song.strip():
            raise ValueError("Informe o nome da música.")
        query = " ".join(x for x in (artist.strip(), song.strip()) if x)
        max_bc, max_search, max_inspect = (6, 9, 8) if depth == "Máxima" else (4, 6, 5)
        candidates: list[SourceCandidate] = []

        for url in self._bandcamp_urls(query, max_bc):
            try:
                candidate = self._candidate(self.inspect_url(url), "Bandcamp", url, artist, song)
                if self._candidate_matches_request(candidate, artist, song):
                    candidates.append(candidate)
                else:
                    self.logger.debug("Bandcamp descartado por não corresponder à música/artista: %s — %s", candidate.uploader, candidate.title)
            except Exception as exc:
                self.logger.debug("Bandcamp candidato ignorado: %s", exc)

        searches = [
            ("SoundCloud", "scsearch", query),
            ("YouTube", "ytsearch", f"{query} official audio"),
            ("YouTube", "ytsearch", f"{query} Topic"),
        ]
        seen: set[str] = {c.url for c in candidates}
        for source, prefix, q in searches:
            try:
                entries = self._flat_search(prefix, q, max_search)
            except Exception as exc:
                self.logger.debug("Busca %s indisponível: %s", source, exc)
                continue
            inspected = 0
            for entry in entries:
                if inspected >= max_inspect:
                    break
                flat_title = str(entry.get("title") or entry.get("track") or "")
                # Evita gastar inspeções com resultados obviamente de outra música.
                if flat_title and not title_matches_song(song, flat_title):
                    self.logger.debug("%s descartado no gate de título: %s", source, flat_title)
                    continue
                url = self._entry_url(entry, source)
                if not url or url in seen:
                    continue
                seen.add(url)
                try:
                    candidate = self._candidate(self.inspect_url(url), source, url, artist, song)
                    if not self._candidate_matches_request(candidate, artist, song):
                        self.logger.debug("%s descartado por não corresponder à música/artista: %s — %s", source, candidate.uploader, candidate.title)
                        continue
                    candidates.append(candidate)
                    inspected += 1
                except Exception as exc:
                    self.logger.debug("%s candidato ignorado: %s", source, exc)

        self._apply_duration_consensus(candidates, song)

        # Previews ficam visíveis somente no fim para diagnóstico, nunca como recomendação automática.
        candidates.sort(key=lambda c: (not c.preview_only, c.score, c.quality_bonus, c.official), reverse=True)
        safe = [c for c in candidates if not c.preview_only]
        rejected = [c for c in candidates if c.preview_only]
        return (safe + rejected)[:15]
