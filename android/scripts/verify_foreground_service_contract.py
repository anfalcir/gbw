#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

service = read("app/src/main/java/com/gbw/android/background/MediaProcessingService.kt")
policy = read("app/src/main/java/com/gbw/android/background/ForegroundServiceTypePolicy.kt")
manifest = read("app/src/main/AndroidManifest.xml")
jobs = read("app/src/main/java/com/gbw/android/background/JobStore.kt")
history = read("app/src/main/java/com/gbw/android/background/JobHistoryStore.kt")
logs = read("app/src/main/java/com/gbw/android/ui/LogsScreen.kt")

assert "ServiceCompat.startForeground" not in service
assert "sdkInt >= Build.VERSION_CODES.Q" in service
assert "startForeground(" in service
assert "declaredForegroundServiceType()" in service
assert "PackageManager.ComponentInfoFlags.of(0)" in service
assert "FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING" in policy
assert "sdkInt >= 35" in policy
assert "sdkInt >= 29" in policy
for exception_name in (
    "InvalidForegroundServiceTypeException",
    "MissingForegroundServiceTypeException",
    "ForegroundServiceStartNotAllowedException",
    "SecurityException",
    "IllegalArgumentException",
):
    assert exception_name in policy
for token in ("requested=", "declared=", "api=", "sanitizeTechnicalMessage"):
    assert token in policy
assert 'android.permission.FOREGROUND_SERVICE' in manifest
assert 'android.permission.FOREGROUND_SERVICE_DATA_SYNC' in manifest
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING' in manifest
assert 'android:foregroundServiceType="dataSync|mediaProcessing"' in manifest
assert "val diagnostic: String? = null" in jobs
assert '.put("diagnostic"' in jobs
assert "diagnostic = job.diagnostic" in history
assert "Detalhes técnicos" in logs
assert "Diagnóstico: " in logs

print("FOREGROUND_SERVICE_CONTRACT_OK")
