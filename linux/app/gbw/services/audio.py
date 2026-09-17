from __future__ import annotations

import json
import logging
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import tempfile
from pathlib import Path
from typing import Callable

from gbw.config import APP_HOME, VENV_DIR
from gbw.core.runner import CommandRunner, CommandError
from gbw.core.system import tool_path, rubberband_r3_status
from gbw.models import ProjectDocument, SourceCandidate, STEMS

AUDIO_EXTS = {".wav",".flac",".m4a",".mp3",".ogg",".opus",".webm",".aac",".aiff",".aif",".wma",".mka",".mp4",".mov"}

class AudioService:
    def __init__(self, runner: CommandRunner, logger: logging.Logger):
        self.runner = runner
        self.logger = logger

    def ytdlp_common(self) -> list[str]:
        args=["--ignore-config"]
        deno=tool_path("deno")
        if deno: args += ["--js-runtimes", f"deno:{deno}"]
        return args

    @staticmethod
    def cli_supports_flag(cli: str, flag: str) -> bool:
        """Consulta o CLI realmente instalado, evitando assumir flags de versões mais novas."""
        try:
            cp=subprocess.run([cli,"--help"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=15)
            return cp.returncode==0 and flag in (cp.stdout or "")
        except Exception:
            return False

    @staticmethod
    def torch_device_capabilities(cli: str | None = None) -> dict[str, bool]:
        """Consulta o PyTorch do MESMO ambiente do separador.

        Algumas versões do bs-roformer-infer expõem --device, mas passam o texto
        diretamente para torch.device(); por isso ``auto`` não é aceito pelo CLI.
        O wizard resolve o dispositivo antes de invocar o separador.
        """
        python_candidates=[]
        if cli:
            python_candidates.append(Path(cli).parent / "python")
        python_candidates += [VENV_DIR / "bin" / "python", Path(sys.executable)]
        python_exec=next((p for p in python_candidates if p.exists()),None)
        if not python_exec:
            return {"cuda":False,"mps":False,"xpu":False}
        code=(
            "import json, torch; "
            "cuda=bool(torch.cuda.is_available()); "
            "mps=bool(getattr(getattr(torch,'backends',None),'mps',None) and torch.backends.mps.is_available()); "
            "xpu=bool(hasattr(torch,'xpu') and torch.xpu.is_available()); "
            "print(json.dumps({'cuda':cuda,'mps':mps,'xpu':xpu}))"
        )
        try:
            cp=subprocess.run([str(python_exec),"-c",code],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=20)
            if cp.returncode==0:
                data=json.loads((cp.stdout or "{}").strip().splitlines()[-1])
                return {k:bool(data.get(k,False)) for k in ("cuda","mps","xpu")}
        except Exception:
            pass
        return {"cuda":False,"mps":False,"xpu":False}

    def resolve_torch_device(self, requested: str, cli: str | None = None) -> str:
        """Converte a opção amigável ``auto`` em um device válido do torch."""
        requested=(requested or "auto").strip().lower()
        if requested=="cpu":
            return "cpu"
        if requested not in ("auto","cuda","xpu","mps"):
            raise CommandError("Opção de processamento inválida.")
        caps=self.torch_device_capabilities(cli)
        if requested=="auto":
            for device in ("cuda","xpu","mps"):
                if caps.get(device):
                    self.logger.info("Processamento: %s.", "GPU" if device != "cpu" else "CPU")
                    return device
            self.logger.info("Processamento: CPU.")
            return "cpu"
        if requested in ("cuda","xpu","mps") and not caps.get(requested,False):
            raise CommandError(
                "A GPU selecionada não está disponível. Escolha Automático ou CPU."
            )
        return requested

    @staticmethod
    def probe_duration(path: Path) -> float:
        ffprobe=tool_path("ffprobe")
        if not ffprobe:
            return 0.0
        try:
            cp=subprocess.run([ffprobe,"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",str(path)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=20)
            return float((cp.stdout or "0").strip() or 0) if cp.returncode==0 else 0.0
        except Exception:
            return 0.0

    @staticmethod
    def _format_is_preview(fmt: dict) -> bool:
        blob=" ".join(str(fmt.get(k) or "") for k in ("format_id","format","format_note","url")).lower()
        return any(tok in blob for tok in ("preview","sample","excerpt","snippet"))

    def inspect_source_before_download(self, url: str) -> dict:
        """Inspeciona a URL imediatamente antes do download para impedir previews antigos/stale."""
        ytdlp=tool_path("yt-dlp")
        if not ytdlp:
            raise CommandError("O download online não está disponível. Verifique a página Sistema.")
        info=self.runner.capture_json([ytdlp,*self.ytdlp_common(),"--no-warnings","--skip-download","--no-playlist","--dump-single-json",url],timeout=150)
        formats=[f for f in (info.get("formats") or []) if isinstance(f,dict) and f.get("vcodec") in (None,"none") and str(f.get("acodec") or "none")!="none"]
        if formats and all(self._format_is_preview(f) for f in formats):
            raise CommandError(
                "A fonte selecionada oferece apenas um trecho curto. Escolha outra fonte."
            )
        return info

    def validate_existing_source(self, doc: ProjectDocument) -> None:
        """Bloqueia previews já baixados por versões antigas antes de iniciar separação pesada."""
        project=Path(doc.state.project_dir)
        for info_path in (project/"source").glob("*.info.json"):
            try:
                info=json.loads(info_path.read_text(encoding="utf-8",errors="replace"))
            except Exception:
                continue
            blobs=[str(info.get("format_id") or ""),str(info.get("format") or ""),str(info.get("format_note") or "")]
            for req in (info.get("requested_downloads") or []):
                if isinstance(req,dict):
                    blobs.extend(str(req.get(k) or "") for k in ("format_id","format","format_note","url"))
            blob=" ".join(blobs).lower()
            if any(tok in blob for tok in ("preview","sample","excerpt","snippet")):
                raise CommandError(
                    "A fonte deste projeto é apenas um trecho curto. Volte à etapa Fonte e escolha uma versão completa."
                )

    def prepare_source(self, doc: ProjectDocument, progress: Callable[[float|None,str],None] | None = None) -> ProjectDocument:
        cfg, st = doc.config, doc.state
        d = Path(st.project_dir); srcdir=d/"source"
        srcdir.mkdir(parents=True, exist_ok=True)
        if progress: progress(None, "Preservando fonte nativa…")
        if cfg.source_mode == "local":
            src = Path(cfg.local_file).expanduser().resolve()
            if not src.is_file(): raise CommandError("Arquivo local não encontrado.")
            dst = srcdir / ("original" + src.suffix.lower())
            shutil.copy2(src, dst)
            source_native = dst
        else:
            url = cfg.source_url
            fmt = "bestaudio/best"
            expected_duration=0.0
            if cfg.source_mode == "auto" and cfg.selected_candidate:
                if bool(cfg.selected_candidate.get("preview_only")):
                    raise CommandError("A fonte selecionada é apenas um trecho curto. Escolha outra fonte.")
                url = str(cfg.selected_candidate.get("url") or "")
                fmt = str(cfg.selected_candidate.get("format_id") or "bestaudio/best")
                try: expected_duration=float(cfg.selected_candidate.get("duration") or 0.0)
                except Exception: expected_duration=0.0
            if not url: raise CommandError("Nenhuma URL de fonte selecionada.")
            # Revalida a URL no momento do download. Isso também protege projetos migrados da v3/v4,
            # cujos manifests ainda não registravam preview_only/duration.
            try:
                current_info=self.inspect_source_before_download(url)
                if expected_duration<=0:
                    expected_duration=float(current_info.get("duration") or 0.0)
            except CommandError:
                raise
            except Exception as exc:
                self.logger.debug("Não foi possível revalidar a fonte antes do download: %s",exc)
            ytdlp=tool_path("yt-dlp")
            if not ytdlp: raise CommandError("O download online não está disponível. Verifique a página Sistema.")
            template=str(srcdir/"original.%(ext)s")
            args=[ytdlp,*self.ytdlp_common(),"-f",fmt,"--no-playlist","--write-info-json","--newline","-o",template,url]
            pct_re=re.compile(r"\[download\]\s+([0-9.]+)%")
            def on_line(line:str):
                m=pct_re.search(line)
                if m and progress: progress(float(m.group(1))/100.0, f"Download {m.group(1)}%")
            self.runner.run(args,"Baixando melhor fonte selecionada",on_line=on_line)
            files=[p for p in srcdir.iterdir() if p.is_file() and p.suffix.lower() in AUDIO_EXTS and not p.name.endswith(".part")]
            if not files: raise CommandError("Download terminou, mas o áudio não foi localizado.")
            source_native=max(files,key=lambda p:p.stat().st_size)
            actual_duration=self.probe_duration(source_native)
            if expected_duration>0 and actual_duration>0 and actual_duration < max(20.0, expected_duration*0.72):
                raise CommandError(
                    "O download parece incompleto. Escolha outra fonte e tente novamente."
                )
            if actual_duration and actual_duration < 45:
                self.logger.warning("A fonte baixada é muito curta. Confirme se é a música completa.")
        st.source_native=str(source_native)

        if progress: progress(None,"Preparando áudio…")
        ffmpeg=tool_path("ffmpeg")
        if not ffmpeg: raise CommandError("O componente de áudio não está disponível. Verifique a página Sistema.")
        prepared=d/"prepared"/"original_44100_f32.wav"
        self.runner.run([ffmpeg,"-y","-hide_banner","-i",str(source_native),"-map","0:a:0","-vn","-ar","44100","-ac","2","-c:a","pcm_f32le",str(prepared)],"Preparando WAV float32")
        st.prepared_wav=str(prepared); st.stage="source_prepared"
        return doc

    @staticmethod
    def _human_time(seconds: float | None) -> str:
        if seconds is None or seconds < 0 or not (seconds < 10**9):
            return "calculando…"
        seconds = int(round(seconds))
        h, rem = divmod(seconds, 3600)
        m, s = divmod(rem, 60)
        if h:
            return f"{h:d}h {m:02d}min"
        if m:
            return f"{m:d}min {s:02d}s"
        return f"{s:d}s"

    @property
    def _eta_stats_path(self) -> Path:
        return APP_HOME / "separation_eta.json"

    def _load_eta_stats(self) -> dict:
        try:
            return json.loads(self._eta_stats_path.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def _eta_ratio(self, mode: str, device: str) -> tuple[float, bool]:
        """Segundos de processamento por segundo de áudio.

        Prioridade: histórico desta máquina > fallback conservador. O fallback é
        explicitamente apresentado como estimativa inicial e é substituído pelo
        histórico após a primeira execução bem-sucedida.
        """
        key = f"{mode}:{device}"
        data = self._load_eta_stats().get(key, {})
        try:
            ratio = float(data.get("seconds_per_audio_second"))
            if 0.01 < ratio < 500:
                return ratio, True
        except Exception:
            pass
        defaults = {
            ("A", "cpu"): 9.0,
            ("A", "cuda"): 0.8,
            ("A", "xpu"): 1.2,
            ("A", "mps"): 1.2,
            ("B", "cpu"): 3.0,
            ("B", "cuda"): 0.35,
            ("B", "xpu"): 0.55,
            ("B", "mps"): 0.55,
        }
        return defaults.get((mode, device), 5.0), False

    def _save_eta_ratio(self, mode: str, device: str, audio_duration: float, elapsed: float) -> None:
        if audio_duration <= 5 or elapsed <= 1:
            return
        observed = elapsed / audio_duration
        if not (0.01 < observed < 500):
            return
        path = self._eta_stats_path
        path.parent.mkdir(parents=True, exist_ok=True)
        data = self._load_eta_stats()
        key = f"{mode}:{device}"
        old = data.get(key, {})
        try:
            old_ratio = float(old.get("seconds_per_audio_second"))
            ratio = old_ratio * 0.65 + observed * 0.35 if 0.01 < old_ratio < 500 else observed
            runs = int(old.get("runs", 0)) + 1
        except Exception:
            ratio, runs = observed, 1
        data[key] = {
            "seconds_per_audio_second": ratio,
            "runs": runs,
            "last_elapsed_seconds": elapsed,
            "last_audio_seconds": audio_duration,
        }
        try:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception as exc:
            self.logger.debug("Não foi possível salvar histórico de ETA: %s", exc)

    @staticmethod
    def _parse_separator_fraction(line: str) -> float | None:
        """Extrai progresso genérico apenas para separadores que o expõem.

        BS-RoFormer A não usa esta fração: versões 0.1.x imprimem ETA por
        chunk, não um percentual global confiável. Isso evita confundir um
        100% interno com o fim da música inteira.
        """
        low = line.lower()
        if any(x in low for x in ("download", "checkpoint", "sha256", "copied packaged")):
            return None
        if not any(x in low for x in ("chunk", "separ", "process", "infer", "audio", "song", "it/s")):
            return None
        m = re.search(r"(?<![\d.])(\d{1,3}(?:\.\d+)?)%", line)
        if m:
            pct = float(m.group(1)) / 100.0
            if 0.0 < pct <= 1.0:
                return pct
        m = re.search(r"(?<!\d)(\d+)\s*/\s*(\d+)(?!\d)", line)
        if m:
            done, total = int(m.group(1)), int(m.group(2))
            if total > 0 and 0 < done <= total:
                return done / total
        return None

    @staticmethod
    def _parse_roformer_native_eta(line: str) -> tuple[str, float] | None:
        """Lê a telemetria nativa do bs-roformer-infer 0.1.x.

        A versão 0.1.5 imprime o tempo total estimado após medir o primeiro
        chunk e, depois, ``Estimated time remaining: X seconds`` a cada chunk.
        Essa informação é muito mais confiável que inferir 100% de outras
        barras/percentuais internos.
        """
        m = re.search(r"Estimated total processing time for this track:\s*([0-9]+(?:\.[0-9]+)?)\s*seconds", line, re.I)
        if m:
            return "total", max(0.0, float(m.group(1)))
        m = re.search(r"Estimated time remaining:\s*([0-9]+(?:\.[0-9]+)?)\s*seconds", line, re.I)
        if m:
            return "remaining", max(0.0, float(m.group(1)))
        return None

    def _roformer_eta_display(self, remaining: float | None, seen_at: float | None, now: float) -> tuple[float | None, str, bool]:
        """Formata ETA do RoFormer sem fingir precisão global.

        ``remaining`` é uma estimativa emitida pelo próprio separador ao concluir
        um chunk. Entre duas atualizações ela envelhece; por isso mostramos a
        idade da estimativa e deixamos de exibir um relógio preciso quando ela
        fica velha demais. O terceiro retorno indica que o modelo informou 0 s
        explicitamente, situação tratada como fase de finalização enquanto o
        subprocesso ainda estiver vivo.
        """
        if remaining is None or seen_at is None:
            return None, "aguardando estimativa", False
        rem = max(0.0, float(remaining))
        age = max(0.0, float(now) - float(seen_at))
        if rem <= 0.5:
            return None, "finalizando", True

        # A estimativa foi feita no instante seen_at. Entre chunks, descontamos
        # somente o tempo de parede decorrido; isso evita um ETA visualmente
        # congelado sem transformá-lo em percentual de progresso global.
        projected = rem - age
        if age <= 90.0 and projected > 0.5:
            return projected, f"atualizada há {self._human_time(age)}", False
        if age <= 300.0:
            if projected > 0.5:
                return projected, f"última atualização há {self._human_time(age)}", False
            return None, f"aguardando atualização (última há {self._human_time(age)})", False
        return None, f"aguardando atualização (última há {self._human_time(age)})", False

    @staticmethod
    def _linux_process_cpu_seconds(pid: int | None) -> float | None:
        """Tempo de CPU do processo Linux, sem adicionar dependência psutil."""
        if not pid or os.name != "posix":
            return None
        try:
            fields = Path(f"/proc/{pid}/stat").read_text(encoding="utf-8", errors="replace").split()
            ticks = float(fields[13]) + float(fields[14])
            hz = float(os.sysconf("SC_CLK_TCK"))
            return ticks / hz if hz > 0 else None
        except Exception:
            return None

    def _run_separator_with_eta(self, args: list[str], label: str, mode: str, device: str,
                                audio_duration: float,
                                progress: Callable[[float | None, str], None] | None = None,
                                overall_base: float = 0.0, overall_span: float = 1.0) -> None:
        start = time.monotonic()
        ratio, calibrated = self._eta_ratio(mode, device)
        fallback_total = max(30.0, audio_duration * ratio) if audio_duration > 0 else None
        now = time.monotonic()
        state = {
            "fraction": None,
            "observed_total": None,
            "native_total": None,
            "native_remaining": None,
            "native_seen_at": None,
            "phase": "processando",
            "last_output": now,
            "last_cpu_seconds": None,
            "last_cpu_active": now,
            "logged_estimate_exceeded": False,
            "logged_activity_silence": False,
            "logged_possible_stall": False,
        }
        lock = threading.Lock()
        stop = threading.Event()

        def on_line(line: str):
            stamp = time.monotonic()
            low = line.lower()
            with lock:
                if line.strip():
                    state["last_output"] = stamp
                if "download" in low and ("checkpoint" in low or "model" in low):
                    state["phase"] = "baixando modelo"

            if mode == "A":
                native = self._parse_roformer_native_eta(line)
                if native:
                    kind, seconds = native
                    with lock:
                        state["native_seen_at"] = stamp
                        state["phase"] = "separando stems"
                        if kind == "total":
                            state["native_total"] = max(seconds, 0.1)
                            # A primeira estimativa total já inclui todos os chunks.
                            state["native_remaining"] = seconds
                        else:
                            state["native_remaining"] = seconds
                    return
                # Não use percentuais genéricos para A: eles podem representar
                # subtarefas internas e chegar a 100% antes do processo terminar.
                return

            frac = self._parse_separator_fraction(line)
            if frac is not None:
                elapsed = max(0.1, stamp - start)
                # 100% do CLI ainda pode ser seguido de escrita/finalização; nunca
                # sinalizamos conclusão antes do subprocesso realmente encerrar.
                safe_frac = min(float(frac), 0.97)
                observed_total = elapsed / max(safe_frac, 0.01)
                with lock:
                    state["fraction"] = safe_frac
                    prev = state.get("observed_total")
                    state["observed_total"] = observed_total if not prev else float(prev) * 0.72 + observed_total * 0.28
                    state["phase"] = "separando stems" if frac < 1.0 else "finalizando stems"

        def ticker():
            while not stop.wait(2.0):
                if not progress:
                    continue
                stamp = time.monotonic()
                elapsed = stamp - start
                proc = self.runner.process
                pid = proc.pid if proc and proc.poll() is None else None
                cpu_seconds = self._linux_process_cpu_seconds(pid)
                with lock:
                    prev_cpu = state.get("last_cpu_seconds")
                    if cpu_seconds is not None:
                        if prev_cpu is not None and cpu_seconds > float(prev_cpu) + 0.05:
                            state["last_cpu_active"] = stamp
                        state["last_cpu_seconds"] = cpu_seconds
                    frac = state.get("fraction")
                    observed_total = state.get("observed_total")
                    native_total = state.get("native_total")
                    native_remaining = state.get("native_remaining")
                    phase = str(state.get("phase") or "processando")
                    last_output = float(state.get("last_output") or start)
                    last_cpu_active = float(state.get("last_cpu_active") or start)

                silence = max(0.0, stamp - last_output)
                cpu_quiet = max(0.0, stamp - last_cpu_active)
                eta = None
                origin = ""
                pct_text = ""

                if mode == "A" and native_remaining is not None:
                    # IMPORTANTE: o RoFormer 0.1.x fornece ETA por chunk, mas não
                    # um percentual global confiável. Portanto nunca derivamos
                    # "43%" de native_remaining/native_total. A barra segue
                    # indeterminada e o texto informa apenas ETA + validade.
                    eta, origin, explicit_zero = self._roformer_eta_display(
                        float(native_remaining),
                        float(state.get("native_seen_at")) if state.get("native_seen_at") is not None else None,
                        stamp,
                    )
                    if explicit_zero:
                        phase = "finalizando stems"
                elif phase == "baixando modelo" and not observed_total:
                    eta = fallback_total
                    origin = "após o download"
                else:
                    total = float(observed_total) if observed_total else fallback_total
                    if total and elapsed < total:
                        eta = max(1.0, total - elapsed)
                        origin = "estimado nesta máquina" if calibrated and not observed_total else (
                            "em atualização" if observed_total else "estimativa inicial"
                        )
                    elif total:
                        # Estimativa vencida não significa conclusão. Nunca mostre ETA=0
                        # enquanto o subprocesso segue vivo.
                        origin = "recalculando"
                        with lock:
                            if not state.get("logged_estimate_exceeded"):
                                state["logged_estimate_exceeded"] = True
                                self.logger.debug(
                                    "ETA inicial excedido após %.1fs; separador continua ativo%s.",
                                    elapsed, f" (PID {pid})" if pid else ""
                                )

                health = ""
                if pid:
                    if device == "cpu" and silence >= 180:
                        if cpu_quiet < 8:
                            health = f" • processo ativo"
                            with lock:
                                if not state.get("logged_activity_silence"):
                                    state["logged_activity_silence"] = True
                                    self.logger.debug(
                                        "Separador sem nova saída textual há %.1fs, mas CPU do processo continua avançando (PID %s).",
                                        silence, pid
                                    )
                        elif silence >= 300 and cpu_quiet >= 120:
                            health = f" • verificando atividade"
                            with lock:
                                if not state.get("logged_possible_stall"):
                                    state["logged_possible_stall"] = True
                                    self.logger.warning(
                                        "A separação está há alguns minutos sem atividade aparente. Aguarde ou cancele para tentar novamente."
                                    )
                        else:
                            health = f" • aguardando atualização"
                    elif device != "cpu" and silence >= 300:
                        health = f" • processo ativo"

                eta_text = f"Tempo restante ~{self._human_time(eta)} • {origin}" if eta is not None else (
                    f"Tempo restante: recalculando • {origin}" if origin else "Tempo restante: calculando"
                )
                msg = (
                    f"{label} • {phase}{pct_text} • decorrido {self._human_time(elapsed)} • "
                    f"{eta_text}{health}"
                )

                # Para A mantemos a barra animada até o processo REALMENTE terminar.
                # Percentuais por chunk aparecem no texto, mas não congelam a barra.
                mapped = None if mode == "A" else (
                    overall_base + overall_span * float(frac) if frac is not None else None
                )
                progress(mapped, msg)

        thread = threading.Thread(target=ticker, daemon=True, name=f"GBW-ETA-{mode}")
        thread.start()
        try:
            if progress:
                initial_eta = fallback_total
                origin = "estimado pelo histórico" if calibrated else "estimativa inicial"
                progress(None, f"{label} • iniciando • tempo restante ~{self._human_time(initial_eta)} • {origin}")
            self.runner.run(args, label, on_line=on_line)
        finally:
            stop.set()
            thread.join(timeout=0.4)
        elapsed = time.monotonic() - start
        self._save_eta_ratio(mode, device, audio_duration, elapsed)
        if progress:
            progress(overall_base + overall_span, f"{label} • concluído em {self._human_time(elapsed)}")

    def _clear_separator_output(self, doc: ProjectDocument, mode: str) -> None:
        """Remove somente a saída do separador indicado, preservando fonte/prepared e outros modos."""
        d = Path(doc.state.project_dir)
        out = d / "separation" / ("A_bs_roformer" if mode == "A" else "B_demucs")
        shutil.rmtree(out, ignore_errors=True)
        doc.state.separator_dirs.pop(mode, None)
        doc.state.stem_maps.pop(mode, None)
        preview = doc.state.previews.pop(mode, None)
        if preview:
            try:
                Path(preview).unlink(missing_ok=True)
            except Exception:
                pass

    def separate(self, doc: ProjectDocument, progress: Callable[[float|None,str],None] | None = None) -> ProjectDocument:
        cfg, st = doc.config, doc.state
        prepared = Path(st.prepared_wav)
        if not prepared.exists():
            raise CommandError("Fonte preparada não existe.")
        self.validate_existing_source(doc)
        audio_duration = self.probe_duration(prepared)
        modes = ["A", "B"] if cfg.separator_mode == "AB" else [cfg.separator_mode]
        span = 1.0 / max(1, len(modes))
        for idx, mode in enumerate(modes):
            base = idx * span
            # Toda nova execução começa limpa para que um cancelamento anterior nunca
            # deixe stems parciais que possam ser confundidos com um resultado válido.
            self._clear_separator_output(doc, mode)
            try:
                if mode == "A":
                    self._separate_a(doc, progress, audio_duration, base, span)
                else:
                    self._separate_b(doc, progress, audio_duration, base, span)
            except Exception:
                if self.runner.cancel_event.is_set():
                    self.logger.warning("Separação cancelada; fonte preservada.")
                    self._clear_separator_output(doc, mode)
                    # Se nada foi concluído nesta execução, volta ao estado seguro anterior.
                    if not st.stem_maps:
                        st.stage = "source_prepared"
                raise
            smap = self.find_stems(Path(st.separator_dirs[mode]))
            missing = [s for s in STEMS if s not in smap]
            if missing:
                raise CommandError("A separação não gerou todos os arquivos esperados. Tente novamente.")
            st.stem_maps[mode] = {k: str(v) for k, v in smap.items()}
            preview = Path(st.project_dir) / "previews" / f"{mode}_backing_original_sem_guitarra.wav"
            self.mix_stems([smap[s] for s in STEMS if s != "guitar"], preview, "WAV 32-bit float", -1.0)
            st.previews[mode] = str(preview)
        st.stage = "separated"
        return doc

    def _separate_a(self, doc: ProjectDocument, progress=None, audio_duration: float = 0.0,
                    overall_base: float = 0.0, overall_span: float = 1.0):
        cli = tool_path("bs-roformer-infer")
        if not cli:
            raise CommandError("O Separador A não está disponível. Verifique a página Sistema.")
        d = Path(doc.state.project_dir)
        prepared = Path(doc.state.prepared_wav)
        inp = d / "prepared" / "bs_input"
        inp.mkdir(exist_ok=True)
        target = inp / "song.wav"
        if not target.exists():
            try:
                os.link(prepared, target)
            except OSError:
                shutil.copy2(prepared, target)
        out = d / "separation" / "A_bs_roformer"
        resolved_device = self.resolve_torch_device(doc.config.device, cli)
        args = [cli, "--input_folder", str(inp), "--store_dir", str(out), "--device", resolved_device]
        if self.cli_supports_flag(cli, "--output_format"):
            args += ["--output_format", "wav_float32"]
            self.logger.debug("Separador A: saída float32 disponível.")
        else:
            self.logger.debug("Separador A: usando formato de saída padrão compatível.")
        self._run_separator_with_eta(
            args, "Separador A", "A", resolved_device, audio_duration,
            progress, overall_base, overall_span,
        )
        doc.state.separator_dirs["A"] = str(out)

    def _separate_b(self, doc: ProjectDocument, progress=None, audio_duration: float = 0.0,
                    overall_base: float = 0.0, overall_span: float = 1.0):
        demucs = tool_path("demucs")
        if not demucs:
            raise CommandError("O Separador B não está disponível. Verifique a página Sistema.")
        d = Path(doc.state.project_dir)
        out = d / "separation" / "B_demucs"
        args = [demucs, "-n", "htdemucs_6s", "--float32", "--clip-mode", "none",
                "--shifts", str(doc.config.demucs_shifts), "--overlap", str(doc.config.demucs_overlap), "-o", str(out)]
        resolved_device = self.resolve_torch_device(doc.config.device, demucs)
        if resolved_device in ("cpu", "cuda"):
            args += ["-d", resolved_device]
        elif doc.config.device != "auto":
            self.logger.debug("Separador B: seleção de dispositivo gerenciada pelo próprio motor (%s).", resolved_device)
        args.append(doc.state.prepared_wav)
        self._run_separator_with_eta(
            args, "Separador B", "B", resolved_device, audio_duration,
            progress, overall_base, overall_span,
        )
        doc.state.separator_dirs["B"] = str(out)

    @staticmethod
    def find_stems(root: Path) -> dict[str,Path]:
        wavs=list(root.rglob("*.wav")); out={}
        for stem in STEMS:
            exact=[p for p in wavs if p.stem.lower()==stem]
            hits=exact or [p for p in wavs if re.search(rf"(^|[_\-. ]){re.escape(stem)}([_\-. ]|$)",p.stem,re.I)]
            if hits: out[stem]=sorted(hits,key=lambda p:(len(p.name),str(p)))[0]
        return out

    def pitch_stem(self, src:Path,dst:Path,semitones:int,formants:bool):
        r3=tool_path("rubberband-r3"); rb=tool_path("rubberband")
        if r3: args=[r3]
        elif rb and rubberband_r3_status()[0]: args=[rb,"-3"]
        else: raise CommandError("O componente de pitch não está disponível. Verifique a página Sistema.")
        args += ["--ignore-clipping"]
        if formants: args += ["-F"]
        args += ["-p",str(semitones),str(src),str(dst)]
        self.runner.run(args,f"Pitch {src.stem}: {semitones:+d} st")

    def pitch_file(self, src: Path, dst: Path, semitones: int, formants: bool,
                   output_format: str, sample_rate: int, channels: int,
                   source_duration: float = 0.0,
                   progress: Callable[[float | None, str], None] | None = None) -> Path:
        """Pitch de arquivo avulso usando o mesmo motor R3 do workflow.

        O input é decodificado uma única vez para float32, sem alterar sample rate
        ou canais; o Rubber Band processa em alta precisão e o resultado é
        codificado uma única vez no formato lossless escolhido.
        """
        src = Path(src).expanduser().resolve()
        dst = Path(dst).expanduser().resolve()
        if not src.is_file():
            raise CommandError("Arquivo de entrada não encontrado.")
        if not -12 <= int(semitones) <= 12:
            raise CommandError("Informe um pitch entre -12 e +12 semitons.")
        ffmpeg = tool_path("ffmpeg")
        ffprobe = tool_path("ffprobe")
        if not ffmpeg or not ffprobe:
            raise CommandError("O componente de áudio não está disponível. Verifique a página Sistema.")
        if int(sample_rate) <= 0 or int(channels) <= 0:
            raise CommandError("Parâmetros de áudio inválidos.")
        dst.parent.mkdir(parents=True, exist_ok=True)

        with tempfile.TemporaryDirectory(prefix="gbw_file_pitch_") as td:
            temp = Path(td)
            prepared = temp / "input_float.wav"
            pitched = temp / "pitched_float.wav"
            rendered = temp / ("final.flac" if output_format == "FLAC 24-bit" else "final.wav")

            if progress:
                progress(None, "Preparando áudio em alta precisão…")
            # Não força -ac: preserva exatamente a quantidade/layout de canais.
            self.runner.run([
                ffmpeg, "-y", "-hide_banner", "-v", "error", "-i", str(src),
                "-map", "0:a:0", "-vn", "-ar", str(int(sample_rate)),
                "-c:a", "pcm_f32le", str(prepared),
            ], "Preparação do arquivo para pitch")

            if int(semitones) == 0:
                shutil.copy2(prepared, pitched)
            else:
                if progress:
                    progress(None, f"Aplicando pitch {int(semitones):+d} semitons…")
                self.pitch_stem(prepared, pitched, int(semitones), bool(formants))

            if progress:
                progress(None, "Gerando arquivo final lossless…")
            enc = [
                ffmpeg, "-y", "-hide_banner", "-v", "error", "-i", str(pitched),
                "-map", "0:a:0", "-vn", "-ar", str(int(sample_rate)),
            ]
            if output_format == "FLAC 24-bit":
                enc += ["-c:a", "flac", "-compression_level", "8", "-sample_fmt", "s32",
                        "-bits_per_raw_sample", "24", str(rendered)]
            elif output_format == "WAV 24-bit":
                enc += ["-c:a", "pcm_s24le", str(rendered)]
            else:
                enc += ["-c:a", "pcm_f32le", str(rendered)]
            self.runner.run(enc, "Render do pitch de arquivo")

            # Validação de sincronismo: pitch deve manter a duração.
            out_duration = self.probe_duration(rendered)
            if source_duration > 0 and out_duration > 0:
                tolerance = min(0.12, max(0.04, source_duration * 0.0005))
                if abs(out_duration - source_duration) > tolerance:
                    raise CommandError(
                        "A duração do arquivo processado divergiu do original; o resultado foi descartado para preservar sincronismo."
                    )

            # Substituição só após sucesso: nunca deixa export parcial com nome final.
            tmp_final = dst.with_name(dst.name + ".gbw-partial")
            shutil.copy2(rendered, tmp_final)
            tmp_final.replace(dst)

        if progress:
            progress(1.0, "Pitch concluído.")
        return dst

    def export(self, doc: ProjectDocument, master_peak_dbfs: float = -1.0,
               progress: Callable[[float | None, str], None] | None = None) -> list[Path]:
        """Exporta pares REAPER-ready: backing + guitar, no original e/ou pitch ajustado.

        O mesmo ganho de segurança é aplicado aos DOIS arquivos de cada par. Isso
        preserva a relação de volume entre guitarra e banda quando ambos são
        importados em 0 dB no REAPER.
        """
        cfg, st = doc.config, doc.state
        mode = cfg.final_separator
        smap = {k: Path(v) for k, v in st.stem_maps.get(mode, {}).items()}
        if any(s not in smap for s in STEMS):
            raise CommandError("A separação não está completa.")

        pitch = cfg.semitones
        d = Path(st.project_dir)
        pitched_dir = d / "pitched" / mode
        pitched_dir.mkdir(parents=True, exist_ok=True)
        exports = d / "exports"
        exports.mkdir(exist_ok=True)
        created: list[Path] = []

        if cfg.export_source_original and st.source_native:
            src = Path(st.source_native)
            dst = exports / f"00_fonte_original{src.suffix.lower()}"
            shutil.copy2(src, dst)
            created.append(dst)

        if cfg.export_original_pair:
            if progress:
                progress(None, "Renderizando par ORIGINAL: backing + guitar…")
            pair_dir = exports / "original"
            backing_out = pair_dir / self.final_name("backing", cfg.output_format)
            guitar_out = pair_dir / self.final_name("guitar", cfg.output_format)
            self.render_pair(
                [smap[s] for s in STEMS if s != "guitar"], smap["guitar"],
                backing_out, guitar_out, cfg.output_format, master_peak_dbfs,
            )
            created.extend([backing_out, guitar_out])

        if cfg.export_pitched_pair and pitch == 0 and cfg.export_original_pair:
            self.logger.info("Pitch 0: arquivo duplicado não foi gerado.")
        elif cfg.export_pitched_pair:
            pitched: dict[str, Path] = {}
            for stem in STEMS:
                src = smap[stem]
                dst = pitched_dir / f"{stem}_{pitch:+d}st.wav"
                if stem == "drums" or pitch == 0:
                    shutil.copy2(src, dst)
                else:
                    if progress:
                        progress(None, f"Pitch: {stem} {pitch:+d} st")
                    self.pitch_stem(src, dst, pitch, stem == "vocals" and cfg.vocal_formants)
                pitched[stem] = dst
            if progress:
                progress(None, f"Renderizando par AJUSTADO ({pitch:+d} st): backing + guitar…")
            pair_dir = exports / f"pitch_{pitch:+d}st"
            backing_out = pair_dir / self.final_name("backing", cfg.output_format)
            guitar_out = pair_dir / self.final_name("guitar", cfg.output_format)
            self.render_pair(
                [pitched[s] for s in STEMS if s != "guitar"], pitched["guitar"],
                backing_out, guitar_out, cfg.output_format, master_peak_dbfs,
            )
            created.extend([backing_out, guitar_out])

        if not cfg.keep_stems:
            shutil.rmtree(pitched_dir, ignore_errors=True)
        st.stage = "finished"
        return created

    @staticmethod
    def final_name(stem: str, fmt: str) -> str:
        return stem + (".flac" if fmt == "FLAC 24-bit" else ".wav")

    def _mix_float(self, inputs: list[Path], temp: Path) -> None:
        ffmpeg = tool_path("ffmpeg")
        if not ffmpeg:
            raise CommandError("O componente de áudio não está disponível. Verifique a página Sistema.")
        temp.parent.mkdir(parents=True, exist_ok=True)
        args = [ffmpeg, "-y", "-hide_banner"]
        for p in inputs:
            args += ["-i", str(p)]
        pads = "".join(f"[{i}:a]" for i in range(len(inputs)))
        args += [
            "-filter_complex", f"{pads}amix=inputs={len(inputs)}:normalize=0:dropout_transition=0[m]",
            "-map", "[m]", "-ar", "44100", "-ac", "2", "-c:a", "pcm_f32le", str(temp),
        ]
        self.runner.run(args, "Mixando backing em float32")

    def _encode_audio(self, src: Path, output: Path, fmt: str, gain_db: float = 0.0) -> None:
        ffmpeg = tool_path("ffmpeg")
        if not ffmpeg:
            raise CommandError("O componente de áudio não está disponível. Verifique a página Sistema.")
        output.parent.mkdir(parents=True, exist_ok=True)
        enc = [ffmpeg, "-y", "-hide_banner", "-i", str(src), "-ar", "44100", "-ac", "2"]
        if gain_db < -0.0001:
            enc += ["-af", f"volume={gain_db:.6f}dB"]
        if fmt == "FLAC 24-bit":
            enc += ["-c:a", "flac", "-compression_level", "8", "-sample_fmt", "s32", "-bits_per_raw_sample", "24", str(output)]
        elif fmt == "WAV 24-bit":
            enc += ["-c:a", "pcm_s24le", str(output)]
        else:
            enc += ["-c:a", "pcm_f32le", str(output)]
        self.runner.run(enc, f"Render final: {output.name}")

    def render_pair(self, backing_inputs: list[Path], guitar_input: Path,
                    backing_output: Path, guitar_output: Path, fmt: str, peak_target: float) -> None:
        """Renderiza backing e guitarra com um ÚNICO ganho compartilhado."""
        temp = backing_output.parent / ".__backing_float.wav"
        combined = backing_output.parent / ".__combined_float.wav"
        self._mix_float(backing_inputs, temp)
        # O teto é calculado sobre a RECOMBINAÇÃO backing+guitar, exatamente como
        # o usuário ouvirá no REAPER com os dois faders em 0 dB. O mesmo ganho
        # resultante é aplicado aos dois arquivos, preservando a relação entre eles.
        self._mix_float([temp, guitar_input], combined)
        combined_peak = self.measure_peak(combined)
        gain = peak_target - combined_peak if combined_peak is not None and combined_peak > peak_target else 0.0
        if gain < -0.0001:
            self.logger.info(
                "Par guitar/backing: recombinação atinge %.2f dBFS; aplicando ganho COMPARTILHADO %.2f dB aos dois arquivos.",
                combined_peak, gain,
            )
        else:
            self.logger.info("Par guitar/backing: sem ganho automático; relação de volumes preservada.")
        self._encode_audio(temp, backing_output, fmt, gain)
        self._encode_audio(guitar_input, guitar_output, fmt, gain)
        temp.unlink(missing_ok=True)
        combined.unlink(missing_ok=True)

    def mix_stems(self, inputs: list[Path], output: Path, fmt: str, peak_target: float):
        """Usado para previews. Exports finais em par usam render_pair()."""
        temp = output.parent / (output.stem + ".__mix_float.wav")
        self._mix_float(inputs, temp)
        peak = self.measure_peak(temp)
        gain = peak_target - peak if peak is not None and peak > peak_target else 0.0
        self._encode_audio(temp, output, fmt, gain)
        temp.unlink(missing_ok=True)

    @staticmethod
    def measure_peak(path:Path)->float|None:
        ffmpeg=tool_path("ffmpeg")
        if not ffmpeg:return None
        cp=subprocess.run([ffmpeg,"-hide_banner","-nostats","-i",str(path),"-af","volumedetect","-f","null","-"],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        text=(cp.stdout or "")+"\n"+(cp.stderr or "")
        m=re.findall(r"max_volume:\s*(-?(?:\d+(?:\.\d+)?|inf))\s*dB",text,re.I)
        if not m:return None
        return -999.0 if m[-1].lower()=="-inf" else float(m[-1])
