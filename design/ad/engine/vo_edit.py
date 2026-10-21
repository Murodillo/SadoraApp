#!/usr/bin/env python3
"""
Places the voice-over take against the picture, cutting it only at its own pauses.

    python3 design/ad/engine/vo_edit.py uz     -> build/ad/vo/uz_edit.wav + uz_anchors.json

Each block is a run of speech between two of the take's pauses, measured with
vo_align.py. The whole take is first sped up by TEMPO (pitch kept, ffmpeg atempo), then
every block is laid at its film time with a short fade at both cuts. The picture reads
its hits from the anchors file this writes, so moving a block here moves the picture.
"""
import json, os, subprocess, sys, tempfile, wave
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
VO = os.path.join(ROOT, "build", "ad", "vo")
SR = 48000

# Per language: the master take, its tempo, and its blocks as
# (name, raw start, raw end, gap before it in film seconds, {word: raw onset}).
# The first block's "gap" is its absolute film start.
TAKES = {
    "uz": {
        "take": "uz_Achernar_1.wav",
        "tempo": 1.08,
        "blocks": [
            # Word keys are the picture's beat names, the same in every language.
            ("tanangiz", 0.43, 1.89, 0.50, {"open": 0.43}),
            ("bosqich", 2.16, 3.54, 0.20, {"stages": 2.28}),
            ("harkuni", 4.03, 5.51, 0.24, {"daily": 4.03, "step": 5.20}),
            ("suv", 5.95, 6.52, 0.22, {"water": 5.95}),
            ("dori", 6.68, 7.36, 0.15, {"meds": 6.68}),
            ("kayfiyat", 7.55, 8.11, 0.17, {"mood": 7.55}),
            ("savol", 8.54, 9.44, 0.34, {"question": 8.54, "arises": 8.98}),
            ("tushuntiradi", 9.70, 10.73, 0.20, {"explains": 9.70}),
            ("jiddiy", 11.15, 13.29, 0.27, {"serious": 11.15, "doctor": 12.19}),
            ("yaqiningiz", 13.58, 15.73, 0.24, {"loved": 13.58, "guess": 14.62}),
            ("biladi", 16.14, 16.59, 0.34, {"know": 16.14}),
            ("sikl", 17.00, 17.78, 0.28, {"cycle": 17.00}),
            ("homiladorlik", 18.05, 19.05, 0.22, {"pregnancy": 18.05}),
            ("onalik", 19.27, 19.92, 0.19, {"motherhood": 19.27}),
            ("menopauza", 20.17, 20.93, 0.21, {"menopause": 20.17}),
            ("ayol", 21.43, 22.37, 0.30, {"every_woman": 21.43}),
            ("lahza", 22.91, 23.83, 0.26, {"every_moment": 22.91}),
            ("sadora", 24.52, 25.05, None, {"sadora": 24.52}),  # pinned to the reveal
        ],
    },
    "en": {
        "take": "en3_Achernar_2.wav",
        "tempo": 1.10,
        "blocks": [
            ("body", 0.29, 2.31, 0.50, {"open": 0.29}),
            ("stages", 2.51, 4.00, 0.20, {"stages": 3.30}),
            ("step", 4.39, 5.74, 0.24, {"daily": 4.39, "step": 5.00}),
            ("water", 6.18, 6.74, 0.22, {"water": 6.18}),
            ("meds", 6.97, 7.59, 0.15, {"meds": 6.97}),
            ("mood", 7.75, 8.25, 0.17, {"mood": 7.75}),
            ("question", 8.71, 9.46, 0.34, {"question": 8.71, "arises": 8.95}),
            ("explains", 9.89, 11.16, 0.20, {"explains": 9.89}),
            ("serious", 11.55, 12.60, 0.27, {"serious": 11.55}),
            ("answers", 12.89, 14.12, 0.12, {"doctor": 12.95}),
            ("love", 14.45, 16.46, 0.24, {"loved": 14.45, "guess": 15.95}),
            ("know", 16.68, 17.43, 0.34, {"know": 16.68}),
            ("cycle", 17.91, 18.55, 0.28, {"cycle": 17.91}),
            ("pregnancy", 18.82, 19.54, 0.22, {"pregnancy": 18.82}),
            ("motherhood", 19.82, 20.46, 0.19, {"motherhood": 19.82}),
            ("menopause", 20.75, 21.74, 0.21, {"menopause": 20.75}),
            ("woman", 22.09, 22.88, 0.30, {"every_woman": 22.09}),
            ("moment", 23.25, 24.04, 0.26, {"every_moment": 23.25}),
            ("sadora", 24.49, 25.05, None, {"sadora": 24.49}),
        ],
    },
}

# The hero: a breath after the last line, then the companions land and Brand.kt's reveal
# starts (t0). "Sadora." is spoken as the wordmark's letters begin to rise, t0 + 1.2 s.
BREATH = 0.24
LETTERS_AT = 1.2

PRE, POST, FADE = 0.035, 0.09, 0.012   # handles around each block, in raw seconds


def read(path):
    with wave.open(path) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
        if w.getnchannels() == 2:
            x = x.reshape(-1, 2).mean(1)
        return x, w.getframerate()


def write(path, x, sr=SR):
    y = np.clip(x * 32767, -32768, 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(y.tobytes())


def stretched(src, tempo):
    """The whole take at 48 kHz and `tempo`, pitch unchanged."""
    out = tempfile.mktemp(suffix=".wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-af",
                    f"aresample={SR}:filter_size=64:cutoff=0.97,atempo={tempo}", "-ac", "1", out], check=True)
    x, _ = read(out)
    os.remove(out)
    return x


def edit(lang, length=25.2, sadora_at=None):
    cfg = TAKES[lang]
    r = cfg["tempo"]
    x = stretched(os.path.join(VO, cfg["take"]), r)
    film = np.zeros(int(length * SR), np.float32)
    anchors, blocks = {}, []
    t = 0.0
    for i, (name, a, b, gap, words) in enumerate(cfg["blocks"]):
        if gap is None:
            anchors["hero"] = round(t + BREATH, 3)
            start = sadora_at if sadora_at is not None else t + BREATH + LETTERS_AT
        else:
            start = gap if i == 0 else t + gap
        dur = (b - a) / r
        # Cut out the block with handles, fade the cut points, lay it at `start`.
        s0 = int(max(0, (a - PRE) / r) * SR)
        s1 = int(min(len(x) / SR, (b + POST) / r) * SR)
        seg = x[s0:s1].copy()
        f = int(FADE * SR)
        seg[:f] *= np.linspace(0, 1, f)
        seg[-f:] *= np.linspace(1, 0, f)
        at = int((start - PRE / r) * SR)
        film[at:at + len(seg)] += seg[: max(0, len(film) - at)]
        for w, onset in words.items():
            anchors[w] = round(start + (onset - a) / r, 3)
        blocks.append({"name": name, "start": round(start, 3), "end": round(start + dur, 3)})
        t = start + dur
    out = os.path.join(VO, f"{lang}_edit.wav")
    write(out, film)
    json.dump({"tempo": r, "blocks": blocks, "words": anchors},
              open(os.path.join(VO, f"{lang}_anchors.json"), "w"), indent=1, ensure_ascii=False)
    return blocks, anchors


if __name__ == "__main__":
    lang = sys.argv[1] if len(sys.argv) > 1 else "uz"
    at = float(sys.argv[2]) if len(sys.argv) > 2 else None
    blocks, anchors = edit(lang, sadora_at=at)
    for b in blocks:
        print(f"{b['start']:6.2f} – {b['end']:6.2f}  {b['name']}")
    print(json.dumps(anchors, ensure_ascii=False))
