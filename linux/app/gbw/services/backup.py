from __future__ import annotations

import hashlib
import json
import shutil
import tempfile
import zipfile
from dataclasses import fields
from datetime import datetime
from pathlib import Path
from typing import Iterable

from gbw.config import APP_NAME, APP_VERSION
from gbw.models import ProjectDocument, WorkflowConfig, ProjectState
from gbw.project import ProjectManager, slugify, normalize_project_text, normalize_project_metadata


PROJECT_BACKUP_MARKER = "gbw_project_backup.json"
APP_BACKUP_MARKER = "gbw_app_backup.json"
PROJECT_BACKUP_FORMAT = 1
APP_BACKUP_FORMAT = 1


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _safe_name(value: str) -> str:
    return slugify(value)[:90] or "gbw-backup"


class BackupService:
    """Backups compactos de projeto e backups portáteis do aplicativo."""

    def __init__(self, app_root: Path, logger):
        self.app_root = Path(app_root).resolve()
        self.logger = logger

    @staticmethod
    def _check_cancel(cancel_event=None) -> None:
        if cancel_event is not None and cancel_event.is_set():
            raise RuntimeError("Operação cancelada pelo usuário.")

    @staticmethod
    def _zip_tree(zip_path: Path, source_root: Path, cancel_event=None) -> None:
        zip_path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
            for path in sorted(source_root.rglob("*")):
                BackupService._check_cancel(cancel_event)
                if path.is_file():
                    zf.write(path, path.relative_to(source_root).as_posix())

    @staticmethod
    def _copy_if_exists(source: Path | None, dest: Path) -> Path | None:
        if source is None or not source.exists() or not source.is_file():
            return None
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
        return dest

    def backup_project(self, doc: ProjectDocument, destination_dir: Path, cancel_event=None) -> Path:
        normalize_project_metadata(doc)
        project_dir = Path(doc.state.project_dir or "")
        if not project_dir.exists():
            raise ValueError("Nenhum projeto válido está aberto.")

        destination_dir = Path(destination_dir).expanduser()
        destination_dir.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        title = " - ".join(x for x in (doc.config.artist, doc.config.song) if x) or project_dir.name
        zip_path = destination_dir / f"GBW_Projeto_{_safe_name(title)}_{stamp}.zip"

        with tempfile.TemporaryDirectory(prefix="gbw-project-backup-") as td:
            root = Path(td)
            payload_dir = root / "files"
            copied: list[dict] = []

            def record(src: Path | None, rel: str) -> None:
                self._check_cancel(cancel_event)
                if src is None or not src.exists() or not src.is_file():
                    return
                dst = payload_dir / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
                copied.append({
                    "path": f"files/{Path(rel).as_posix()}",
                    "size": dst.stat().st_size,
                    "sha256": _sha256(dst),
                })

            # Fonte nativa: pequena e essencial para restaurar/reprocessar no futuro.
            source_native = Path(doc.state.source_native) if doc.state.source_native else None
            if source_native and source_native.exists():
                record(source_native, f"source/{source_native.name}")

            # WAV preparado: evita novo download/decodificação e permite retomar da separação.
            prepared = Path(doc.state.prepared_wav) if doc.state.prepared_wav else None
            if prepared and prepared.exists():
                record(prepared, f"prepared/{prepared.name}")

            # Resultados finais: são o conteúdo mais importante do projeto.
            exports_dir = project_dir / "exports"
            if exports_dir.exists():
                for file in sorted(exports_dir.rglob("*")):
                    if file.is_file():
                        record(file, f"exports/{file.relative_to(exports_dir).as_posix()}")

            # Análise de afinação como JSON independente para restauração simples.
            if doc.state.tuning_analysis:
                tuning_file = payload_dir / "metadata" / "tuning_analysis.json"
                tuning_file.parent.mkdir(parents=True, exist_ok=True)
                tuning_file.write_text(
                    json.dumps(doc.state.tuning_analysis, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                copied.append({
                    "path": "files/metadata/tuning_analysis.json",
                    "size": tuning_file.stat().st_size,
                    "sha256": _sha256(tuning_file),
                })

            manifest = {
                "format": "guitar-backing-wizard-project-backup",
                "format_version": PROJECT_BACKUP_FORMAT,
                "created_at": datetime.now().isoformat(timespec="seconds"),
                "app_version": APP_VERSION,
                "artist": doc.config.artist,
                "song": doc.config.song,
                "config": doc.to_dict().get("config", {}),
                "state": {
                    "tuning_confirmed": bool(getattr(doc.state, "tuning_confirmed", False)),
                    "stage_at_backup": doc.state.stage,
                    "tuning_analysis_separator": doc.state.tuning_analysis_separator,
                },
                "contents": copied,
                "restore_note": (
                    "Backup compacto: preserva fonte, WAV preparado, análise e exports. "
                    "Stems intermediários não são incluídos; ao restaurar, a separação pode ser refeita."
                ),
            }
            (root / PROJECT_BACKUP_MARKER).write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            self._check_cancel(cancel_event)
            self._zip_tree(zip_path, root, cancel_event)

        self.logger.info("Backup do projeto salvo: %s", zip_path)
        return zip_path

    @staticmethod
    def _validate_zip_member(name: str) -> None:
        p = Path(name)
        if p.is_absolute() or ".." in p.parts:
            raise ValueError("Backup contém caminho inválido.")

    def restore_project(self, backup_zip: Path, manager: ProjectManager, cancel_event=None) -> Path:
        backup_zip = Path(backup_zip).expanduser()
        if not backup_zip.exists():
            raise FileNotFoundError("Arquivo de backup não encontrado.")

        restored: ProjectDocument | None = None
        created_dir: Path | None = None
        try:
            with tempfile.TemporaryDirectory(prefix="gbw-project-restore-") as td:
                root = Path(td)
                with zipfile.ZipFile(backup_zip, "r") as zf:
                    names = zf.namelist()
                    for name in names:
                        self._validate_zip_member(name)
                    if PROJECT_BACKUP_MARKER not in names:
                        raise ValueError("Este arquivo não é um backup de projeto do GBW.")
                    for member in zf.infolist():
                        self._check_cancel(cancel_event)
                        zf.extract(member, root)

                info = json.loads((root / PROJECT_BACKUP_MARKER).read_text(encoding="utf-8"))
                if info.get("format") != "guitar-backing-wizard-project-backup":
                    raise ValueError("Formato de backup de projeto inválido.")

                for item in info.get("contents", []):
                    self._check_cancel(cancel_event)
                    rel = str(item.get("path", ""))
                    self._validate_zip_member(rel)
                    f = root / rel
                    if not f.exists():
                        raise ValueError(f"Backup incompleto: {rel}")
                    expected = str(item.get("sha256", ""))
                    if expected and _sha256(f) != expected:
                        raise ValueError(f"Falha de integridade no backup: {rel}")

                artist = str(info.get("artist", ""))
                song = str(info.get("song", ""))
                doc = manager.create(artist, song)
                project_dir = Path(doc.state.project_dir)
                created_dir = project_dir

                cdata = dict(info.get("config", {}) or {})
                cdata["artist"] = normalize_project_text(cdata.get("artist", artist))
                cdata["song"] = normalize_project_text(cdata.get("song", song))
                allowed_cfg = {f.name for f in fields(WorkflowConfig)}
                cfg = WorkflowConfig(**{k: v for k, v in cdata.items() if k in allowed_cfg})

                files_root = root / "files"
                source_native = ""
                prepared_wav = ""
                if (files_root / "source").exists():
                    for src in sorted((files_root / "source").iterdir()):
                        self._check_cancel(cancel_event)
                        if src.is_file():
                            dst = project_dir / "source" / src.name
                            shutil.copy2(src, dst)
                            if not source_native:
                                source_native = str(dst)
                if (files_root / "prepared").exists():
                    for src in sorted((files_root / "prepared").iterdir()):
                        self._check_cancel(cancel_event)
                        if src.is_file():
                            dst = project_dir / "prepared" / src.name
                            shutil.copy2(src, dst)
                            if not prepared_wav:
                                prepared_wav = str(dst)
                if (files_root / "exports").exists():
                    for src in sorted((files_root / "exports").rglob("*")):
                        self._check_cancel(cancel_event)
                        if src.is_file():
                            rel = src.relative_to(files_root / "exports")
                            dst = project_dir / "exports" / rel
                            dst.parent.mkdir(parents=True, exist_ok=True)
                            shutil.copy2(src, dst)

                tuning_analysis = None
                tuning_file = files_root / "metadata" / "tuning_analysis.json"
                if tuning_file.exists():
                    tuning_analysis = json.loads(tuning_file.read_text(encoding="utf-8"))

                state_info = dict(info.get("state", {}) or {})
                st = ProjectState(
                    project_dir=str(project_dir),
                    source_native=source_native,
                    prepared_wav=prepared_wav,
                    tuning_analysis=tuning_analysis,
                    tuning_analysis_separator=str(state_info.get("tuning_analysis_separator", "")),
                    tuning_confirmed=bool(state_info.get("tuning_confirmed", False)),
                    stage="source_prepared" if prepared_wav else "new",
                )
                restored = ProjectDocument(config=cfg, state=st)
                manager.save(restored)

            manifest_path = manager.manifest_path(restored)
            self.logger.info("Backup de projeto restaurado: %s", manifest_path)
            return manifest_path
        except Exception:
            if created_dir and created_dir.exists():
                shutil.rmtree(created_dir, ignore_errors=True)
            raise

    @staticmethod
    def _iter_app_files(root: Path) -> Iterable[Path]:
        excluded_dirs = {
            ".git", "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache",
            "venv", ".venv", "logs", "cache", "projects",
        }
        excluded_suffixes = {".pyc", ".pyo"}
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            rel = path.relative_to(root)
            if any(part in excluded_dirs for part in rel.parts):
                continue
            if path.suffix in excluded_suffixes:
                continue
            yield path

    def backup_application(self, destination_dir: Path, cancel_event=None) -> Path:
        destination_dir = Path(destination_dir).expanduser()
        destination_dir.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        zip_path = destination_dir / f"Guitar_Backing_Wizard_v{APP_VERSION}_Backup_{stamp}.zip"

        required = ["gbw", "guitar_backing_wizard.py", "run.sh", "install.sh", "requirements.txt"]
        missing = [name for name in required if not (self.app_root / name).exists()]
        if missing:
            raise ValueError(
                "A instalação atual não contém todos os arquivos necessários para um backup portátil: "
                + ", ".join(missing)
                + ". Execute o install.sh desta versão e tente novamente."
            )

        with tempfile.TemporaryDirectory(prefix="gbw-app-backup-") as td:
            root = Path(td) / "guitar_backing_wizard"
            root.mkdir(parents=True, exist_ok=True)
            file_manifest = []
            for src in self._iter_app_files(self.app_root):
                self._check_cancel(cancel_event)
                rel = src.relative_to(self.app_root)
                dst = root / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
                file_manifest.append({
                    "path": rel.as_posix(),
                    "size": dst.stat().st_size,
                    "sha256": _sha256(dst),
                })

            info = {
                "format": "guitar-backing-wizard-app-backup",
                "format_version": APP_BACKUP_FORMAT,
                "app_name": APP_NAME,
                "app_version": APP_VERSION,
                "created_at": datetime.now().isoformat(timespec="seconds"),
                "portable_install": "Extraia o ZIP e execute ./install.sh no Linux.",
                "excludes": ["projetos", "músicas", "logs", "cache", "venv", "configurações pessoais"],
                "files": file_manifest,
            }
            (root / APP_BACKUP_MARKER).write_text(
                json.dumps(info, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            self._check_cancel(cancel_event)
            self._zip_tree(zip_path, root.parent, cancel_event)

        self.logger.info("Backup do GBW salvo: %s", zip_path)
        return zip_path
