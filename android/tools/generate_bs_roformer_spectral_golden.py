#!/usr/bin/env python3
"""Regenerate the committed BS-RoFormer spectral golden values.

This tool is intentionally not part of the lightweight Android CI because it
requires PyTorch. The committed constants in BsRoformerSpectralHostSmoke.cpp
were generated with this exact torch.stft contract, which is the one used by
BS-RoFormer-SW 0.1.5.
"""
import math
import torch

N = 4096
SR = 44100
n = torch.arange(N, dtype=torch.float32)
x = (
    0.31 * torch.sin(2 * math.pi * 440 * n / SR)
    + 0.17 * torch.cos(2 * math.pi * 997 * n / SR)
    + 0.001 * ((n.to(torch.int64) % 17).to(torch.float32) - 8)
)
window = torch.hann_window(2048, periodic=True)
z = torch.stft(
    x,
    n_fft=2048,
    hop_length=512,
    win_length=2048,
    window=window,
    center=True,
    pad_mode="reflect",
    normalized=False,
    onesided=True,
    return_complex=True,
)
y = torch.istft(
    z,
    n_fft=2048,
    hop_length=512,
    win_length=2048,
    window=window,
    center=True,
    normalized=False,
    onesided=True,
)

assert tuple(z.shape) == (1025, 9)
assert tuple(y.shape) == (4096,)
print("torch", torch.__version__)
print("stft_shape", tuple(z.shape))
print("istft_max_abs", float((y - x).abs().max()))
for frame in (0, 1, 4, 8):
    for bin_index in (0, 1, 20, 46, 1024):
        value = z[bin_index, frame]
        print(
            f"{{{frame}, {bin_index}, {float(value.real):.17g}f, "
            f"{float(value.imag):.17g}f}},"
        )
