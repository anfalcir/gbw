#!/usr/bin/env python3
"""Build the exact production BS-RoFormer-SW ExecuTorch/XNNPACK artifact.

The resulting .pte contains the real-valued transformer/mask core only.
STFT, complex mask multiplication, ISTFT and outer overlap-add remain native
Android responsibilities, matching the proven PFFFT spectral boundary.

This script consumes the exact 699 MB source checkpoint and the packaged
bs-roformer-infer 0.1.5 config, then exports the static production shape
T=1151 corresponding to the Linux baseline chunk_size=588800.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.resources
import json
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
CONFIG_BYTES = 686
CONFIG_SHA256 = "52df622c95ff3c1f4e1389f476ed737581a2c2dc12324d52c9763be9ccd2be2b"

STEMS = ["bass", "drums", "other", "vocals", "guitar", "piano"]
CHUNK = 588_800
N_FFT = 2048
HOP = 512
BINS = N_FFT // 2 + 1
TIME = 1 + CHUNK // HOP
CHANNELS = 2
COMPLEX = 2
EXECUTORCH_VERSION = "1.3.1"
OUTPUT_NAME = "GBW-BS-RoFormer-SW-executorch-1.3.1-T1151.pte"


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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    if args.checkpoint.stat().st_size != CHECKPOINT_BYTES:
        raise SystemExit("checkpoint byte size mismatch")
    if sha256_file(args.checkpoint) != CHECKPOINT_SHA256:
        raise SystemExit("checkpoint sha256 mismatch")

    config_resource = importlib.resources.files("bs_roformer") / "configs" / CONFIG_NAME
    with importlib.resources.as_file(config_resource) as config_path:
        config_path = Path(config_path)
        if config_path.stat().st_size != CONFIG_BYTES:
            raise SystemExit("packaged config byte size mismatch")
        if sha256_file(config_path) != CONFIG_SHA256:
            raise SystemExit("packaged config sha256 mismatch")
        with config_path.open("r", encoding="utf-8") as handle:
            config = ConfigDict(yaml.load(handle, Loader=SafeLoaderWithTuple))

    if list(config.training.instruments) != STEMS:
        raise SystemExit("stem order mismatch")
    if int(config.inference.chunk_size) != CHUNK:
        raise SystemExit("chunk size mismatch")
    if int(config.inference.num_overlap) != 2:
        raise SystemExit("overlap mismatch")
    if int(config.model.stft_n_fft) != N_FFT:
        raise SystemExit("n_fft mismatch")
    if int(config.model.stft_hop_length) != HOP:
        raise SystemExit("hop mismatch")
    if TIME != 1151:
        raise SystemExit("production STFT time geometry mismatch")

    model = get_model_from_config("bs_roformer", config).eval()
    state = torch.load(
        args.checkpoint,
        map_location="cpu",
        weights_only=True,
        mmap=True,
    )
    incompatible = model.load_state_dict(state, strict=True, assign=True)
    if incompatible.missing_keys or incompatible.unexpected_keys:
        raise SystemExit(str(incompatible))

    Attend.flash_attn = exportable_flash_attn
    core = MaskCore(model).eval()

    input_shape = (1, CHANNELS, BINS, TIME, COMPLEX)
    output_shape = (1, len(STEMS), BINS * CHANNELS, TIME, COMPLEX)
    example = torch.zeros(input_shape, dtype=torch.float32)

    print("Exporting exact production core", input_shape, "->", output_shape, flush=True)
    exported = torch.export.export(core, (example,), strict=True)

    from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
    from executorch.exir import to_edge_transform_and_lower

    print("Lowering exact production core to XNNPACK", flush=True)
    lowered = to_edge_transform_and_lower(
        exported,
        partitioner=[XnnpackPartitioner()],
    )
    program = lowered.to_executorch()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    pte = args.output_dir / OUTPUT_NAME
    with pte.open("wb") as handle:
        handle.write(program.buffer)

    pte_bytes = pte.stat().st_size
    pte_sha = sha256_file(pte)
    parameter_count = sum(p.numel() for p in model.parameters())
    parameter_bytes = sum(p.numel() * p.element_size() for p in model.parameters())

    metadata = {
        "artifact": pte.name,
        "pte_bytes": pte_bytes,
        "pte_sha256": pte_sha,
        "executorch_version": EXECUTORCH_VERSION,
        "backend": "XNNPACK",
        "source_revision": SOURCE_REVISION,
        "model_revision": MODEL_REVISION,
        "checkpoint_name": CHECKPOINT_NAME,
        "checkpoint_bytes": CHECKPOINT_BYTES,
        "checkpoint_sha256": CHECKPOINT_SHA256,
        "packaged_config_name": CONFIG_NAME,
        "packaged_config_bytes": CONFIG_BYTES,
        "packaged_config_sha256": CONFIG_SHA256,
        "parameter_count": parameter_count,
        "parameter_bytes": parameter_bytes,
        "input_shape": list(input_shape),
        "output_shape": list(output_shape),
        "input_float_count": int(torch.tensor(input_shape).prod().item()),
        "output_float_count": int(torch.tensor(output_shape).prod().item()),
        "chunk_frames": CHUNK,
        "stft_n_fft": N_FFT,
        "stft_hop": HOP,
        "stems": STEMS,
    }

    metadata_path = args.output_dir / "BS-RoFormer-SW-executorch-metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    # Drop compiler/model objects before independently testing that the emitted
    # file is parseable by the ExecuTorch runtime.
    del program, lowered, exported, example, core, model, state
    gc.collect()

    print("Loading emitted PTE with ExecuTorch runtime", flush=True)
    from executorch.runtime import Runtime
    runtime_program = Runtime.get().load_program(str(pte))
    runtime_program.load_method("forward")

    sha_file = args.output_dir / "SHA256SUMS.txt"
    sha_file.write_text(pte_sha + "  " + pte.name + "\n", encoding="utf-8")

    print("BSROFORMER_PRODUCTION_PTE_OK")
    print(json.dumps(metadata, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
