#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Análise local de tom e afinação de guitarra para o Guitar Backing Wizard.

Entrada: mix/prepared WAV e stem de guitarra.
Saída: JSON em stdout.

O algoritmo é deliberadamente conservador: estima tom musical com chroma CQT,
frequência global de afinação e pontua afinações de guitarra a partir de:
- recorrência de notas graves por pYIN;
- energia absoluta por semitom em CQT;
- presença das seis cordas soltas esperadas;
- penalidade para energia recorrente abaixo da corda grave candidata.

A etapa online e a fusão de evidências ficam na GUI principal.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
import librosa

NOTE_NAMES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]

TUNINGS = {
    "E Standard": [40, 45, 50, 55, 59, 64],
    "Eb Standard": [39, 44, 49, 54, 58, 63],
    "D Standard": [38, 43, 48, 53, 57, 62],
    "C# Standard": [37, 42, 47, 52, 56, 61],
    "C Standard": [36, 41, 46, 51, 55, 60],
    "B Standard": [35, 40, 45, 50, 54, 59],
    "Bb Standard": [34, 39, 44, 49, 53, 58],
    "A Standard": [33, 38, 43, 48, 52, 57],
    "Drop D": [38, 45, 50, 55, 59, 64],
    "Drop C#": [37, 44, 49, 54, 58, 63],
    "Drop C": [36, 43, 48, 53, 57, 62],
    "Drop B": [35, 42, 47, 52, 56, 61],
    "Drop Bb": [34, 41, 46, 51, 55, 60],
    "Drop A": [33, 40, 45, 50, 54, 59],
    "Drop Ab": [32, 39, 44, 49, 53, 58],
    "Drop G": [31, 38, 43, 48, 52, 57],
    "Drop F#": [30, 37, 42, 47, 51, 56],
    "Drop F": [29, 36, 41, 46, 50, 55],
}

# Krumhansl-Schmuckler, ordem C..B
MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88], dtype=float)
MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17], dtype=float)


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    den = float(np.linalg.norm(a) * np.linalg.norm(b))
    return float(np.dot(a, b) / den) if den > 1e-12 else 0.0


def midi_name(m: int) -> str:
    octv = m // 12 - 1
    return f"{NOTE_NAMES[m % 12]}{octv}"


def analyze_key(path: Path) -> dict:
    y, sr = librosa.load(path, sr=22050, mono=True, duration=480.0)
    if y.size < sr:
        raise RuntimeError("Áudio curto demais para análise tonal")

    # Atenua transientes de bateria antes do chroma.
    y_h = librosa.effects.harmonic(y, margin=4.0)
    tuning = float(librosa.estimate_tuning(y=y_h, sr=sr))
    chroma = librosa.feature.chroma_cqt(
        y=y_h, sr=sr, hop_length=2048, bins_per_octave=36,
        n_chroma=12, tuning=tuning
    )
    # Evita que silêncio pese tanto quanto regiões musicais.
    energy = np.sum(chroma, axis=0)
    if np.any(energy > 0):
        keep = energy >= np.percentile(energy[energy > 0], 25)
        chroma = chroma[:, keep] if np.any(keep) else chroma
    v = np.mean(chroma, axis=1)
    v = v / (np.linalg.norm(v) + 1e-12)

    candidates = []
    for tonic in range(12):
        candidates.append((cosine(v, np.roll(MAJOR_PROFILE, tonic)), tonic, "major"))
        candidates.append((cosine(v, np.roll(MINOR_PROFILE, tonic)), tonic, "minor"))
    candidates.sort(reverse=True)
    top = candidates[0]
    second = candidates[1]
    margin = max(0.0, top[0] - second[0])
    confidence = int(max(35, min(95, 45 + margin * 260 + max(0, top[0] - 0.65) * 50)))

    top3 = []
    for score, tonic, mode in candidates[:3]:
        top3.append({
            "key": NOTE_NAMES[tonic],
            "mode": mode,
            "label": f"{NOTE_NAMES[tonic]} {'maior' if mode == 'major' else 'menor'}",
            "score": round(float(score), 4),
        })

    a4 = 440.0 * (2.0 ** (tuning / 12.0))
    cents = tuning * 100.0
    return {
        "key": NOTE_NAMES[top[1]],
        "mode": top[2],
        "label": f"{NOTE_NAMES[top[1]]} {'maior' if top[2] == 'major' else 'menor'}",
        "confidence": confidence,
        "score": round(float(top[0]), 4),
        "alternatives": top3,
        "tuning_offset_bins": round(tuning, 5),
        "a4_hz": round(a4, 2),
        "a4_cents": round(cents, 1),
    }


def normalize01(x: np.ndarray) -> np.ndarray:
    x = np.asarray(x, dtype=float)
    if x.size == 0:
        return x
    lo = float(np.percentile(x, 5))
    hi = float(np.percentile(x, 95))
    if hi <= lo + 1e-12:
        return np.zeros_like(x)
    return np.clip((x - lo) / (hi - lo), 0, 1)


def analyze_guitar(path: Path) -> dict:
    # 16 kHz é suficiente para as fundamentais relevantes e deixa pYIN bem mais leve.
    y, sr = librosa.load(path, sr=16000, mono=True, duration=480.0)
    if y.size < sr:
        raise RuntimeError("Stem de guitarra curto demais")
    y_h = librosa.effects.harmonic(y, margin=2.0)

    fmin_note = "F1"
    fmin = float(librosa.note_to_hz(fmin_note))
    fmax = float(librosa.note_to_hz("E4"))
    base_midi = int(round(float(librosa.hz_to_midi(fmin))))

    # CQT de semitom absoluto: ótimo para distinguir C2, C#2, D2 etc.
    cqt_tuning = float(librosa.estimate_tuning(y=y_h, sr=sr))
    bins_per_octave = 36  # 3 bins por semitom: reduz vazamento entre notas vizinhas.
    C = np.abs(librosa.cqt(
        y=y_h, sr=sr, hop_length=1024, fmin=fmin,
        n_bins=144, bins_per_octave=bins_per_octave, tuning=0.0
    ))
    cqt_stat_hi = 0.50 * np.mean(C, axis=1) + 0.50 * np.percentile(C, 85, axis=1)
    # Converte a CQT de 36 bins/oitava em energia por semitom absoluto.
    cqt_note = np.zeros(48, dtype=float)
    for semi in range(48):
        center = semi * 3
        lo, hi = max(0, center - 1), min(len(cqt_stat_hi), center + 2)
        cqt_note[semi] = float(np.max(cqt_stat_hi[lo:hi])) if hi > lo else 0.0
    mx = float(np.max(cqt_note)) if cqt_note.size else 0.0
    cqt_norm = cqt_note / mx if mx > 1e-12 else cqt_note

    # pYIN serve como segunda evidência, especialmente em chugs/riffs monofônicos graves.
    try:
        f0, voiced, prob = librosa.pyin(
            y_h, fmin=fmin, fmax=fmax, sr=sr,
            frame_length=4096, hop_length=512,
            fill_na=np.nan
        )
        valid = np.isfinite(f0) & voiced & np.isfinite(prob) & (prob >= 0.55)
        hist = np.zeros(60, dtype=float)
        if np.any(valid):
            mids = np.rint(librosa.hz_to_midi(f0[valid])).astype(int)
            weights = np.asarray(prob[valid], dtype=float)
            for m, w in zip(mids, weights):
                if 0 <= m < hist.size:
                    hist[m] += float(w)
        hmax = float(np.max(hist)) if hist.size else 0.0
        hist = hist / hmax if hmax > 1e-12 else hist
        voiced_ratio = float(np.mean(valid))
    except Exception:
        hist = np.zeros(60, dtype=float)
        voiced_ratio = 0.0

    # Mapa combinado por nota MIDI.
    combined = np.zeros(90, dtype=float)
    pyin_weight = 0.58 if voiced_ratio >= 0.03 else 0.20
    for i, val in enumerate(cqt_norm):
        m = base_midi + i
        if 0 <= m < combined.size:
            combined[m] = (1.0 - pyin_weight) * float(val) + pyin_weight * float(hist[m] if m < hist.size else 0.0)
    cmax = float(np.max(combined)) if combined.size else 0.0
    combined = combined / cmax if cmax > 1e-12 else combined

    weights = np.array([3.0, 1.65, 1.15, 0.85, 0.65, 0.50], dtype=float)
    raw = []
    for name, strings in TUNINGS.items():
        vals = np.array([combined[m] if m < len(combined) else 0.0 for m in strings], dtype=float)
        open_score = float(np.dot(vals, weights) / np.sum(weights))
        low = strings[0]
        low_presence = float(combined[low])

        # Forte evidência recorrente abaixo da corda grave candidata é incompatível com a afinação.
        below_slice = combined[max(0, low - 5):low]
        below = float(np.max(below_slice)) if below_slice.size else 0.0
        below_penalty = max(0.0, below - 0.28) * 0.55

        # A segunda corda ajuda a distinguir família Drop de Standard.
        second_presence = float(combined[strings[1]])
        family_bonus = 0.09 * second_presence
        score = 0.56 * low_presence + 0.35 * open_score + family_bonus - below_penalty
        raw.append((score, name, low_presence, open_score, below))

    raw.sort(reverse=True)
    scores = np.array([max(0.0, r[0]) for r in raw], dtype=float)
    if scores.max() > 0:
        # Normalização relativa, sem fingir calibração probabilística.
        rel = 100.0 * scores / scores.max()
    else:
        rel = np.zeros_like(scores)

    top_score, top_name, top_low, top_open, top_below = raw[0]
    second_score = raw[1][0] if len(raw) > 1 else 0.0
    margin = max(0.0, top_score - second_score)
    evidence_strength = min(1.0, 0.65 * top_low + 0.35 * top_open)
    # Sem evidência online, mantemos teto conservador.
    local_conf = int(max(30, min(82, 38 + 35 * evidence_strength + 90 * margin)))

    candidates = []
    for i, (score, name, low_presence, open_score, below) in enumerate(raw[:6]):
        candidates.append({
            "tuning": name,
            "strings_midi": TUNINGS[name],
            "strings": [midi_name(m) for m in TUNINGS[name]],
            "local_score": round(float(rel[i]), 1),
            "raw_score": round(float(score), 5),
            "lowest_presence": round(float(low_presence), 4),
            "open_strings_score": round(float(open_score), 4),
            "below_energy": round(float(below), 4),
        })

    low_region = [(m, combined[m]) for m in range(29, 46)]
    low_region.sort(key=lambda x: x[1], reverse=True)
    top_low_notes = [
        {"note": midi_name(m), "midi": m, "strength": round(float(v), 3)}
        for m, v in low_region[:6]
    ]

    return {
        "best": top_name,
        "confidence_local": local_conf,
        "candidates": candidates,
        "top_low_notes": top_low_notes,
        "voiced_ratio": round(voiced_ratio, 4),
        "analysis_tuning_offset_bins": round(cqt_tuning, 5),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mix", required=True)
    ap.add_argument("--guitar", required=True)
    args = ap.parse_args()
    result = {
        "key": analyze_key(Path(args.mix)),
        "guitar": analyze_guitar(Path(args.guitar)),
        "engine": "librosa chroma-CQT + pYIN + absolute CQT",
    }
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
