#!/usr/bin/env python3
"""
Finds a take's own pauses and the speech islands between them.

    python3 design/ad/engine/vo_align.py build/ad/vo/uz_Achernar_1.wav

Prints every island (start–end, seconds). The edit that places the take against the
picture lives in vo_edit.py; this only measures.
"""
import sys, wave
import numpy as np


def load(path):
    with wave.open(path) as w:
        sr = w.getframerate()
        x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
        if w.getnchannels() == 2:
            x = x.reshape(-1, 2).mean(1)
    return x, sr


def islands(x, sr, floor_db=36, min_gap=0.11, hop_s=0.005):
    hop = int(sr * hop_s)
    n = len(x) // hop
    e = np.sqrt((x[: n * hop].reshape(n, hop) ** 2).mean(1) + 1e-12)
    db = 20 * np.log10(e)
    on = db > db.max() - floor_db
    segs, s = [], None
    for i, v in enumerate(on):
        if v and s is None:
            s = i
        elif not v and s is not None:
            segs.append([s, i]); s = None
    if s is not None:
        segs.append([s, n])
    merged = []
    for a, b in segs:
        if merged and (a - merged[-1][1]) * hop_s < min_gap:
            merged[-1][1] = b
        else:
            merged.append([a, b])
    return [(a * hop_s, b * hop_s) for a, b in merged if (b - a) * hop_s > 0.04]


if __name__ == "__main__":
    x, sr = load(sys.argv[1])
    gap = float(sys.argv[2]) if len(sys.argv) > 2 else 0.11
    for i, (a, b) in enumerate(islands(x, sr, min_gap=gap)):
        print(f"{i:2d}  {a:6.2f} – {b:6.2f}   {b - a:4.2f}s")
