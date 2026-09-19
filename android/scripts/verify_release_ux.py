#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ui = (ROOT / "app/src/main/java/com/gbw/android/ui/GbwApp.kt").read_text(encoding="utf-8")
logs = (ROOT / "app/src/main/java/com/gbw/android/ui/LogsScreen.kt").read_text(encoding="utf-8")
store = (ROOT / "app/src/main/java/com/gbw/android/background/JobStore.kt").read_text(encoding="utf-8")
history = (ROOT / "app/src/main/java/com/gbw/android/background/JobHistoryStore.kt").read_text(encoding="utf-8")

assert "LOGS(" not in ui
assert "LogsScreen(embedded = true)" in ui
assert "PlaceholderScreen" not in ui
assert "Diagnóstico avançado" in ui
assert ui.count("Detalhes técnicos") >= 1
assert "Linux v5.23" not in ui
assert "Copiar tudo" in logs
assert "Limpar histórico" in logs
assert "Job:" in logs
assert "Projeto:" in logs
assert "JobHistoryStore" in store
assert "JobHistoryPolicy.shouldRecord" in store
assert "MAX_ENTRIES = 250" in history
assert "ProjectJobLinkStore" in history

print("RELEASE_UX_GATE_OK")
