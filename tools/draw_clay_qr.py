"""Draws the doctor app's Scan tab icon: a QR code built from clay blocks."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 1536                      # supersampled canvas; saved at 384
OUT = sys.argv[1]
N = 11                        # modules across
PAD = 150
u = (S - 2 * PAD) / N

CORAL = np.array([245, 124, 141]) / 255
VIOLET = np.array([146, 135, 219]) / 255

def blank():
    return Image.new("L", (S, S), 0)

def rr(d, x0, y0, x1, y1, r):
    d.rounded_rectangle([PAD + x0 * u, PAD + y0 * u, PAD + x1 * u, PAD + y1 * u], radius=r * u, fill=255)

# finder patterns: a thick rounded ring and a dot inside, at three corners
finders = blank(); fd = ImageDraw.Draw(finders)
rings = blank(); rd = ImageDraw.Draw(rings)
for fx, fy in [(0, 0), (N - 4, 0), (0, N - 4)]:
    rr(rd, fx, fy, fx + 4, fy + 4, 1.15)
    hole = Image.new("L", (S, S), 0); hd = ImageDraw.Draw(hole)
    rr(hd, fx + 0.95, fy + 0.95, fx + 3.05, fy + 3.05, 0.55)
    rings = Image.fromarray(np.minimum(np.array(rings), 255 - np.array(hole)).astype(np.uint8)); rd = ImageDraw.Draw(rings)
    rr(fd, fx + 1.45, fy + 1.45, fx + 2.55, fy + 2.55, 0.42)

# data modules: a fixed pattern outside the finders, neighbours fused into bars
data = [
    ".....#.....",
    "......#....",
    ".....##....",
    "...........",
    ".....#.....",
    "##.#.##.#.#",
    "......##.##",
    ".....#..#..",
    ".....##.###",
    ".....#.#...",
    "......##.##",
]
cells = blank(); cd = ImageDraw.Draw(cells)
g = 0.08
for y, row in enumerate(data):
    for x, ch in enumerate(row):
        if ch != "#":
            continue
        rr(cd, x + g, y + g, x + 1 - g, y + 1 - g, 0.32)
        if x + 1 < N and row[x + 1] == "#":
            cd.rectangle([PAD + (x + 0.5) * u, PAD + (y + g) * u, PAD + (x + 1.5) * u, PAD + (y + 1 - g) * u], fill=255)
        if y + 1 < N and data[y + 1][x] == "#":
            cd.rectangle([PAD + (x + g) * u, PAD + (y + 0.5) * u, PAD + (x + 1 - g) * u, PAD + (y + 1.5) * u], fill=255)

def clay(mask, base, soft):
    """Lights a flat mask as a puffy clay solid: height from a blur, light from the top left."""
    m = np.array(mask, dtype=np.float32) / 255
    h = np.array(mask.filter(ImageFilter.GaussianBlur(soft)), dtype=np.float32) / 255
    h = np.sqrt(np.clip(h, 0, 1))
    gy, gx = np.gradient(h * soft * 1.4)
    n = np.stack([-gx, -gy, np.ones_like(h)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    L = np.array([-0.45, -0.6, 0.66]); L /= np.linalg.norm(L)
    diff = np.clip((n * L).sum(-1), 0, 1)
    H = L + np.array([0, 0, 1]); H /= np.linalg.norm(H)
    spec = np.clip((n * H).sum(-1), 0, 1) ** 40 * 0.55
    rim = (1 - n[..., 2]) ** 2 * 0.25            # warm bounce under the edges
    col = base * (0.55 + 0.55 * diff)[..., None] + spec[..., None] + (rim[..., None] * np.array([1, 0.85, 0.9]) * (n[..., 1] > 0)[..., None])
    col = np.clip(col, 0, 1)
    return np.dstack([col, m])

layers = [clay(rings, CORAL, 34), clay(finders, VIOLET, 22), clay(cells, VIOLET, 22)]

# soft contact shadow under everything
allm = np.maximum.reduce([np.array(m) for m in (rings, finders, cells)])
sh = Image.fromarray(allm).transform((S, S), Image.AFFINE, (1, 0, -10, 0, 1, -26)).filter(ImageFilter.GaussianBlur(26))
sha = np.array(sh, dtype=np.float32) / 255 * 0.28
out = np.zeros((S, S, 4), np.float32)
out[..., :3] = np.array([0.35, 0.22, 0.32]); out[..., 3] = sha
for lay in layers:
    a = lay[..., 3:4]
    out[..., :3] = lay[..., :3] * a + out[..., :3] * out[..., 3:4] * (1 - a)
    out[..., 3:4] = a + out[..., 3:4] * (1 - a)
    out[..., :3] = np.where(out[..., 3:4] > 0, out[..., :3] / np.maximum(out[..., 3:4], 1e-6), 0)
img = Image.fromarray((out * 255).round().astype(np.uint8), "RGBA").resize((384, 384), Image.LANCZOS)
img.save(OUT)
