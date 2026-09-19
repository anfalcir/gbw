#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def section(text: str, start: str, end: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]


repo = read("app/src/main/java/com/gbw/android/project/ProjectRepository.kt")
ui = read("app/src/main/java/com/gbw/android/ui/GbwApp.kt")
screens = read("app/src/main/java/com/gbw/android/ui/ProjectScreens.kt")
service = read("app/src/main/java/com/gbw/android/background/MediaProcessingService.kt")
source_worker = read("app/src/main/java/com/gbw/android/background/SourcePreparationWorker.kt")

publish_sep = section(repo, "fun publishSeparation(", "fun publishExport(")
publish_export = section(repo, "fun publishExport(", "fun setLastSynced(")
assert "setActive(projectId)" not in publish_sep, "publishSeparation must never switch the active project"
assert "setActive(projectId)" not in publish_export, "publishExport must never switch the active project"
assert "fun projectSeparationResult(projectId: String)" in repo

separation = section(ui, "private fun SeparationScreen(", "private fun SeparationResultsCard(")
assert "projectRepository.projectSeparationResult" in separation
assert "availableResult = validated" not in separation
assert "initialUriText" not in separation
assert 'picker.launch(arrayOf("audio/*"))' not in separation
assert "jobProjectId == p.projectId" in separation

assert "shellMessage" not in ui, "transient close events must not persist in the shell"
assert "SnackbarHostState" in ui
assert "sourceJobProjectId == activeProjectId" in ui

assert "projectMatchesSearch" in screens
assert "foldProjectSearchText" in screens
assert 'Text(if (isActive) "Continuar" else "Abrir")' in screens
assert "jobProjectId == p.projectId" in screens

assert "projects.publishSeparation(projectId, validated)" in service
assert "projects.adoptPreparedSource(" in source_worker

print("PROJECT_SCOPED_WORKFLOW_OK")
