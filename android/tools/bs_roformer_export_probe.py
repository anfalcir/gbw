#!/usr/bin/env python3
"""Bounded Android export feasibility probes for BS-RoFormer.

This is NOT a product inference path and does not validate the exact 699 MB
checkpoint. It exercises the operator families and Android-export seam against
openmirlab/bs-roformer-infer 0.1.5 while keeping CI cost bounded.

Scopes:
- core: numerical equivalence of the attention adaptation, then export/lower the
  real-valued transformer + mask-estimator core.
- full: separately probe the full waveform graph including STFT, complex mask
  multiplication and ISTFT. The workflow runs this scope under an external
  timeout so it can never hide the higher-value core result.
"""
from __future__ import annotations

import argparse
import json
import platform
import sys
import traceback
from pathlib import Path

import torch
import torch.nn.functional as F
from einops import pack, rearrange, unpack
from bs_roformer.attend import Attend
from bs_roformer.bs_roformer import BSRoformer

SOURCE_REVISION = "244cddd4f7611956eb1cc4958e82b65b4891c019"
TARGET_CHECKPOINT_SHA256 = "24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e"


def make_model() -> BSRoformer:
    """Reduced compiler probe preserving the load-bearing operator families."""
    return BSRoformer(
        dim=32,
        depth=1,
        stereo=True,
        num_stems=6,
        time_transformer_depth=1,
        freq_transformer_depth=1,
        dim_head=8,
        heads=2,
        attn_dropout=0.0,
        ff_dropout=0.0,
        flash_attn=True,
        dim_freqs_in=1025,
        stft_n_fft=2048,
        stft_hop_length=512,
        stft_win_length=2048,
        stft_normalized=False,
        mask_estimator_depth=2,
    ).eval()


def exportable_flash_attn(
    self: Attend,
    q: torch.Tensor,
    k: torch.Tensor,
    v: torch.Tensor,
) -> torch.Tensor:
    """Same eval-time SDPA math, without the non-traceable CUDA selector.

    Upstream already calls F.scaled_dot_product_attention. The removed
    torch.backends.cuda.sdp_kernel(...) context only selects the host CUDA
    implementation. ExecuTorch/backends must own backend selection after export.
    """
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
    """Upstream BSRoformer from post-STFT real tensor to real-valued masks.

    Input:  [batch, stereo=2, freq=1025, time, complex_component=2]
    Output: [batch, stems=6, freq*stereo, time, complex_component=2]
    """

    def __init__(self, model: BSRoformer):
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
        mask = torch.stack([fn(x) for fn in self.mask_estimators], dim=1)
        return rearrange(mask, "b n t (f c) -> b n f t c", c=2)


def new_report(scope: str) -> dict:
    return {
        "purpose": "BS-RoFormer Android export feasibility; not a release gate",
        "scope": scope,
        "python": sys.version,
        "platform": platform.platform(),
        "torch": torch.__version__,
        "executorch_target": "1.3.1",
        "source_revision": SOURCE_REVISION,
        "source_version": "0.1.5",
        "target_model": "roformer-model-bs-roformer-sw-by-jarredou",
        "target_checkpoint": "BS-Rofo-SW-Fixed.ckpt",
        "target_checkpoint_bytes": 699412152,
        "target_checkpoint_sha256": TARGET_CHECKPOINT_SHA256,
        "target_stems": ["bass", "drums", "other", "vocals", "guitar", "piano"],
        "probe_is_reduced": True,
        "stages": {},
    }


def stage(report: dict, name: str, fn):
    try:
        value = fn()
        report["stages"][name] = {"ok": True, "detail": str(value)[:4000]}
        return value
    except Exception as exc:  # diagnostic by design
        report["stages"][name] = {
            "ok": False,
            "error_type": type(exc).__name__,
            "error": str(exc)[:12000],
            "traceback": traceback.format_exc()[-20000:],
        }
        return None


def write_report(report: dict) -> None:
    scope = report["scope"]
    report_path = Path(f"bs-roformer-export-probe-{scope}.json")
    summary_path = Path(f"bs-roformer-export-probe-{scope}.md")
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    lines = [
        f"# BS-RoFormer export probe — {scope}",
        "",
        f"- PyTorch: `{report['torch']}`",
        f"- ExecuTorch target: `{report['executorch_target']}`",
        f"- Upstream: `openmirlab/bs-roformer-infer@{report['source_revision']}` (0.1.5)",
        "- Scope: reduced-width/depth operator-feasibility probe; **not** exact-model validation",
        "- Attention adaptation: remove only the upstream CUDA SDPA backend-selection context manager",
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
                f"- `{name}` — `{result.get('error_type')}`: {msg[:1400]}"
            )
    if failures == 0:
        lines.append("- None")

    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(summary_path.read_text(encoding="utf-8"))


def run_core() -> dict:
    report = new_report("core")
    torch.manual_seed(20260917)
    model = make_model()
    audio = torch.randn(1, 2, 8192)

    def verify_attention_patch():
        with torch.no_grad():
            reference = model(audio)
        Attend.flash_attn = exportable_flash_attn
        with torch.no_grad():
            candidate = model(audio)

        if reference.shape != candidate.shape:
            raise AssertionError(
                f"shape changed: {reference.shape} vs {candidate.shape}"
            )
        max_abs = (reference - candidate).abs().max().item()
        mean_abs = (reference - candidate).abs().mean().item()
        if not torch.allclose(reference, candidate, rtol=1e-5, atol=1e-6):
            raise AssertionError(
                f"SDPA patch changed output: max_abs={max_abs} mean_abs={mean_abs}"
            )
        return {
            "shape": list(candidate.shape),
            "max_abs": max_abs,
            "mean_abs": mean_abs,
        }

    parity = stage(report, "attention_patch_equivalence", verify_attention_patch)
    if parity is None:
        return report

    core = MaskCore(model).eval()
    stft_real = torch.randn(1, 2, 1025, 33, 2)
    core_ep = stage(
        report,
        "core_torch_export",
        lambda: torch.export.export(core, (stft_real,), strict=True),
    )

    if core_ep is not None:
        def portable_edge():
            from executorch.exir import to_edge
            return to_edge(core_ep)

        edge = stage(report, "core_to_edge", portable_edge)
        if edge is not None:
            stage(
                report,
                "core_to_executorch_portable",
                lambda: len(edge.to_executorch().buffer),
            )

        def xnnpack_lower():
            from executorch.backends.xnnpack.partition.xnnpack_partitioner import (
                XnnpackPartitioner,
            )
            from executorch.exir import to_edge_transform_and_lower

            lowered = to_edge_transform_and_lower(
                core_ep,
                partitioner=[XnnpackPartitioner()],
            )
            return len(lowered.to_executorch().buffer)

        stage(report, "core_to_executorch_xnnpack", xnnpack_lower)

    return report


def run_full() -> dict:
    report = new_report("full")
    torch.manual_seed(20260917)
    model = make_model()
    Attend.flash_attn = exportable_flash_attn
    # 4096 is sufficient to exercise STFT/complex/ISTFT while keeping the
    # compiler probe bounded. Exact chunk-size deployment is a later gate.
    audio = torch.randn(1, 2, 4096)

    full_ep = stage(
        report,
        "full_torch_export",
        lambda: torch.export.export(model, (audio,), strict=True),
    )
    if full_ep is not None:
        def full_edge():
            from executorch.exir import to_edge
            return to_edge(full_ep)

        edge_full = stage(report, "full_to_edge", full_edge)
        if edge_full is not None:
            stage(
                report,
                "full_to_executorch_portable",
                lambda: len(edge_full.to_executorch().buffer),
            )
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scope", choices=("core", "full"), required=True)
    args = parser.parse_args()

    report = run_core() if args.scope == "core" else run_full()
    write_report(report)
    # Stage failures are findings, not infrastructure failures. The JSON/MD
    # evidence drives the next engineering decision.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
