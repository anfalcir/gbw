from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from gbw.core.runner import CommandError, CommandRunner
from gbw.core.system import tool_path
from gbw.services.audio import AUDIO_EXTS, AudioService


LOSSLESS_CODECS = {
    "pcm_s8", "pcm_u8", "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be",
    "pcm_s32le", "pcm_s32be", "pcm_f32le", "pcm_f32be", "pcm_f64le", "pcm_f64be",
    "flac", "alac", "wavpack", "ape",
}
LOSSY_CODECS = {
    "mp3", "aac", "opus", "vorbis", "wmav1", "wmav2", "ac3", "eac3",
}


@dataclass
class QualityIssue:
    title: str
    detail: str
    ideal: str
    risk: str


@dataclass
class AudioInspection:
    path: Path
    container: str
    codec: str
    sample_rate: int
    channels: int
    channel_layout: str
    sample_fmt: str
    bit_depth: int | None
    is_float: bool
    is_lossless: bool
    duration: float
    bit_rate: int | None = None
    peak_dbfs: float | None = None
    status: str = "adequate"  # ideal / adequate / caution
    issues: list[QualityIssue] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    @property
    def status_label(self) -> str:
        return {"ideal": "Entrada ideal", "adequate": "Entrada adequada", "caution": "Entrada com ressalvas"}.get(self.status, "Entrada")

    @property
    def kind(self) -> str:
        return {"ideal": "success", "adequate": "accent", "caution": "warning"}.get(self.status, "muted")

    @property
    def channel_label(self) -> str:
        if self.channels == 1:
            return "Mono"
        if self.channels == 2:
            return "Estéreo"
        return f"{self.channels} canais"

    @property
    def depth_label(self) -> str:
        if self.is_float and self.bit_depth:
            return f"{self.bit_depth}-bit float"
        if self.bit_depth:
            return f"{self.bit_depth}-bit"
        return self.sample_fmt or "profundidade não informada"

    @property
    def format_label(self) -> str:
        ext = self.path.suffix.lower().lstrip(".").upper()
        return ext or self.container.upper() or self.codec.upper()

    @property
    def summary(self) -> str:
        return f"{self.format_label} • {self.sample_rate/1000:g} kHz • {self.depth_label} • {self.channel_label}"


class FilePitchService:
    """Ferramenta independente de pitch.

    A inspeção é própria desta ferramenta, mas a transformação chama AudioService,
    portanto workflow e arquivo avulso compartilham o mesmo motor Rubber Band R3.
    """

    def __init__(self, runner: CommandRunner, audio: AudioService):
        self.runner = runner
        self.audio = audio

    @staticmethod
    def supported(path: Path) -> bool:
        return path.suffix.lower() in AUDIO_EXTS

    @staticmethod
    def _bit_depth(stream: dict) -> tuple[int | None, bool]:
        sample_fmt = str(stream.get("sample_fmt") or "").lower()
        raw = stream.get("bits_per_raw_sample") or stream.get("bits_per_sample")
        try:
            if raw and int(raw) > 0:
                bits = int(raw)
            else:
                bits = None
        except Exception:
            bits = None
        is_float = sample_fmt.startswith("flt") or sample_fmt.startswith("dbl")
        if bits is None:
            if sample_fmt.startswith("u8"):
                bits = 8
            elif sample_fmt.startswith("s16"):
                bits = 16
            elif sample_fmt.startswith("s32"):
                bits = 32
            elif sample_fmt.startswith("s64"):
                bits = 64
            elif sample_fmt.startswith("flt"):
                bits = 32
            elif sample_fmt.startswith("dbl"):
                bits = 64
        return bits, is_float

    def inspect(self, path: Path, progress: Callable[[float | None, str], None] | None = None) -> AudioInspection:
        path = Path(path).expanduser().resolve()
        if not path.is_file():
            raise CommandError("Arquivo não encontrado.")
        if not self.supported(path):
            raise CommandError("Formato de áudio não reconhecido pelo GBW.")
        ffprobe = tool_path("ffprobe")
        ffmpeg = tool_path("ffmpeg")
        if not ffprobe or not ffmpeg:
            raise CommandError("O componente de áudio não está disponível. Verifique a página Sistema.")
        if progress:
            progress(None, "Analisando formato e qualidade…")
        data = self.runner.capture_json([
            ffprobe, "-v", "error", "-select_streams", "a:0",
            "-show_entries",
            "stream=codec_name,codec_long_name,sample_fmt,sample_rate,channels,channel_layout,bits_per_sample,bits_per_raw_sample,bit_rate:format=format_name,duration,bit_rate",
            "-of", "json", str(path),
        ], timeout=60)
        streams = data.get("streams") or []
        if not streams:
            raise CommandError("O arquivo não possui uma faixa de áudio válida.")
        stream = streams[0]
        fmt = data.get("format") or {}
        codec = str(stream.get("codec_name") or "").lower()
        container = str(fmt.get("format_name") or "")
        try:
            sample_rate = int(stream.get("sample_rate") or 0)
            channels = int(stream.get("channels") or 0)
        except Exception:
            sample_rate, channels = 0, 0
        if sample_rate <= 0 or channels <= 0:
            raise CommandError("Não foi possível identificar taxa de amostragem/canais do arquivo.")
        bit_depth, is_float = self._bit_depth(stream)
        try:
            duration = float(fmt.get("duration") or 0.0)
        except Exception:
            duration = 0.0
        try:
            bit_rate = int(stream.get("bit_rate") or fmt.get("bit_rate") or 0) or None
        except Exception:
            bit_rate = None
        lossless = codec in LOSSLESS_CODECS or codec.startswith("pcm_")
        if codec in LOSSY_CODECS:
            lossless = False

        if progress:
            progress(None, "Verificando nível e integridade…")
        # Uma decodificação rápida e completa também testa a integridade do stream.
        level_text = self.runner.run([
            ffmpeg, "-hide_banner", "-nostats", "-v", "info", "-xerror", "-i", str(path),
            "-map", "0:a:0", "-af", "volumedetect", "-f", "null", "-",
        ], "Inspeção de qualidade")
        peaks = re.findall(r"max_volume:\s*(-?(?:\d+(?:\.\d+)?|inf))\s*dB", level_text, re.I)
        peak_dbfs = None
        if peaks:
            peak_dbfs = -999.0 if peaks[-1].lower() == "-inf" else float(peaks[-1])

        result = AudioInspection(
            path=path, container=container, codec=codec, sample_rate=sample_rate,
            channels=channels, channel_layout=str(stream.get("channel_layout") or ""),
            sample_fmt=str(stream.get("sample_fmt") or ""), bit_depth=bit_depth,
            is_float=is_float, is_lossless=lossless, duration=duration,
            bit_rate=bit_rate, peak_dbfs=peak_dbfs,
        )

        ext = path.suffix.lower()
        if not lossless:
            br = f" ({bit_rate/1000:.0f} kbps)" if bit_rate else ""
            result.issues.append(QualityIssue(
                "Formato com perdas",
                f"{codec.upper() or ext.lstrip('.').upper()}{br} já descartou parte da informação de áudio.",
                "Exporte novamente do DAW em WAV 32-bit float, mantendo a taxa da sessão.",
                "A mudança de pitch pode tornar artefatos de compressão mais perceptíveis.",
            ))
        if sample_rate < 44100:
            result.issues.append(QualityIssue(
                "Taxa de amostragem baixa",
                f"O arquivo está em {sample_rate/1000:g} kHz.",
                "Use a mesma taxa da sessão; 44,1 ou 48 kHz são os padrões mais comuns.",
                "Há menos informação de alta frequência disponível para o processamento.",
            ))
        if lossless and bit_depth is not None and not is_float and bit_depth <= 16:
            result.issues.append(QualityIssue(
                "Profundidade reduzida",
                f"O arquivo está em {bit_depth}-bit inteiro.",
                "Para novo intercâmbio com o DAW, prefira WAV 32-bit float (ou WAV/FLAC 24-bit).",
                "Há menos resolução/margem numérica do que numa exportação em 24-bit ou float.",
            ))
        if peak_dbfs is not None and peak_dbfs >= -0.01:
            result.issues.append(QualityIssue(
                "Picos no limite digital",
                f"O pico medido está em aproximadamente {peak_dbfs:.2f} dBFS.",
                "Evite normalizar/limitar a exportação apenas para deixá-la mais alta e preserve margem quando possível.",
                "Picos já limitados ou clipados não podem ser recuperados pelo pitch e podem gerar artefatos mais evidentes.",
            ))
        elif peak_dbfs is not None and peak_dbfs > -1.0:
            result.notes.append(f"Pico alto ({peak_dbfs:.2f} dBFS), porém sem indicação suficiente para bloquear o processamento.")

        if result.issues:
            result.status = "caution"
        else:
            # O ideal operacional é WAV float32, sem obrigar resampling. 44,1/48 kHz
            # são ambos excelentes quando correspondem à sessão do usuário.
            is_wav = ext == ".wav" or "wav" in container.lower()
            if is_wav and is_float and bit_depth == 32 and sample_rate >= 44100:
                result.status = "ideal"
            else:
                result.status = "adequate"
                if lossless:
                    result.notes.append("Formato lossless adequado; não é necessário reexportar apenas para usar o GBW.")
        return result

    def process(self, inspection: AudioInspection, output: Path, semitones: int,
                formants: bool, output_format: str,
                progress: Callable[[float | None, str], None] | None = None) -> Path:
        if not -12 <= int(semitones) <= 12:
            raise CommandError("Informe um pitch entre -12 e +12 semitons.")
        return self.audio.pitch_file(
            inspection.path, Path(output), int(semitones), formants, output_format,
            inspection.sample_rate, inspection.channels, inspection.duration, progress,
        )
