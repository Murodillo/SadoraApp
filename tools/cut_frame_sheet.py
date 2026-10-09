#!/usr/bin/env python3
"""
Cuts a 2x2 Gemini sheet of avatar frames into transparent, centred PNGs.

A frame is a ring on white with white inside it too, so the background is flooded from
the cell's border *and* from its middle. Each cut is then scaled and centred on the hole,
not on its bounding box: the app draws the face at InnerFraction of the avatar box and the
frame image at OVER times the box, so the hole has to land at the same place in every
frame whatever ornaments stick out on one side (Humo's wings, the laurel's bow).

usage:
    python3 tools/cut_frame_sheet.py sheet.png name1 name2 name3 name4   ('-' skips a cell)

Sheets are kept in design/frames/. Output: frame_<name>.webp in composeResources/drawable.
"""
import os
import sys

import numpy as np
from PIL import Image

SIZE = int(os.environ.get("SIZE", 512))
# The image covers OVER x the avatar box; ornaments may reach past the box, the ring does not.
OVER = 1.3
# The hole's radius as a share of the avatar box: a hair under the face's 0.41, so the
# ring's inner edge covers the face's rim and no seam shows.
HOLE = 0.40
BG, LO, HI, BAND = 14, 3, 30, 2
INSET = 6
OUT = os.path.join(os.path.dirname(__file__), "..", "sadora-client/shared/src/commonMain/composeResources/drawable")


def dilate(m):
    o = m.copy()
    o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]
    return o


def flood(light, seed):
    reach = seed & light
    while True:
        nxt = dilate(reach) & light
        if (nxt == reach).all():
            return reach
        reach = nxt


def cut(cell):
    a = np.asarray(cell.convert("RGB")).astype(np.float32)
    d = (255 - a).max(axis=2)
    light = d < BG
    h, w = light.shape
    border = np.zeros_like(light)
    border[0, :] = border[-1, :] = True
    border[:, 0] = border[:, -1] = True
    outside = flood(light, border)
    middle = np.zeros_like(light)
    middle[h // 2 - 4:h // 2 + 4, w // 2 - 4:w // 2 + 4] = True
    hole = flood(light, middle)
    if hole.sum() < 0.05 * h * w:
        raise SystemExit("no hole found in the middle of the cell")
    reach = outside | hole

    band = reach.copy()
    for _ in range(BAND):
        band = dilate(band)
    edge = band & ~reach
    alpha = np.ones(d.shape, np.float32)
    alpha[reach] = 0
    alpha[edge] = np.clip((d[edge] - LO) / (HI - LO), 0, 1)
    am = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip(np.where(edge[..., None], (a - 255 * (1 - am)) / am, a), 0, 255)
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)

    ys, xs = np.where(hole)
    cy, cx = ys.mean(), xs.mean()
    radius = np.sqrt(hole.sum() / np.pi)
    # Pixels per avatar box, then the canvas that covers OVER boxes around the hole.
    box = radius / HOLE
    side = int(round(box * OVER))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    img = Image.fromarray(rgba, "RGBA")
    canvas.paste(img, (int(round(side / 2 - cx)), int(round(side / 2 - cy))), img)
    fg = np.asarray(canvas)[..., 3] > 40
    fy, fx = np.where(fg)
    reach_out = np.sqrt(((fy - side / 2) ** 2 + (fx - side / 2) ** 2).max()) / box
    print(f"  hole r={radius:.0f}px, reaches {reach_out:.2f} of the box (canvas {OVER / 2:.2f})")
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


sheet = Image.open(sys.argv[1])
names = sys.argv[2:]
W, H = sheet.size
cw, ch = W // 2, H // 2
for i, name in enumerate(names):
    if name == "-":
        continue
    r, c = divmod(i, 2)
    cell = sheet.crop((c * cw + INSET, r * ch + INSET, (c + 1) * cw - INSET, (r + 1) * ch - INSET))
    print(name)
    cut(cell).save(os.path.join(OUT, f"frame_{name}.webp"), "WEBP", quality=90, method=6)
