#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
repo_root = ROOT.parent
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (repo_root / ".github/workflows/android-release.yml").read_text(encoding="utf-8")
ignore = (repo_root / ".gitignore").read_text(encoding="utf-8")
third_party = (repo_root / "docs/ANDROID_THIRD_PARTY.md").read_text(encoding="utf-8")

required_env = (
    "GBW_RELEASE_KEYSTORE_PATH",
    "GBW_RELEASE_STORE_PASSWORD",
    "GBW_RELEASE_KEY_ALIAS",
    "GBW_RELEASE_KEY_PASSWORD",
)
for token in required_env:
    assert token in build, f"build.gradle.kts missing {token}"

assert 'create("production")' in build
assert 'signingConfig = signingConfigs.findByName("production")' in build
assert 'versionName = "6.0.0-rc3"' in build
assert 'versionCode = 24' in build

assert "workflow_dispatch:" in workflow
assert "\n  push:" not in workflow
assert "\n  pull_request:" not in workflow
for secret in (
    "GBW_RELEASE_KEYSTORE_B64",
    "GBW_RELEASE_STORE_PASSWORD",
    "GBW_RELEASE_KEY_ALIAS",
    "GBW_RELEASE_KEY_PASSWORD",
    "GBW_RELEASE_CERT_SHA256",
):
    assert f"secrets.{secret}" in workflow, f"release workflow missing {secret}"

assert "repository LICENSE is missing" in workflow
assert "production-private" in workflow
assert "homologation-public-test-key" not in workflow
assert "6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0" in workflow
assert "Production certificate must differ" in workflow

for pattern in ("*.jks", "*.keystore", "*.p12", "*.pfx"):
    assert pattern in ignore, f".gitignore missing {pattern}"

assert "youtubedl-android" in third_party
assert "htdemucs_6s" in third_party
assert "Release blocker" in third_party

print("RELEASE_PIPELINE_GATE_OK")
