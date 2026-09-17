#!/usr/bin/env python3
"""Exact BS-RoFormer-SW checkpoint/compiler gate for GBW Android.

This gate intentionally keeps the 699 MB upstream checkpoint outside the repo.
It proves checkpoint identity, exact architecture/state_dict compatibility and
whether the exact real-valued inference core can be serialized for
ExecuTorch/XNNPACK. It does not yet claim production-shape latency/RAM parity or
physical-device homologation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import platform
import sys
import traceback
from pathlib import Path

import torch
import torch.nn.functional as F
import yaml
from einops import pack, rearrange, unpack
from ml_collections import ConfigDict

from bs_roformer.attend import Attend
from bs_roformer.inference import SafeLoaderWithTuple
from bs_roformer.utils import get_model_from_config

SOURCE_REVISION = "244cddd4f7611956eb1cc4958e82b65b4891c019"
MODEL_REVISION = "a443a2985534b3bc815ef54a5d446c6a0390f974"
CHECKPOINT_NAME = "BS-Rofo-SW-Fixed.ckpt"
CHECKPOINT_BYTES = 699_412_152
CHECKPOINT_SHA256 = "24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e"
CONFIG_NAME = "BS-Rofo-SW-Fixed.yaml"
CONFIG_BYTES = 4_613
CONFIG_SHA256 = "f9fada9f94e5ba2d2e4600196299459294bc5f532b314c209cc156ac63e4329b"
EXPECTED_STEMS = ["bass", "drums", "other", "vocals", "guitar", "piano"]
REPORT = Path("bs-roformer-exact-gate.json")
SUMMARY = Path("bs-roformer-exact-gate.md")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def exportable_flash_attn(
    self: Attend,
    q: torch.Tensor,
    k: torch.Tensor,
    v: torch.Tensor,
) -> torch.Tensor:
    if self.scale is not None:
        default_scale = q.shape[-1] ** -0.5
        q = q * (self.scale / default_scale)
    return F.scaled_dot_product_attention(
        q,
        k,
        v,
        dropout_p=self.dropout if self.training else 0.0,
    )


class MaskCore(torch.nn.Module):
    """Exact trained BS-RoFormer from real STFT input to real/imag masks."""

    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.band_split = model.band_split
        self.layers = model.layers
        self.final_norm = model.final_norm
        self.mask_estimators = model.mask_estimators

    def forward(self, stft_real: torch.Tensor) -> torch.Tensor:
        x = rearrange(stft_real, "b s f t c -> b (f s) t c")
        x = rearrange(x, "b f t c -> b t (f c)")
        x = self.band_split(x)

        time_v_residual = None
        freq_v_residual = None
        for time_transformer, freq_transformer in self.layers:
            x = rearrange(x, "b t f d -> b f t d")
            x, ps = pack([x], "* t d")
            x, next_time_v_residual = time_transformer(
                x, value_residual=time_v_residual
            )
            if time_v_residual is None:
                time_v_residual = next_time_v_residual
            x, = unpack(x, ps, "* t d")

            x = rearrange(x, "b f t d -> b t f d")
            x, ps = pack([x], "* f d")
            x, next_freq_v_residual = freq_transformer(
                x, value_residual=freq_v_residual
            )
            if freq_v_residual is None:
                freq_v_residual = next_freq_v_residual
            x, = unpack(x, ps, "* f d")

        x = self.final_norm(x)
        masks = torch.stack([fn(x) for fn in self.mask_estimators], dim=1)
        return rearrange(masks, "b n t (f c) -> b n f t c", c=2)


def stage(report: dict, name: str, fn):
    try:
        value = fn()
        report["stages"][name] = {"ok": True, "detail": value}
        return value
    except Exception as exc:
        report["stages"][name] = {
            "ok": False,
            "error_type": type(exc).__name__,
            "error": str(exc)[:12000],
            "traceback": traceback.format_exc()[-20000:],
        }
        return None


def write_report(report: dict) -> None:
    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    lines = [
        "# BS-RoFormer-SW exact Android compiler gate",
        "",
        f"- PyTorch: `{report['torch']}`",
        f"- ExecuTorch: `{report['executorch_target']}`",
        f"- Inference source: `openmirlab/bs-roformer-infer@{SOURCE_REVISION}`",
        f"- Model mirror revision: `enerjazzer/BS-ROFO-SW-Fixed@{MODEL_REVISION}`",
        f"- Checkpoint: `{CHECKPOINT_NAME}` — {CHECKPOINT_BYTES:,} bytes",
        f"- Checkpoint SHA-256: `{CHECKPOINT_SHA256}`",
        "- Scope: exact weights + exact architecture; compact STFT time axis for compiler feasibility",
        "- Not yet claimed: production 588800-sample shape, Android-device RAM/latency or auditory parity",
        "",
        "| Stage | Result |",
        "|---|---|",
    ]
    for name, result in report["stages"].items():
        lines.append(f"| `{name}` | {'PASS' if result['ok'] else 'FAIL'} |")
    lines += ["", "## Failures"]
    failures = 0
    for name, result in report["stages"].items():
        if not result["ok"]:
            failures += 1
            msg = result.get("error", "").replace("\n", " ")
            lines.append(
                f"- `{name}` — `{result.get('error_type')}`: {msg[:1600]}"
            )
    if failures == 0:
        lines.append("- None")
    SUMMARY.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(SUMMARY.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()

    torch.manual_seed(20260917)
    report = {
        "purpose": "Exact BS-RoFormer-SW Android compiler gate",
        "python": sys.version,
        "platform": platform.platform(),
        "torch": torch.__version__,
        "executorch_target": "1.3.1",
        "source_revision": SOURCE_REVISION,
        "source_version": "0.1.5",
        "model_provider": "enerjazzer/BS-ROFO-SW-Fixed",
        "model_revision": MODEL_REVISION,
        "checkpoint_name": CHECKPOINT_NAME,
        "checkpoint_bytes": CHECKPOINT_BYTES,
        "checkpoint_sha256": CHECKPOINT_SHA256,
        "config_name": CONFIG_NAME,
        "config_bytes": CONFIG_BYTES,
        "config_sha256": CONFIG_SHA256,
        "expected_stems": EXPECTED_STEMS,
        "compiler_probe_time_frames": 33,
        "production_time_frames_pending": True,
        "stages": {},
    }

    def verify_checkpoint():
        actual_bytes = args.checkpoint.stat().st_size
        actual_sha = sha256_file(args.checkpoint)
        if actual_bytes != CHECKPOINT_BYTES:
            raise AssertionError(
                f"checkpoint size mismatch: {actual_bytes} != {CHECKPOINT_BYTES}"
            )
        if actual_sha != CHECKPOINT_SHA256:
            raise AssertionError(
                f"checkpoint sha256 mismatch: {actual_sha} != {CHECKPOINT_SHA256}"
            )
        return {"bytes": actual_bytes, "sha256": actual_sha}

    def verify_config():
        actual_bytes = args.config.stat().st_size
        actual_sha = sha256_file(args.config)
        if actual_bytes != CONFIG_BYTES:
            raise AssertionError(
                f"config size mismatch: {actual_bytes} != {CONFIG_BYTES}"
            )
        if actual_sha != CONFIG_SHA256:
            raise AssertionError(
                f"config sha256 mismatch: {actual_sha} != {CONFIG_SHA256}"
            )
        return {"bytes": actual_bytes, "sha256": actual_sha}

    if stage(report, "checkpoint_integrity", verify_checkpoint) is None:
        write_report(report)
        return 1
    if stage(report, "config_integrity", verify_config) is None:
        write_report(report)
        return 1

    with args.config.open("r", encoding="utf-8") as handle:
        config = ConfigDict(yaml.load(handle, Loader=SafeLoaderWithTuple))

    def construct_and_load():
        instruments = list(config.training.instruments)
        if instruments != EXPECTED_STEMS:
            raise AssertionError(
                f"stem order mismatch: {instruments} != {EXPECTED_STEMS}"
            )
        if int(config.audio.sample_rate) != 44100:
            raise AssertionError(f"sample rate mismatch: {config.audio.sample_rate}")
        if int(config.audio.chunk_size) != 588800:
            raise AssertionError(f"chunk size mismatch: {config.audio.chunk_size}")

        model = get_model_from_config("bs_roformer", config).eval()
        state = torch.load(
            args.checkpoint,
            map_location="cpu",
            weights_only=True,
            mmap=True,
        )
        incompat = model.load_state_dict(state, strict=True, assign=True)
        if incompat.missing_keys or incompat.unexpected_keys:
            raise AssertionError(str(incompat))

        param_count = sum(p.numel() for p in model.parameters())
        param_bytes = sum(p.numel() * p.element_size() for p in model.parameters())
        return model, {
            "instruments": instruments,
            "sample_rate": int(config.audio.sample_rate),
            "chunk_size": int(config.audio.chunk_size),
            "num_parameters": param_count,
            "parameter_bytes": param_bytes,
            "state_tensors": len(state),
        }

    loaded = stage(report, "exact_model_construct_and_strict_load", construct_and_load)
    if loaded is None:
        write_report(report)
        return 1
    model, load_meta = loaded
    report["stages"]["exact_model_construct_and_strict_load"]["detail"] = load_meta

    def verify_attention_patch():
        attend = None
        for module in model.modules():
            if isinstance(module, Attend):
                attend = module
                break
        if attend is None:
            raise AssertionError("no Attend module found")
        q = torch.randn(1, 8, 33, 64)
        k = torch.randn(1, 8, 33, 64)
        v = torch.randn(1, 8, 33, 64)
        with torch.no_grad():
            reference = attend.flash_attn(q, k, v)
            candidate = exportable_flash_attn(attend, q, k, v)
        max_abs = (reference - candidate).abs().max().item()
        mean_abs = (reference - candidate).abs().mean().item()
        if not torch.allclose(reference, candidate, rtol=1e-5, atol=1e-6):
            raise AssertionError(
                f"attention patch drift: max_abs={max_abs}, mean_abs={mean_abs}"
            )
        Attend.flash_attn = exportable_flash_attn
        return {"max_abs": max_abs, "mean_abs": mean_abs}

    if stage(report, "attention_patch_equivalence", verify_attention_patch) is None:
        write_report(report)
        return 1

    core = MaskCore(model).eval()
    probe_input = torch.zeros(1, 2, 1025, 33, 2, dtype=torch.float32)

    exported = stage(
        report,
        "exact_core_torch_export",
        lambda: torch.export.export(core, (probe_input,), strict=True),
    )
    if exported is None:
        write_report(report)
        return 1
    report["stages"]["exact_core_torch_export"]["detail"] = {
        "input_shape": list(probe_input.shape)
    }

    def serialize_xnnpack():
        from executorch.backends.xnnpack.partition.xnnpack_partitioner import (
            XnnpackPartitioner,
        )
        from executorch.exir import to_edge_transform_and_lower

        lowered = to_edge_transform_and_lower(
            exported,
            partitioner=[XnnpackPartitioner()],
        )
        buffer = lowered.to_executorch().buffer
        return {
            "pte_bytes": len(buffer),
            "pte_sha256": hashlib.sha256(buffer).hexdigest(),
        }

    if stage(report, "exact_core_xnnpack_serialize", serialize_xnnpack) is None:
        write_report(report)
        return 1

    write_report(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
