#!/usr/bin/env python3
"""One-shot Android runtime feasibility probe for BS-RoFormer.

This is deliberately NOT a product inference path. It asks two narrow questions
against the source revision that matches the Linux 0.1.5 baseline:

1. Can torch.export capture the upstream full forward graph (including STFT,
   complex mask multiplication and ISTFT)?
2. If not, can it capture and lower the real-valued transformer/mask-estimator
   core when STFT/ISTFT and complex multiplication are kept outside the model?

The probe uses a reduced-dimension model with the same operator families to keep
CI cost bounded. Exact checkpoint/shape deployment is a later gate and must not
be inferred from a successful reduced probe.
"""
from __future__ import annotations

import json
import platform
import sys
import traceback
from pathlib import Path

import torch
from einops import pack, rearrange, unpack
from bs_roformer.bs_roformer import BSRoformer

REPORT = Path("bs-roformer-export-probe.json")
SUMMARY = Path("bs-roformer-export-probe.md")


def make_model() -> BSRoformer:
    # Same operator families and six-stem/stereo contract as BS-Rofo-SW-Fixed,
    # but intentionally smaller transformer width/depth for a bounded compiler
    # feasibility probe. Frequency partition and STFT contract stay exact.
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


class MaskCore(torch.nn.Module):
    """Upstream BSRoformer forward from post-STFT real tensor to real masks.

    Input shape: [batch, stereo, freq=1025, time, complex_component=2].
    Output shape: [batch, 6, freq*stereo, time, complex_component=2].

    Native Android DSP can own STFT/ISTFT and real/imag complex multiplication
    if this core is exportable while the full model is not.
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
    report = {
        "purpose": "BS-RoFormer Android export feasibility; not a release gate",
        "python": sys.version,
        "platform": platform.platform(),
        "torch": torch.__version__,
        "source_revision": "244cddd4f7611956eb1cc4958e82b65b4891c019",
        "source_version": "0.1.5",
        "target_model": "roformer-model-bs-roformer-sw-by-jarredou",
        "target_checkpoint": "BS-Rofo-SW-Fixed.ckpt",
        "target_checkpoint_bytes": 699412152,
        "target_checkpoint_sha256": "24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e",
        "probe_is_reduced": True,
        "stages": {},
    }

    model = make_model()
    # 16,384 samples keeps the eager shape valid while avoiding a costly
    # 588,800-sample CPU run during compiler investigation.
    audio = torch.randn(1, 2, 16_384)

    full_ep = stage(report, "full_torch_export", lambda: torch.export.export(model, (audio,), strict=True))

    # The post-STFT tensor uses the exact 1025 bins and stereo/real-imag layout.
    # A small time axis is sufficient to exercise axial attention and mask heads.
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

    # If full export captured successfully, separately test Edge conversion.
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
        f"- Upstream: `openmirlab/bs-roformer-infer@{report['source_revision']}` (0.1.5)",
        "- Scope: reduced-width/depth operator-feasibility probe; **not** exact-model validation",
        "",
        "| Stage | Result |",
        "|---|---|",
    ]
    for name, result in report["stages"].items():
        lines.append(f"| `{name}` | {'PASS' if result['ok'] else 'FAIL'} |")
    lines += ["", "## Failures"]
    for name, result in report["stages"].items():
        if not result["ok"]:
            msg = result.get("error", "").replace("\n", " ")
            lines.append(f"- `{name}` — `{result.get('error_type')}`: {msg[:1000]}")
    SUMMARY.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(SUMMARY.read_text(encoding="utf-8"))
    # Diagnostic workflow succeeds if it produced evidence. Individual stage
    # PASS/FAIL is the finding and is consumed by the next engineering decision.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
