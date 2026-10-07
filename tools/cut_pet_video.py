#!/usr/bin/env python3
"""
Turns a Gemini (Veo) pet video on a white ground into transparent animation frames.

    python3 tools/cut_pet_video.py design/pets/anim/nilufar_wave.mp4 OUT_DIR [--fps 12] [--from 0] [--to 48] [--size 240]

The frames are cut like tools/cut_pet_sheet.py cuts the stills: the white reachable from
the border becomes transparent, the grey floor shadow becomes translucent black so it
reads on the dark theme too, and every frame is cropped to one shared box so the pet does
not jitter. [--from, --to] is the loop, inclusive, in output-fps frames; pick it with the
frame closest to the first (the script prints the candidates when --to is omitted).
Writes OUT_DIR/000.webp, 001.webp, ... ready for composeResources/files/.
"""
import argparse, glob, os, subprocess, tempfile
import cv2
import numpy as np
from PIL import Image

BG = 12   # whiteness distance still counted as background (--bg lowers it for a white pet)
SH = 70   # how dark a neutral pixel may be and still count as floor shadow


def dil(m):
    o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]
    return o


def flood(seed, allow):
    r = seed & allow
    while True:
        n = dil(r) & allow
        if (n == r).all():
            return r
        r = n


def cut(img):
    a = np.asarray(img.convert("RGB")).astype(np.float32)
    d = (255 - a).max(2); sat = a.max(2) - a.min(2)
    light = d < BG
    seed = np.zeros_like(light); seed[0] = seed[-1] = True; seed[:, 0] = seed[:, -1] = True
    reach = flood(seed, light)
    low = np.zeros_like(light); low[int(a.shape[0] * .5):] = True
    shadow = flood(reach, reach | ((sat <= 10) & (d < SH) & low)) & ~reach
    edge = dil(dil(reach | shadow)) & ~(reach | shadow)
    alpha = np.ones(d.shape, np.float32); rgb = a.copy()
    alpha[reach] = 0
    alpha[shadow] = np.clip(d[shadow] / 255 * 1.6, 0, .45); rgb[shadow] = 0
    alpha[edge] = np.clip((d[edge] - 3) / 27, 0, 1)
    m = np.maximum(alpha[edge], 1e-3)[:, None]
    rgb[edge] = np.clip((a[edge] - 255 * (1 - m)) / m, 0, 255)
    return np.dstack([rgb, alpha * 255]).astype(np.uint8)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("video"); p.add_argument("out")
    p.add_argument("--fps", type=int, default=12)
    p.add_argument("--from", dest="start", type=int, default=0)
    p.add_argument("--to", dest="end", type=int)
    p.add_argument("--size", type=int, default=240)
    p.add_argument("--quality", type=int, default=88)
    p.add_argument("--bg", type=int, default=12)
    # Forward then back: for a clip with no frame close enough to its first to loop on.
    p.add_argument("--pingpong", action="store_true")
    args = p.parse_args()
    global BG
    BG = args.bg

    tmp = tempfile.mkdtemp()
    subprocess.run(["ffmpeg", "-v", "error", "-i", args.video, "-vf", f"fps={args.fps}", f"{tmp}/f%03d.png"], check=True)
    files = sorted(glob.glob(f"{tmp}/*.png"))

    if args.end is None:
        small = [np.asarray(Image.open(f).convert("L").resize((160, 90))).astype(np.float32) for f in files]
        ref = small[args.start]
        best = sorted(range(args.start + 2 * args.fps, len(files)), key=lambda i: np.mean((small[i] - ref) ** 2))[:6]
        print("closest to the first frame (pick one as --to, minus one):", best)
        return

    frames = [cut(Image.open(f)) for f in files[args.start:args.end + 1]]
    if args.pingpong:
        frames = frames + frames[-2:0:-1]
    ys, xs = [], []
    for f in frames:
        # Rows and columns with a real run of opaque pixels: a stray speck of video noise
        # in a corner must not stretch the shared box to the whole frame.
        # The pet is the biggest opaque blob; sparkles around a happy jump are small
        # ones, and they must not widen the box and shrink the pet.
        m = (f[..., 3] > 40).astype(np.uint8)
        n, _, stats, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
        big = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        x, y, w, h = stats[big, :4]
        ys += [y, y + h - 1]; xs += [x, x + w - 1]
    y0, y1, x0, x1 = min(ys), max(ys) + 1, min(xs), max(xs) + 1
    side = int(max(y1 - y0, x1 - x0) * 1.06)
    os.makedirs(args.out, exist_ok=True)
    for old in glob.glob(f"{args.out}/*.webp"):
        os.remove(old)
    for n, f in enumerate(frames):
        im = Image.fromarray(f[y0:y1, x0:x1], "RGBA")
        cv = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        cv.paste(im, ((side - im.width) // 2, side - im.height - int(side * .03)))
        cv.resize((args.size, args.size), Image.LANCZOS).save(f"{args.out}/{n:03d}.webp", "WEBP", quality=args.quality, method=6)
    print(len(frames), "frames ->", args.out)


if __name__ == "__main__":
    main()
