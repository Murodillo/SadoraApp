#!/usr/bin/env python3
"""
Cuts a 3x3 Gemini icon sheet into transparent square PNGs.

The colour icons (`ic3d_*`) are made in the Gemini web app, nine per image so the set
keeps one style: the API key in `.env` is free-tier and gets no image quota. The four
source sheets are in `design/icons3d/`; ask Gemini for "another 3x3 sheet, identical
style, pure white background, no labels" in the same chat, press its Copy image button,
and save the clipboard with
    osascript -e 'set f to open for access POSIX file "/abs/sheet5.png" with write permission' \
              -e 'write (the clipboard as «class PNGf») to f' -e 'close access f'

usage:
    python3 tools/cut_icon_sheet.py sheet.png out_dir name1 ... name9   ('-' skips a cell)
    LABELS=1 ...   drops a text label Gemini put under each icon (sheet1 has them)
    SIZE=480 ...   a larger cut, for an illustration rather than an icon (art_shield)
    GRID=2 ...     a 2x2 sheet: four bigger icons, for art drawn large (the badge medals)

Output goes to sadora-client/shared/src/commonMain/composeResources/drawable/.
"""
import sys, os
import numpy as np
from PIL import Image

SIZE = int(os.environ.get("SIZE", 192))  # 64dp at xxhdpi
INSET = 10          # drops any grid line at the cell edges
BG = int(os.environ.get("BG", 12))  # whiteness distance still counted as background
LO, HI = 3, int(os.environ.get("HI", 30))
BAND = int(os.environ.get("BAND", 2))

def dilate(m):
    o = m.copy()
    o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]
    return o

def cut(cell):
    a = np.asarray(cell.convert("RGB")).astype(np.float32)
    d = (255 - a).max(axis=2)
    light = d < BG
    reach = np.zeros_like(light)
    reach[0, :] = light[0, :]; reach[-1, :] = light[-1, :]
    reach[:, 0] = light[:, 0]; reach[:, -1] = light[:, -1]
    while True:
        nxt = dilate(reach) & light
        if (nxt == reach).all(): break
        reach = nxt
    band = reach.copy()
    for _ in range(BAND): band = dilate(band)
    edge = band & ~reach
    alpha = np.ones(d.shape, np.float32)
    alpha[reach] = 0
    alpha[edge] = np.clip((d[edge] - LO) / (HI - LO), 0, 1)
    # Un-mix the white background out of the soft edge.
    am = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.where(edge[..., None], (a - 255 * (1 - am)) / am, a)
    rgb = np.clip(rgb, 0, 255)
    fg = alpha > 0.15
    # Keep only the tallest run of rows: drops a text label under the icon.
    rows = fg.any(axis=1)
    runs, start = [], None
    for y, r in enumerate(list(rows) + [False]):
        if r and start is None: start = y
        if not r and start is not None: runs.append((start, y)); start = None
    merged = []
    for s, e in runs:
        if merged and s - merged[-1][1] < 8: merged[-1] = (merged[-1][0], e)
        else: merged.append((s, e))
    if os.environ.get("LABELS"):
        s, e = max(merged, key=lambda r: r[1] - r[0])
        alpha[:s] = 0; alpha[e:] = 0
    fg = alpha > 0.15
    ys, xs = np.where(fg)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    out = np.dstack([rgb, alpha * 255]).astype(np.uint8)[y0:y1, x0:x1]
    img = Image.fromarray(out, "RGBA")
    side = int(max(img.size) * 1.08)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)

sheet = Image.open(sys.argv[1]); out = sys.argv[2]; names = sys.argv[3:]
GRID = int(os.environ.get("GRID", 3))
W, H = sheet.size; cw, ch = W // GRID, H // GRID
for i, name in enumerate(names):
    if name == "-": continue
    r, c = divmod(i, GRID)
    cell = sheet.crop((c * cw + INSET, r * ch + INSET, (c + 1) * cw - INSET, (r + 1) * ch - INSET))
    cut(cell).save(os.path.join(out, f"{name}.png"), optimize=True)
    print(name)
