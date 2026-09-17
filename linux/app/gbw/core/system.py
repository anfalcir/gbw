from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

from gbw.config import APP_HOME, VENV_DIR, APP_NAME, APP_VERSION


def detect_linux_system() -> dict[str, str]:
    info: dict[str, str] = {}
    p = Path("/etc/os-release")
    if p.exists():
        for raw in p.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" not in raw or raw.lstrip().startswith("#"):
                continue
            k, v = raw.split("=", 1)
            info[k.strip().lower()] = v.strip().strip('"')
    distro_id = info.get("id", "linux").lower()
    id_like = info.get("id_like", "").lower()
    blob = f"{distro_id} {id_like}"
    manager = next((x for x in ("apt-get", "dnf", "pacman", "zypper") if shutil.which(x)), "")
    if manager == "apt-get" or any(x in blob for x in ("debian", "ubuntu", "linuxmint")):
        family = "debian"
    elif manager == "dnf" or any(x in blob for x in ("fedora", "rhel", "centos")):
        family = "fedora"
    elif manager == "pacman" or "arch" in blob:
        family = "arch"
    elif manager == "zypper" or "suse" in blob:
        family = "suse"
    else:
        family = "unknown"
    return {
        "id": distro_id,
        "pretty": info.get("pretty_name", distro_id),
        "version": info.get("version_id", ""),
        "family": family,
        "manager": manager or "não detectado",
        "codename": info.get("ubuntu_codename") or info.get("version_codename", ""),
    }


def candidate_paths(name: str) -> list[Path]:
    out = [VENV_DIR / "bin" / name, APP_HOME / "bin" / name, Path.home() / ".local" / "bin" / name]
    found = shutil.which(name)
    if found:
        out.append(Path(found))
    return out


def tool_path(name: str) -> str | None:
    for p in candidate_paths(name):
        try:
            if p.exists() and os.access(p, os.X_OK):
                return str(p)
        except OSError:
            pass
    return None


def tool_version(name: str, args: list[str] | None = None) -> str:
    p = tool_path(name)
    if not p:
        return "ausente"
    for probe in (args or ["--version"], ["-version"], ["version"]):
        try:
            cp = subprocess.run([p, *probe], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=8)
            line = (cp.stdout or "").strip().splitlines()
            if line:
                return line[0][:140]
        except Exception:
            continue
    return p


def rubberband_r3_status() -> tuple[bool, str]:
    dedicated = tool_path("rubberband-r3")
    if dedicated:
        return True, f"R3 dedicado: {dedicated}"
    rb = tool_path("rubberband")
    if not rb:
        return False, "Rubber Band ausente"
    try:
        cp = subprocess.run([rb, "--help"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=8)
        txt = cp.stdout or ""
        if re.search(r"(?:^|\s)-3(?:\s|,|$)", txt) or "--fine" in txt.lower() or "r3" in txt.lower():
            return True, f"R3 via rubberband -3: {rb}"
        cp = subprocess.run([rb, "--version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=8)
        m = re.search(r"(\d+)\.(\d+)", cp.stdout or "")
        if m and int(m.group(1)) >= 3:
            return True, f"Rubber Band {m.group(0)} com R3: {rb}"
    except Exception:
        pass
    return False, f"Rubber Band encontrado, mas R3 não confirmado: {rb}"


def system_install_command() -> str:
    fam = detect_linux_system()["family"]
    if fam == "debian":
        return "sudo apt-get update && sudo apt-get install -y ffmpeg rubberband-cli python3-tk python3-venv python3-pip curl unzip ca-certificates"
    if fam == "fedora":
        return "sudo dnf install -y ffmpeg-free rubberband python3-tkinter python3-pip curl unzip ca-certificates"
    if fam == "arch":
        return "sudo pacman -S --needed ffmpeg rubberband tk python python-pip curl unzip ca-certificates"
    if fam == "suse":
        return "sudo zypper install ffmpeg rubberband python3-tk python3-pip curl unzip ca-certificates"
    return "Distribuição não reconhecida automaticamente; consulte README.md."


def diagnostic_report() -> str:
    sysinfo = detect_linux_system()
    r3_ok, r3_detail = rubberband_r3_status()
    lines = [
        f"{APP_NAME} {APP_VERSION}",
        f"Sistema: {sysinfo['pretty']} | família={sysinfo['family']} | gerenciador={sysinfo['manager']} | codename={sysinfo['codename']}",
        f"Python UI: {sys.version.split()[0]} ({sys.executable})",
        f"FFmpeg: {tool_version('ffmpeg')}",
        f"yt-dlp: {tool_version('yt-dlp')}",
        f"Deno: {tool_version('deno')}",
        f"Rubber Band R3: {'OK' if r3_ok else 'ATENÇÃO'} — {r3_detail}",
        f"BS-RoFormer: {tool_path('bs-roformer-infer') or 'ausente'}",
        f"Demucs: {tool_path('demucs') or 'ausente'}",
        f"NVIDIA/CUDA indicativo: {'sim' if tool_path('nvidia-smi') else 'não'}",
    ]
    try:
        import customtkinter as ctk
        lines.append(f"CustomTkinter: {getattr(ctk, '__version__', 'instalado')}")
    except Exception:
        lines.append("CustomTkinter: ausente/fallback")
    try:
        import librosa
        lines.append(f"librosa: {librosa.__version__}")
    except Exception:
        lines.append("librosa: ausente")
    return "\n".join(lines)


def system_health(separator_mode: str = "A") -> dict[str, object]:
    """Resumo rápido de saúde usado pela navegação.

    Só considera bloqueantes as dependências necessárias ao fluxo escolhido. Deno é
    tratado como recomendação (o yt-dlp pode continuar funcional sem ele em alguns
    cenários) e o separador alternativo não bloqueia quando não participa da estratégia.
    """
    blocking: list[str] = []
    warnings: list[str] = []

    if not tool_path("ffmpeg"):
        blocking.append("Componente de áudio ausente")
    if not tool_path("yt-dlp"):
        blocking.append("Download online indisponível")
    r3_ok, _ = rubberband_r3_status()
    if not r3_ok:
        blocking.append("Componente de pitch indisponível")

    mode = (separator_mode or "A").upper()
    if mode in ("A", "AB") and not tool_path("bs-roformer-infer"):
        blocking.append("Separador A indisponível")
    if mode in ("B", "AB") and not tool_path("demucs"):
        blocking.append("Separador B indisponível")

    if not tool_path("deno"):
        warnings.append("Suporte web opcional indisponível")
    if mode == "A" and not tool_path("demucs"):
        warnings.append("Separador B indisponível")
    if mode == "B" and not tool_path("bs-roformer-infer"):
        warnings.append("Separador A indisponível")

    return {
        "ok": not blocking,
        "blocking": blocking,
        "warnings": warnings,
        "summary": "Sistema pronto" if not blocking else f"{len(blocking)} problema(s) bloqueante(s)",
    }
