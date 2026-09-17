from __future__ import annotations

import json
import re
import shutil
from dataclasses import fields
from datetime import datetime
from pathlib import Path
from typing import Any

from gbw.models import ProjectDocument, WorkflowConfig, ProjectState
from gbw.config import APP_VERSION




def normalize_project_text(value: str) -> str:
    """Canonicaliza nomes visíveis do projeto no padrão Inicial Maiúscula.

    Colapsa espaços e aplica capitalização por palavra de forma Unicode-aware.
    Caminhos técnicos/slug não são alterados por esta função.
    """
    text = " ".join(str(value or "").strip().split())
    return text.title() if text else ""


def normalize_project_metadata(doc: ProjectDocument) -> ProjectDocument:
    """Aplica a convenção visual de artista/música ao documento inteiro."""
    doc.config.artist = normalize_project_text(doc.config.artist)
    doc.config.song = normalize_project_text(doc.config.song)
    return doc

def slugify(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^\w\s.-]", "", value)
    value = re.sub(r"[\s_]+", "-", value)
    value = re.sub(r"-{2,}", "-", value).strip("-.")
    return value or "projeto-guitarra"

class ProjectManager:
    def __init__(self, root: Path):
        self.root = root.expanduser()
        self.root.mkdir(parents=True, exist_ok=True)

    def create(self, artist: str, song: str) -> ProjectDocument:
        artist = normalize_project_text(artist)
        song = normalize_project_text(song)
        name = slugify(" - ".join(x for x in (artist, song) if x) or "projeto-guitarra")
        d = self.root / name
        if d.exists():
            d = self.root / f"{name}-{datetime.now():%Y%m%d-%H%M%S}"
        for sub in ("source", "prepared", "separation/A_bs_roformer", "separation/B_demucs", "previews", "pitched", "exports", "logs"):
            (d / sub).mkdir(parents=True, exist_ok=True)
        doc = ProjectDocument(config=WorkflowConfig(artist=artist, song=song), state=ProjectState(project_dir=str(d)))
        self.save(doc)
        return doc

    @staticmethod
    def manifest_path(doc: ProjectDocument) -> Path:
        return Path(doc.state.project_dir) / "workflow_manifest.json"

    def save(self, doc: ProjectDocument) -> None:
        normalize_project_metadata(doc)
        p = self.manifest_path(doc)
        p.parent.mkdir(parents=True, exist_ok=True)
        payload = doc.to_dict()
        payload.update({"app_version": APP_VERSION, "updated_at": datetime.now().isoformat(timespec="seconds")})
        p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    @staticmethod
    def load(path: Path) -> ProjectDocument:
        data = json.loads(path.read_text(encoding="utf-8"))
        cdata = dict(data.get("config", {}) or {})
        cfields = {f.name for f in fields(WorkflowConfig)}
        # Compatibilidade com manifests v3/v4.
        if "source_url" not in cdata and cdata.get("youtube_url"):
            cdata["source_url"] = cdata.get("youtube_url")
        if "selected_candidate" not in cdata and cdata.get("source_url") and cdata.get("source_format_id"):
            cdata["selected_candidate"] = {
                "url": cdata.get("source_url"),
                "format_id": cdata.get("source_format_id"),
                "quality": cdata.get("source_quality", ""),
            }
        cdata["artist"] = normalize_project_text(cdata.get("artist", ""))
        cdata["song"] = normalize_project_text(cdata.get("song", ""))
        cfg = WorkflowConfig(**{k:v for k,v in cdata.items() if k in cfields})

        if isinstance(data.get("state"), dict):
            sdata = dict(data.get("state") or {})
        else:
            # Formato legado: estado ficava no nível superior.
            previews = {}
            preview_dir = path.parent / "previews"
            for mode in ("A", "B"):
                pp = preview_dir / f"{mode}_backing_original_sem_guitarra.wav"
                if pp.exists(): previews[mode] = str(pp)
            tuning_analysis = None
            tuning_file = path.parent / "tuning_analysis.json"
            if tuning_file.exists():
                try: tuning_analysis = json.loads(tuning_file.read_text(encoding="utf-8"))
                except Exception: pass
            sdata = {
                "project_dir": str(path.parent),
                "source_native": data.get("source_native") or "",
                "prepared_wav": data.get("prepared_wav") or "",
                "separator_dirs": data.get("separator_dirs") or {},
                "previews": previews,
                "stem_maps": data.get("stems") or {},
                "tuning_analysis": tuning_analysis,
                "tuning_analysis_separator": str((tuning_analysis or {}).get("separator", "")),
                "stage": data.get("stage") or "legacy",
            }
        sfields = {f.name for f in fields(ProjectState)}
        st = ProjectState(**{k:v for k,v in sdata.items() if k in sfields})
        if not st.project_dir: st.project_dir = str(path.parent)
        return ProjectDocument(config=cfg, state=st)

    def list_projects(self) -> list[Path]:
        return sorted(self.root.glob("*/workflow_manifest.json"), key=lambda p: p.stat().st_mtime, reverse=True)

    def delete_project(self, manifest: Path) -> Path:
        manifest = Path(manifest).expanduser().resolve()
        root = self.root.resolve()
        project_dir = manifest.parent
        try:
            project_dir.relative_to(root)
        except ValueError as exc:
            raise ValueError("Projeto fora da pasta configurada.") from exc
        if project_dir == root or manifest.name != "workflow_manifest.json":
            raise ValueError("Projeto inválido para exclusão.")
        if not manifest.exists():
            raise FileNotFoundError("Projeto não encontrado.")
        shutil.rmtree(project_dir)
        return project_dir
