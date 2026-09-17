from __future__ import annotations

from pathlib import Path

from gbw.models import ProjectDocument, STEMS, tuning_delta

WORKFLOW_STEPS = (
    ("source", "Fonte"),
    ("separation", "Separação"),
    ("tuning", "Afinação & Pitch"),
    ("export", "Exportação"),
)

STEP_HINTS = {
    "source": "Escolha a fonte.",
    "separation": "Separe a guitarra.",
    "tuning": "Ajuste a afinação.",
    "export": "Exporte os arquivos finais.",
}


def _path_exists(value: str) -> bool:
    return bool(value and Path(value).exists())


def source_complete(doc: ProjectDocument) -> bool:
    st = doc.state
    return _path_exists(st.prepared_wav)


def separation_complete(doc: ProjectDocument) -> bool:
    if not source_complete(doc):
        return False
    cfg, st = doc.config, doc.state
    mode = cfg.final_separator or ("B" if cfg.separator_mode == "B" else "A")
    smap = st.stem_maps.get(mode, {}) or {}
    return all(stem in smap and _path_exists(str(smap[stem])) for stem in STEMS)


def tuning_complete(doc: ProjectDocument) -> bool:
    if not separation_complete(doc):
        return False
    st, cfg = doc.state, doc.config
    if getattr(st, "tuning_confirmed", False):
        if cfg.pitch_mode == "tuning":
            return bool(cfg.original_tuning and cfg.target_tuning and tuning_delta(cfg.original_tuning, cfg.target_tuning) is not None)
        if cfg.pitch_mode == "manual":
            return -12 <= int(cfg.semitones) <= 12
        if cfg.pitch_mode == "none":
            return True
    # Compatibilidade: projetos já finalizados de versões anteriores devem aparecer completos.
    return st.stage == "finished"


def export_complete(doc: ProjectDocument) -> bool:
    if not tuning_complete(doc):
        return False
    st = doc.state
    if st.stage != "finished" or not st.project_dir:
        return False
    exports = Path(st.project_dir) / "exports"
    if not exports.exists():
        return False
    # Basta existir pelo menos um arquivo final; o manifesto registra as opções escolhidas.
    return any(p.is_file() for p in exports.rglob("*"))


def workflow_states(doc: ProjectDocument) -> dict[str, bool]:
    states = {
        "source": source_complete(doc),
        "separation": separation_complete(doc),
        "tuning": tuning_complete(doc),
        "export": export_complete(doc),
    }
    # Força linearidade visual mesmo se um manifesto externo estiver inconsistente.
    previous = True
    for key, _ in WORKFLOW_STEPS:
        states[key] = bool(previous and states[key])
        previous = states[key]
    return states


def active_step(doc: ProjectDocument) -> str:
    states = workflow_states(doc)
    for key, _ in WORKFLOW_STEPS:
        if not states[key]:
            return key
    return "export"


def completed_count(doc: ProjectDocument) -> int:
    states = workflow_states(doc)
    return sum(1 for key, _ in WORKFLOW_STEPS if states[key])


def step_number(key: str) -> int:
    for idx, (step_key, _) in enumerate(WORKFLOW_STEPS, start=1):
        if step_key == key:
            return idx
    return 0


def step_label(key: str) -> str:
    for step_key, label in WORKFLOW_STEPS:
        if step_key == key:
            return label
    return key


def header_workflow_text(doc: ProjectDocument) -> str:
    done = completed_count(doc)
    if done >= len(WORKFLOW_STEPS):
        return "Workflow concluído • 4 de 4 etapas"
    key = active_step(doc)
    number = step_number(key)
    label = step_label(key)
    return f"Etapa {number} de 4 • {label} — {STEP_HINTS[key]}"
