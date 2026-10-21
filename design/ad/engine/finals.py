#!/usr/bin/env python3
"""
Builds the deliverables: picture for every language × format, muxed with that language's mix.

    build/ad/.venv/bin/python design/ad/engine/finals.py            # all four
    build/ad/.venv/bin/python design/ad/engine/finals.py uz 169     # one

Writes build/ad/out/Sadora_ThePath_<lang>_<16x9|9x16>.mp4 (H.264 1080p60 + AAC 48 kHz).
The mix comes from audio/build.py (build/ad/audio/<lang>_mix.wav); without it the voice
edit alone is used so the picture can still be reviewed in sync.
"""
import os, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render
from core import BUILD

NAMES = {"169": "16x9", "916": "9x16"}


def final(lang, fmt):
    pic = os.path.join(BUILD, "out", f"{lang}_{fmt}_picture.mp4")
    render.cmd_video(lang, fmt, preview=False, workers=10, mblur=3, out=pic)
    mix = os.path.join(BUILD, "audio", f"{lang}_mix.wav")
    if not os.path.exists(mix):
        mix = os.path.join(BUILD, "vo", f"{lang}_edit.wav")
        print("  (no music mix yet — using the voice edit)")
    out = os.path.join(BUILD, "out", f"Sadora_ThePath_{lang}_{NAMES[fmt]}.mp4")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", pic, "-i", mix, "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "320k", "-ar", "48000", "-shortest",
                    "-movflags", "+faststart", out], check=True)
    print(out)
    return out


if __name__ == "__main__":
    langs = [sys.argv[1]] if len(sys.argv) > 1 else ["uz", "en"]
    fmts = [sys.argv[2]] if len(sys.argv) > 2 else ["169", "916"]
    for l in langs:
        for f in fmts:
            final(l, f)
