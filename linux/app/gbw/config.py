from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from pathlib import Path

APP_NAME = "Guitar Backing Wizard"
APP_VERSION = "5.23.0"
APP_HOME = Path.home() / ".local" / "share" / "guitar-backing-wizard"
VENV_DIR = APP_HOME / "venv"
CONFIG_DIR = Path.home() / ".config" / "guitar-backing-wizard"
CONFIG_FILE = CONFIG_DIR / "settings.json"
DEFAULT_PROJECT_ROOT = Path.home() / "Music" / "GuitarBackings"

@dataclass
class Settings:
    appearance: str = "System"
    ui_scale: float = 1.25
    project_root: str = str(DEFAULT_PROJECT_ROOT)
    search_depth: str = "Robusta"
    default_separator: str = "A"
    default_output_format: str = "FLAC 24-bit"
    demucs_shifts_cpu: int = 1
    demucs_shifts_gpu: int = 5
    demucs_overlap: float = 0.5
    vocal_formants: bool = True
    master_peak_dbfs: float = -1.0


def load_settings() -> Settings:
    try:
        data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        allowed = {k: v for k, v in data.items() if k in Settings.__dataclass_fields__}
        return Settings(**allowed)
    except Exception:
        return Settings()


def save_settings(settings: Settings) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(asdict(settings), ensure_ascii=False, indent=2), encoding="utf-8")
