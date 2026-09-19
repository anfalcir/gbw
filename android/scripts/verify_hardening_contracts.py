#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

demucs = read("app/src/main/java/com/gbw/android/separation/DemucsSeparator.kt")
export = read("app/src/main/java/com/gbw/android/export/ProjectExportRenderer.kt")
source = read("app/src/main/java/com/gbw/android/source/OnlineSourcePreparer.kt")
remote = read("app/src/main/java/com/gbw/android/backup/SafBackupRemoteStore.kt")
scheduler = read("app/src/main/java/com/gbw/android/backup/BackupScheduler.kt")
ui = read("app/src/main/java/com/gbw/android/ui/GbwApp.kt")
project_ui = read("app/src/main/java/com/gbw/android/ui/ProjectScreens.kt")
logs_ui = read("app/src/main/java/com/gbw/android/ui/LogsScreen.kt")
project_json = read("app/src/main/java/com/gbw/android/project/ProjectJson.kt")

assert "AudioStorageBudget.requireAvailable" in demucs
assert "if (!success) outputDir.deleteRecursively()" in demucs
assert "currentCoroutineContext().ensureActive()" in demucs

assert "AudioStorageBudget.requireAvailable" in export
assert "if (!published) stagingRoot.deleteRecursively()" in export
assert "currentCoroutineContext().ensureActive()" in export

assert "AudioStorageBudget.sourcePrepareRequiredBytes" in source
assert "if (!success) workRoot.deleteRecursively()" in source
assert "currentCoroutineContext().ensureActive()" in source

assert "ProjectHashing.sha256(target) == rf.sha256" in remote
assert "stagingRoot.deleteRecursively()" in remote
assert "validateV2Revision" in remote

assert ".setRequiredNetworkType(NetworkType.CONNECTED)" in scheduler
assert "ExistingWorkPolicy.KEEP" in scheduler
assert "ExistingPeriodicWorkPolicy.UPDATE" in scheduler

assert "screenWidthDp >= 840" in ui
assert "ModalNavigationDrawer" in ui
assert "rememberSaveable" in ui
assert "schemaVersion in 1..ProjectManifest.CURRENT_SCHEMA_VERSION" in project_json

combined_ui = ui + project_ui + logs_ui
assert ".clickable(" not in combined_ui
assert "combinedClickable(" not in combined_ui
assert "fontSize =" not in combined_ui

required_tests = [
    "app/src/test/java/com/gbw/android/audio/AudioStorageBudgetTest.kt",
    "app/src/test/java/com/gbw/android/backup/BackupCoalescingPolicyTest.kt",
    "app/src/test/java/com/gbw/android/project/ProjectJsonMigrationTest.kt",
    "app/src/test/java/com/gbw/android/project/ProjectModelStressTest.kt",
    "app/src/test/java/com/gbw/android/export/SharedGainStressTest.kt",
]
for test in required_tests:
    assert (ROOT / test).is_file(), f"missing hardening test: {test}"

print("FINAL_HARDENING_CONTRACTS_OK")
