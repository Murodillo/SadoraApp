"""
Shared matting for the pet art (tools/cut_pet_sheet.py and tools/cut_pet_video.py).

The ground is cut by flooding inward from the border, which cannot reach the white
that is enclosed by the pet itself — between the legs, between an arm and the body. Those
pockets stayed white and showed as white patches on the dark theme. [clear_holes] finds
them: an enclosed region the colour of the ground, flat (no shading), small next to the
pet, and in its lower part. The last two keep the pet's own white safe — Laylo's head is
exactly the ground's colour, but it is large and at the top.
"""
import cv2
import numpy as np

HOLE_MIN_AREA = 60        # px at the working resolution: smaller is an eye highlight
HOLE_MAX_SHARE = 0.02     # of the pet's bounding box
HOLE_TOP = 0.40           # a hole's centre must sit below this fraction of the pet's height
HOLE_FLAT = 3.5           # max colour spread inside a hole
HOLE_CLOSE = 4.0          # max mean distance from the ground's colour
HOLE_FEET = 0.80          # below this fraction of the pet's height a pale pocket is ground


def _dilate(m, n=1):
    k = np.ones((3, 3), np.uint8)
    return cv2.dilate(m.astype(np.uint8), k, iterations=n).astype(bool)


def clear_holes(a, rgb, alpha):
    """[a] the source RGB (float), [rgb]/[alpha] the cut so far; both are edited in place."""
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    ground = np.median(border, axis=0)
    dist = np.abs(a - ground).max(2)
    sat = a.max(2) - a.min(2)

    solid = alpha > 0.5
    ys, xs = np.where(solid)
    if len(ys) == 0:
        return
    top, bottom = ys.min(), ys.max()
    box_area = (bottom - top + 1) * (xs.max() - xs.min() + 1)

    pocket = (dist < 8) & (sat < 10) & solid
    n, lab, stats, cents = cv2.connectedComponentsWithStats(pocket.astype(np.uint8), connectivity=4)
    hole = np.zeros(pocket.shape, bool)

    # Between short legs the pocket fades from ground white into the floor shadow, so it
    # is neither flat nor quite the ground's colour. Down at the feet, a colourless pale
    # pocket that small is ground whatever its gradient.
    feet = np.zeros(pocket.shape, bool)
    feet[top + int(HOLE_FEET * (bottom - top)):] = True
    pale = (dist < 40) & (sat < 8) & solid & feet
    pn, plab, pst, _ = cv2.connectedComponentsWithStats(pale.astype(np.uint8), connectivity=4)
    for i in range(1, pn):
        area = pst[i, cv2.CC_STAT_AREA]
        if HOLE_MIN_AREA <= area <= HOLE_MAX_SHARE * box_area:
            hole |= plab == i
    for i in range(1, n):
        area = stats[i, cv2.CC_STAT_AREA]
        if area < HOLE_MIN_AREA or area > HOLE_MAX_SHARE * box_area:
            continue
        if cents[i][1] < top + HOLE_TOP * (bottom - top):
            continue
        m = lab == i
        if a[m].std(0).max() > HOLE_FLAT or dist[m].mean() > HOLE_CLOSE:
            continue
        hole |= m
    if not hole.any():
        return

    # The hole goes clear; its rim fades like the outer edge, with the ground unmixed.
    ring = _dilate(hole, 2) & ~hole
    alpha[hole] = 0
    d = (255 - a).max(2)
    soft = np.clip((d[ring] - 3) / 27, 0, 1)
    alpha[ring] = np.minimum(alpha[ring], soft)
    m = np.maximum(alpha[ring], 1e-3)[:, None]
    rgb[ring] = np.clip((a[ring] - 255 * (1 - m)) / m, 0, 255)


def close_specks(a, rgb, alpha, shadow=None, size=7):
    """
    Fills the pinholes a near-white pet (Laylo's feathers) gets where its own white
    matched the ground: anything the pet's mask closes over within [size] px goes back
    to opaque with its own colour. Run before [clear_holes]; real gaps between the legs
    are wider than this and stay open.
    """
    solid = (alpha > 0.5).astype(np.uint8)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (size, size))
    closed = cv2.morphologyEx(solid, cv2.MORPH_CLOSE, k).astype(bool)
    fill = closed & ~solid.astype(bool)
    if shadow is not None:
        fill &= ~shadow
    alpha[fill] = 1
    rgb[fill] = a[fill]


def drop_crumbs(alpha, near=8, max_area=60):
    """
    Drops the crumbs a frayed edge leaves: tiny opaque bits hugging the pet's outline but
    not joined to it (a white wing tip that half melted into the ground). Sparkles sit
    further out than [near] px and survive.
    """
    solid = (alpha > 0.5).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(solid, connectivity=8)
    if n <= 2:
        return
    big = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    halo = cv2.dilate((lab == big).astype(np.uint8), np.ones((3, 3), np.uint8), iterations=near).astype(bool)
    for i in range(1, n):
        if i == big or stats[i, cv2.CC_STAT_AREA] > max_area:
            continue
        m = lab == i
        if (m & halo).any():
            alpha[_dilate(m, 1)] = 0


def key_green(a, lo=25.0, hi=75.0):
    """
    Chroma key for a pet filmed on solid green (Laylo, whose white melts into a white
    ground). Greenness = G - max(R, B): the ground is ~110, the pet ~0. Between [lo] and
    [hi] the edge fades; green spill on the rim is pulled back to max(R, B). A darker
    patch of ground under the feet is the floor shadow and becomes translucent black.
    Returns (rgb, alpha, shadow).
    """
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    green = g - np.maximum(r, b)
    alpha = np.clip(1 - (green - lo) / (hi - lo), 0, 1).astype(np.float32)
    rgb = a.copy()
    spill = green > 0
    rgb[..., 1][spill] = np.maximum(r, b)[spill]
    ground = alpha < 0.05
    lum = a.mean(2)
    ground_lum = float(np.median(lum[ground])) if ground.any() else 255.0
    dark = ground & (lum < ground_lum - 8)
    h = a.shape[0]
    dark[: int(h * 0.5)] = False
    alpha[dark] = np.clip((ground_lum - lum[dark]) / ground_lum * 1.6, 0, 0.45)
    rgb[dark] = 0
    return rgb, alpha, dark
