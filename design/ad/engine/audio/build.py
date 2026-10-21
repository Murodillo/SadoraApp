#!/usr/bin/env python3
"""
Builds the whole ad soundtrack from code, per language, locked to timeline.beats(lang):

    build/ad/.venv/bin/python design/ad/engine/audio/build.py uz en

Writes to build/ad/audio/: {lang}_music.wav, {lang}_sfx.wav, {lang}_mix.wav (48 kHz stereo
24-bit), {lang}_cues.json, {lang}_spectrogram.png, plus chime.wav and sonic_logo.wav.
Every run re-reads the timeline and the VO edit and overwrites the outputs.
"""
import json
import os
import re
import subprocess
import sys
import time

sys.dont_write_bytecode = True  # leave no __pycache__ in the engine folders
HERE = os.path.dirname(os.path.abspath(__file__))
ENGINE = os.path.dirname(HERE)
sys.path[:0] = [HERE, ENGINE]

import numpy as np  # noqa: E402

import timeline  # noqa: E402
import chime  # noqa: E402
import mix  # noqa: E402
import music  # noqa: E402
import sfx  # noqa: E402
from synth import SR, hp, true_peak_db, windowed_lufs, write_wav  # noqa: E402

ROOT = timeline.ROOT
OUT = os.path.join(ROOT, "build", "ad", "audio")
VO = timeline.VO
FFMPEG = "/opt/homebrew/bin/ffmpeg"


def beat_value(B, ref):
    m = re.fullmatch(r"(\w+)(?:\[(\d+)\])?", ref)
    v = B[m.group(1)]
    return float(v[int(m.group(2))] if m.group(2) is not None else v)


def ffmpeg_loudness(path):
    p = subprocess.run([FFMPEG, "-hide_banner", "-nostats", "-i", path, "-af", "ebur128=peak=true",
                        "-f", "null", "-"], capture_output=True, text=True)
    txt = p.stderr[p.stderr.rfind("Summary:"):]
    i = float(re.search(r"I:\s+(-?[\d.]+) LUFS", txt).group(1))
    tp = float(re.search(r"Peak:\s+(-?[\d.]+) dBFS", txt).group(1))
    return i, tp


def hf_level(x, f=12000):
    """Loudest 2 ms of content above `f` (dBFS): a click detector for band-limited stems."""
    y = hp(x.mean(1) if x.ndim == 2 else x, f, 4)
    k = int(0.002 * SR)
    m = len(y) // k
    r = np.sqrt((y[:m * k].reshape(m, k) ** 2).mean(1))
    j = int(np.argmax(r))
    return 20 * np.log10(r[j] + 1e-12), j * k / SR


def spectrogram(path, x, stems, B, lang):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    y = x.mean(1)
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(18, 9), sharex=True, gridspec_kw=dict(height_ratios=[3, 1.4]))
    a1.specgram(y, NFFT=2048, Fs=SR, noverlap=1536, cmap="magma", vmin=-130, vmax=-20, scale="dB")
    a1.set_ylim(0, 14000)
    a1.axhline(8000, color="cyan", lw=0.8, ls="--")
    a1.set_ylabel("Hz")
    a1.set_title(f"Sadora ad - {lang} mix (cyan dashed = 8 kHz)")
    for name, col in (("vo", "white"), ("music", "tab:orange"), ("sfx", "tab:cyan")):
        tc, L = windowed_lufs(stems[name])
        a2.plot(tc, np.maximum(L, -70), color=col if name != "vo" else "k", lw=1, label=name)
    tc, L = windowed_lufs(x)
    a2.plot(tc, np.maximum(L, -70), color="tab:red", lw=0.6, alpha=0.6, label="mix")
    a2.set_ylim(-70, -5)
    a2.set_ylabel("LUFS (400 ms)")
    a2.legend(loc="lower left", ncol=4, fontsize=8)
    marks = {"slam": B["slam"], "drop": B["drop"], "pullback": B["pullback"][0], "breath": B["breath"][0],
             "t0": B["t0"], "sonic": B["sonic"]}
    for k, v in marks.items():
        for a in (a1, a2):
            a.axvline(v, color="lime", lw=0.7, alpha=0.8)
        a1.text(v + 0.03, 13300, k, color="lime", fontsize=8)
    a2.set_xlabel("s")
    a2.set_xlim(0, timeline.LENGTH)
    fig.tight_layout()
    fig.savefig(path, dpi=80)
    plt.close(fig)


def build_lang(lang):
    t_start = time.time()
    B = timeline.beats(lang)
    blocks = timeline.anchors(lang)["blocks"]
    from synth import read_wav
    vo_raw = read_wav(os.path.join(VO, f"{lang}_edit.wav"))
    n = int(round(timeline.LENGTH * SR))
    vo_raw = np.pad(vo_raw, (0, max(0, n - len(vo_raw))))[:n]

    M = music.render(B, timeline.LENGTH)
    S = sfx.render(B, timeline.LENGTH)
    out, stems, info = mix.run(vo_raw, M, S, blocks, B["breath"])

    for name, x in (("music", stems["music"]), ("sfx", stems["sfx"]), ("mix", out)):
        write_wav(os.path.join(OUT, f"{lang}_{name}.wav"), x)

    # ---- cues
    cues = []
    for c in S["cues"]:
        bv = beat_value(B, c["beat"])
        cues.append(dict(c, beat_time=round(bv, 4), offset=round(c["t"] - bv, 4)))
    with open(os.path.join(OUT, f"{lang}_cues.json"), "w") as f:
        json.dump(dict(lang=lang, length=timeline.LENGTH, fps=timeline.FPS, count=len(cues), cues=cues), f, indent=1)

    print(f"\n=== {lang}: {len(cues)} SFX cues ===")
    print(f"{'t':>7s} {'frame':>5s}  {'name':24s} {'beat':16s} {'beat_t':>7s} {'offset':>7s} {'gain':>6s} {'pan':>5s} hero")
    for c in cues:
        print(f"{c['t']:7.3f} {timeline.frame(c['t']):5d}  {c['name']:24s} {c['beat']:16s} {c['beat_time']:7.3f} "
              f"{c['offset']:+7.3f} {c['gain_db']:6.1f} {c['pan']:+5.2f} {'*' if c['hero'] else ''}"
              f"{'  (tail fades with the film)' if c['tail_faded_at_end'] else ''}")
    inside = all(0.0 <= c["start"] and c["end"] <= timeline.LENGTH and 0.0 <= c["t"] <= timeline.LENGTH for c in cues)
    print(f"all {len(cues)} cues inside 0-{timeline.LENGTH} s: {inside}  (* = hero, never ducked)")

    # ---- voice / music gap per block
    rows = mix.worst_gaps(stems["vo"], stems["music"], blocks)
    print(f"\nvoice vs music, 400 ms K-weighted windows centred >= {mix.EDGE * 1000:.0f} ms inside each VO block, "
          f"speech-gated (voice within 10 LU of its integrated level); need >= {mix.GAP_MIN} dB")
    for r in rows:
        print(f"  {r['block']:14s} {r['start']:6.2f}-{r['end']:6.2f}  worst {r['worst_gap']:5.1f} dB @ {r['at']:6.2f}"
              f"  (voice {r['voice']:6.1f}, music {r['music']:6.1f}, {r['windows']} windows)")
    worst = min(rows, key=lambda r: r["worst_gap"])
    print(f"  WORST: {worst['worst_gap']:.2f} dB in '{worst['block']}' @ {worst['at']:.2f} s")
    ung = min(mix.worst_gaps(stems["vo"], stems["music"], blocks, gated=False), key=lambda r: r["worst_gap"])
    print(f"  (ungated, incl. half-silent edge windows: {ung['worst_gap']:.2f} dB in '{ung['block']}' @ {ung['at']:.2f} s,"
          f" voice there {ung['voice']:.1f} LUFS)")

    # ---- hero cues: are they on top?
    print("\nhero cues, loudest 400 ms window within 0.5 s of the cue (LUFS): sfx / music / voice")
    tc, ls = windowed_lufs(stems["sfx"])
    _, lm = windowed_lufs(stems["music"])
    _, lv = windowed_lufs(stems["vo"])
    seen = set()
    for c in cues:
        key = re.sub(r"_\d+$", "", c["name"]).replace("slam_shockwave", "slam_thoom")
        if not c["hero"] or key in seen:
            continue
        seen.add(key)
        s = (tc >= c["t"]) & (tc <= c["t"] + 0.5)
        j = np.argmax(ls[s])
        print(f"  {key:12s} {c['t']:6.2f}  sfx {ls[s][j]:6.1f}  music {lm[s][j]:6.1f}  voice {max(lv[s][j], -70):6.1f}")

    # ---- checks
    print("\nchecks")
    i_ff, tp_ff = ffmpeg_loudness(os.path.join(OUT, f"{lang}_mix.wav"))
    print(f"  mix integrated {info['lufs']:.2f} LUFS, true peak {info['tp']:.2f} dBTP (own meter)")
    print(f"  ffmpeg ebur128: I = {i_ff:.1f} LUFS, true peak = {tp_ff:.1f} dBFS")
    print(f"  master gain {info['master_gain_db']:+.2f} dB, limiter max GR {info['limiter_max_gr_db']:.2f} dB "
          f"@ {info['limiter_max_gr_at']:.2f} s, "
          f"VO comp mean GR {info['vo_comp_gr_mean']:.1f} dB")
    print(f"  music duck: base {info['duck_speech_max']:.1f} dB, guarantee extra max {info['corr_max']:.1f} dB "
          f"(mean {info['corr_mean_in_speech']:.1f} dB while speaking)")
    for name, x in (("vo", stems["vo"]), ("music", stems["music"]), ("sfx", stems["sfx"]), ("mix", out)):
        dc = np.abs(x.mean(0)).max()
        pk = 20 * np.log10(np.abs(x).max() + 1e-12)
        hf, hft = hf_level(x)
        print(f"  {name:5s} peak {pk:6.1f} dBFS  DC {dc:.1e}  >12 kHz max {hf:6.1f} dBFS @ {hft:5.2f}s")
    br0, t0 = B["breath"]
    a, b = int((br0 + 0.01) * SR), int((t0 - 0.03) * SR)
    for name in ("vo", "music", "sfx"):
        r = np.sqrt(np.mean(stems[name][a:b] ** 2)) if b > a else 0
        print(f"  breath {br0:.2f}-{t0:.2f}: {name:5s} rms {20 * np.log10(r + 1e-12):6.1f} dBFS")
    m = stems["music"]
    corr = np.sum(m[:, 0] * m[:, 1]) / (np.sqrt(np.sum(m[:, 0] ** 2) * np.sum(m[:, 1] ** 2)) + 1e-12)
    mono = (m[:, 0] + m[:, 1]) / 2
    drop_db = 10 * np.log10((np.mean(mono ** 2) + 1e-20) / (np.mean((m ** 2).mean(1)) + 1e-20))
    print(f"  music L/R correlation {corr:+.2f}, mono fold-down {drop_db:+.1f} dB")
    tc, L = windowed_lufs(stems["music"])
    for lab, x0, x1 in (("intro", 0.5, B["drop"]), ("drop", B["drop"], B["pullback"][0]),
                        ("lift", B["stage_hits"][0], B["breath"][0]), ("end", B["t0"], B["fade_out"][0])):
        s = (tc >= x0) & (tc < x1)
        print(f"  music {lab:5s} {x0:5.2f}-{x1:5.2f} mean {10 * np.log10(np.mean(10 ** (L[s] / 10))):6.1f} LUFS (ducked)")

    spectrogram(os.path.join(OUT, f"{lang}_spectrogram.png"), out, stems, B, lang)
    print(f"  built {lang} in {time.time() - t_start:.1f} s")
    return dict(lang=lang, lufs=i_ff, tp=tp_ff, worst_gap=worst["worst_gap"], cues=len(cues))


def main(langs):
    os.makedirs(OUT, exist_ok=True)
    c = chime.chime()
    pre = np.zeros((int(0.02 * SR), 2))
    write_wav(os.path.join(OUT, "chime.wav"), np.vstack([pre, c]) * 10 ** (-3 / 20))
    s = chime.sonic_logo()
    write_wav(os.path.join(OUT, "sonic_logo.wav"), np.vstack([pre, s]) * 10 ** (-3 / 20))
    res = [build_lang(lang) for lang in langs]
    print("\nsummary")
    for r in res:
        print(f"  {r['lang']}: {r['lufs']:.1f} LUFS, TP {r['tp']:.1f} dBTP, worst voice-music gap "
              f"{r['worst_gap']:.1f} dB, {r['cues']} cues")


if __name__ == "__main__":
    main(sys.argv[1:] or ["uz", "en"])
