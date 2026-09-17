#!/usr/bin/env python3
"""Android runtime feasibility probe for BS-RoFormer.

This is deliberately NOT a product inference path. It asks narrow compiler/runtime
questions against the exact 0.1.5 source revision used as the Linux baseline:

1. Does removing only the non-semantic CUDA SDPA context manager preserve eager
   inference numerically while making attention traceable?
2. Can torch.export capture the upstream full forward graph (including STFT,
   complex mask multiplication and ISTFT)?
3. If the spectral DSP is the blocker, can torch.export + ExecuTorch capture and
   lower the real-valued transformer/mask-estimator core instead?

The probe uses a reduced transformer width/depth to bound CI cost while preserving
the same operator families, stereo/six-stem contract, frequency partition and STFT
configuration. Passing this probe does NOT validate the exact 699 MB checkpoint.
"""
from __future__ import annotations

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

REPORT = Path("bs-roformer-export-probe.json")
SUMMARY = Path("bs-roformer-export-probe.md")


def make_model() -> BSRoformer:
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


def exportable_flash_attn(self: Attend, q: torch.Tensor, k: torch.Tensor, v: torch.Tensor) -> torch.Tensor:
    """Semantically equivalent eval-time SDPA without the CUDA context manager.

    Upstream wraps F.scaled_dot_product_attention in
    torch.backends.cuda.sdp_kernel(...). That context manager selects an
    implementation backend, but is not part of the mathematical model and is not
    traceable by torch.export. ExecuTorch/backends must own backend selection after
    export, so the export path calls the same PyTorch SDPA primitive directly.
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

    A native Android DSP layer can own STFT/ISTFT and real/imag multiplication if
    this core is exportable while the full model is not.
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
            x, next_time_v_residual = time_transformer(x, value_residual=time_v_residual)
            if time_v_residual is None:
                time_v_residual = next_time_v_residual
            x, = unpack(x, ps, "* t d")

            x = rearrange(x, "b f t d -> b t f d")
            x, ps = pack([x], "* f d")
            x, next_freq_v_residual = freq_transformer(x, value_residual=freq_v_residual)
            if freq_v_residual is None:
                freq_v_residual = next_freq_v_residual
            x, = unpack(x, ps, "* f d")

        x = self.final_norm(x)
        mask = torch.stack([fn(x) for fn in self.mask_estimators], dim=1)
        return rearrange(mask, "b n t (f c) -> b n f t c", c=2)


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


def main() -> int:
    torch.manual_seed(20260917)
    report = {
        "purpose": "BS-RoFormer Android export feasibility; not a release gate",
        "python": sys.version,
        "platform": platform.platform(),
        "torch": torch.__version__,
        "executorch_target": "1.3.1",
        "source_revision": "244cddd4f7611956eb1cc4958e82b65b4891c019",
        "source_version": "0.1.5",
        "target_model": "roformer-model-bs-roformer-sw-by-jarredou",
        "target_checkpoint": "BS-Rofo-SW-Fixed.ckpt",
        "target_checkpoint_bytes": 699412152,
        "target_checkpoint_sha256": "24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e",
        "target_stems": ["bass", "drums", "other", "vocals", "guitar", "piano"],
        "probe_is_reduced": True,
        "stages": {},
    }

    model = make_model()
    audio = torch.randn(1, 2, 16_384)

    def verify_attention_patch():
        with torch.no_grad():
            reference = model(audio)
        # Patch only the non-traceable backend-selection wrapper.
        Attend.flash_attn = exportable_flash_attn
        with torch.no_grad():
            candidate = model(audio)
        if reference.shape != candidate.shape:
            raise AssertionError(f"shape changed: {reference.shape} vs {candidate.shape}")
        max_abs = (reference - candidate).abs().max().item()
        mean_abs = (reference - candidate).abs().mean().item()
        if not torch.allclose(reference, candidate, rtol=1e-5, atol=1e-6):
            raise AssertionError(f"SDPA patch changed output: max_abs={max_abs} mean_abs={mean_abs}")
        return {"shape": list(candidate.shape), "max_abs": max_abs, "mean_abs": mean_abs}

    stage(report, "attention_patch_equivalence", verify_attention_patch)

    full_ep = stage(report, "full_torch_export", lambda: torch.export.export(model, (audio,), strict=True))

    core = MaskCore(model).eval()
    stft_real = torch.randn(1, 2, 1025, 33, 2)
    core_ep = stage(report, "core_torch_export", lambda: torch.export.export(core, (stft_real,), strict=True))

    if core_ep is not None:
        def portable_edge():
            from executorch.exir import to_edge
            return to_edge(core_ep)
        edge = stage(report, "core_to_edge", portable_edge)
        if edge is not None:
            stage(report, "core_to_executorch_portable", lambda: len(edge.to_executorch().buffer))

        def xnnpack_lower():
            from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
            from executorch.exir import to_edge_transform_and_lower
            lowered = to_edge_transform_and_lower(core_ep, partitioner=[XnnpackPartitioner()])
            return len(lowered.to_executorch().buffer)
        stage(report, "core_to_executorch_xnnpack", xnnpack_lower)

    if full_ep is not None:
        def full_edge():
            from executorch.exir import to_edge
            return to_edge(full_ep)
        edge_full = stage(report, "full_to_edge", full_edge)
        if edge_full is not None:
            stage(report, "full_to_executorch_portable", lambda: len(edge_full.to_executorch().buffer))

    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")

    lines = [
        "# BS-RoFormer export probe",
        "",
        f"- PyTorch: `{report['torch']}`",
        f"- ExecuTorch target: `{report['executorch_target']}`",
        f"- Upstream: `openmirlab/bs-roformer-infer@{report['source_revision']}` (0.1.5)",
        "- Scope: reduced-width/depth operator-feasibility probe; **not** exact-model validation",
        "- Attention adaptation: remove only upstream CUDA SDPA backend-selection context manager; numerical equivalence is measured before export",
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
            lines.append(f"- `{name}` — `{result.get('error_type')}`: {msg[:1200]}")
    if failures == 0:
        lines.append("- None")
    SUMMARY.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(SUMMARY.read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
