#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

ui = read("app/src/main/java/com/gbw/android/ui/GbwApp.kt")
vm = read("app/src/main/java/com/gbw/android/ui/SourceSearchViewModel.kt")
worker = read("app/src/main/java/com/gbw/android/background/SourcePreparationWorker.kt")
providers = read("app/src/main/java/com/gbw/android/source/BandcampDiscoveryProvider.kt")
source_rules = read("app/src/main/java/com/gbw/android/domain/SourceSearch.kt")
score_bands = read("app/src/main/java/com/gbw/android/domain/SourceScoreBand.kt")
score_badge = read("app/src/main/java/com/gbw/android/ui/SourceScoreBadge.kt")
ytdlp = read("app/src/main/java/com/gbw/android/source/YtDlpDiscoveryProvider.kt")
policy = read("app/src/main/java/com/gbw/android/background/ForegroundServiceTypePolicy.kt")
manifest = read("app/src/main/AndroidManifest.xml")

# Search must outlive SourceScreen composition and remain project-scoped.
assert "viewModelScope.launch" in vm
assert "fun bindProject(value: String?)" in vm
assert "searchGeneration" in vm
assert "searchState.search {" in ui
assert "searchState.bindProject(active.projectId)" in ui

# Online preparation is persisted by WorkManager and must not consume the
# Android dataSync foreground-service quota.
assert "SourcePreparationWorker.enqueue" in ui
assert "MediaProcessingService.sourcePrepareIntent" not in ui
assert "OneTimeWorkRequestBuilder<SourcePreparationWorker>()" in worker
assert ".setRequiredNetworkType(NetworkType.CONNECTED)" in worker
assert "setForeground(" not in worker
assert "ForegroundInfo" not in worker

# Long media work uses the semantically correct type on Android 15+.
assert "FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING" in policy
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING' in manifest
assert 'android:foregroundServiceType="dataSync|mediaProcessing"' in manifest

# Only sources that can actually be prepared belong in primary online results.
assert "AppleMusicDiscoveryProvider()" not in providers
assert "APPLE_MUSIC" not in source_rules
assert ".filter { it.automaticDownloadSupported && !it.previewOnly }" in providers
assert "result.candidates.filter { it.automaticDownloadSupported && !it.previewOnly }" in vm

# Ranking remains sorted by the real score, and release UX exposes the exact
# normalized value with a non-color semantic label.
assert ".thenByDescending { it.score }" in source_rules
assert "score.coerceIn(0, 100)" in score_bands
assert "INTERMEDIATE_MIN = 55" in score_bands
assert "GOOD_MIN = 75" in score_bands
assert "SourceScoreBadge(candidate.score)" in ui
assert "contentDescription = accessibility" in score_badge
for label in ("Baixa", "Intermediária", "Boa"):
    assert label in score_bands

# One dead/geo-blocked/private YouTube result may not abort the whole search.
assert "YtDlpDiscoveryResilience.availableOrNull" in ytdlp
assert "if (out.isEmpty() && successfulSearches == 0" in ytdlp

# Errors/warnings are contextual in-page and also surfaced transiently.
for token in (
    "SourceNoticePlacement.LOCAL",
    "SourceNoticePlacement.SEARCH",
    "SourceNoticePlacement.PREPARATION",
    "SourceNoticePlacement.MANUAL_URL",
    "Toast.makeText",
):
    assert token in ui or token in vm

# Final UX has no standalone Logs page or Linux-baseline copy.
assert "LOGS(" not in ui
assert "LogsScreen(embedded = true)" in ui
assert "Linux v5.23" not in ui
assert "Baseline:" not in ui

print("ONLINE_SOURCE_RESILIENCE_GATE_OK")
