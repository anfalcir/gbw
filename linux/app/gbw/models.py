from __future__ import annotations

from dataclasses import dataclass, asdict, field
from pathlib import Path
from typing import Any

TUNINGS: dict[str, tuple[int, ...]] = {
    "E Standard": (40, 45, 50, 55, 59, 64),
    "Eb Standard": (39, 44, 49, 54, 58, 63),
    "D Standard": (38, 43, 48, 53, 57, 62),
    "C# Standard": (37, 42, 47, 52, 56, 61),
    "C Standard": (36, 41, 46, 51, 55, 60),
    "B Standard": (35, 40, 45, 50, 54, 59),
    "Bb Standard": (34, 39, 44, 49, 53, 58),
    "A Standard": (33, 38, 43, 48, 52, 57),
    "Drop D": (38, 45, 50, 55, 59, 64),
    "Drop C#": (37, 44, 49, 54, 58, 63),
    "Drop C": (36, 43, 48, 53, 57, 62),
    "Drop B": (35, 42, 47, 52, 56, 61),
    "Drop Bb": (34, 41, 46, 51, 55, 60),
    "Drop A": (33, 40, 45, 50, 54, 59),
    "Drop Ab": (32, 39, 44, 49, 53, 58),
    "Drop G": (31, 38, 43, 48, 52, 57),
    "Drop F#": (30, 37, 42, 47, 51, 56),
    "Drop F": (29, 36, 41, 46, 50, 55),
}
TUNING_NAMES = tuple(TUNINGS.keys())
PC_NAMES = ("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
NOTE_PCS = {"C":0,"C#":1,"Db":1,"D":2,"D#":3,"Eb":3,"E":4,"F":5,"F#":6,"Gb":6,"G":7,"G#":8,"Ab":8,"A":9,"A#":10,"Bb":10,"B":11}
STEMS = ("vocals", "drums", "bass", "guitar", "piano", "other")


def tuning_delta(source: str, target: str) -> int | None:
    a, b = TUNINGS.get(source), TUNINGS.get(target)
    if not a or not b:
        return None
    diffs = {y - x for x, y in zip(a, b)}
    return diffs.pop() if len(diffs) == 1 else None


def tuning_notes(name: str) -> str:
    return " ".join(f"{PC_NAMES[m % 12]}{m // 12 - 1}" for m in TUNINGS.get(name, ()))


def transpose_key_label(key: str, mode: str, semitones: int) -> str:
    pc = NOTE_PCS.get(key)
    if pc is None:
        return "—"
    note = PC_NAMES[(pc + semitones) % 12]
    return f"{note} {'maior' if mode == 'major' else 'menor'}"

@dataclass
class SourceCandidate:
    score: int
    source: str
    quality: str
    title: str
    uploader: str
    url: str
    format_id: str = "bestaudio/best"
    official: bool = False
    reason: str = ""
    quality_bonus: int = 0
    duration: float = 0.0
    preview_only: bool = False
    duration_warning: bool = False

@dataclass
class WorkflowConfig:
    artist: str = ""
    song: str = ""
    source_mode: str = "auto"  # auto/local/url
    local_file: str = ""
    source_url: str = ""
    selected_candidate: dict[str, Any] | None = None
    separator_mode: str = "A"  # A/B/AB
    final_separator: str = "A"
    device: str = "auto"
    demucs_shifts: int = 1
    demucs_overlap: float = 0.5
    pitch_mode: str = "tuning"  # tuning/manual/none
    original_tuning: str = ""
    target_tuning: str = "Drop D"
    semitones: int = 0
    vocal_formants: bool = True
    output_format: str = "FLAC 24-bit"
    export_source_original: bool = True
    export_original_pair: bool = True
    export_pitched_pair: bool = True
    keep_stems: bool = True

@dataclass
class ProjectState:
    project_dir: str = ""
    source_native: str = ""
    prepared_wav: str = ""
    separator_dirs: dict[str, str] = field(default_factory=dict)
    previews: dict[str, str] = field(default_factory=dict)
    stem_maps: dict[str, dict[str, str]] = field(default_factory=dict)
    tuning_analysis: dict[str, Any] | None = None
    tuning_analysis_separator: str = ""
    tuning_confirmed: bool = False
    stage: str = "new"

    def path(self, field_name: str) -> Path | None:
        value = getattr(self, field_name, "")
        return Path(value) if value else None

@dataclass
class ProjectDocument:
    config: WorkflowConfig = field(default_factory=WorkflowConfig)
    state: ProjectState = field(default_factory=ProjectState)

    def to_dict(self) -> dict[str, Any]:
        return {"config": asdict(self.config), "state": asdict(self.state)}
