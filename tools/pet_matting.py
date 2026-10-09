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


def clear_holes(a, rgb, alpha, white_pet=False):
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
    # A strip of the ground itself trapped under a tail, between a paw and the floor
    # shadow: exactly the ground's white, so it cannot be a cream paw — but it touches the
    # paws, which made the pale pocket above too big to count. Not for a white pet.
    if not white_pet:
        exact = (dist < 10) & (sat < 7) & solid & feet
        en, elab, est, _ = cv2.connectedComponentsWithStats(exact.astype(np.uint8), connectivity=4)
        for i in range(1, en):
            if HOLE_MIN_AREA // 3 <= est[i, cv2.CC_STAT_AREA] <= HOLE_MAX_SHARE * box_area:
                hole |= elab == i
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


def key_magenta(a, lo=35.0, hi=110.0):
    """
    Chroma key for a pet filmed on solid magenta (Humo, whose wings throw a blue and
    turquoise glow a green key would eat). Magentaness = min(R, B) - G: the ground is
    ~250, gold and cyan are below zero, so the glow and its particles stay. The soft band
    is wider than green's because the glow fades into the ground over many pixels; magenta
    spill on the rim is taken off R and B. The floor shadow is handled as for green.
    Returns (rgb, alpha, shadow).
    """
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    magenta = np.minimum(r, b) - g
    alpha = np.clip(1 - (magenta - lo) / (hi - lo), 0, 1).astype(np.float32)
    rgb = a.copy()
    spill = np.clip(magenta, 0, None)
    rgb[..., 0] = np.clip(r - spill, 0, 255)
    rgb[..., 2] = np.clip(b - spill, 0, 255)
    # Magenta seen through the translucent glowing tail stays pink after the despill;
    # pushed toward blue it reads as the glow it is. Cheeks (green above blue) are not hit.
    nr, ng, nb = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    purple = (nr > ng + 12) & (nb > ng + 12) & (nb >= nr * 0.85)
    rgb[..., 0][purple] = ng[purple] * 0.85
    ground = alpha < 0.05
    lum = a.mean(2)
    ground_lum = float(np.median(lum[ground])) if ground.any() else 255.0
    dark = ground & (lum < ground_lum - 10)
    h = a.shape[0]
    dark[: int(h * 0.5)] = False
    alpha[dark] = np.clip((ground_lum - lum[dark]) / ground_lum * 1.6, 0, 0.45)
    rgb[dark] = 0
    return rgb, alpha, dark


def blue_ambient(rgb, alpha, mask, scale=1.0):
    """
    Humo's ambient light, made reliable: Veo draws its sparkles white, which on a light
    page read as grey bubbles. Everything keyed in that the model says is not the bird
    becomes a blue glow particle (brightness kept as alpha), and the turquoise of the wing
    feathers throws a soft blue halo outside the body. [scale] is the frame height / 720.
    """
    blue = np.array([96, 178, 255], np.float32)
    c = rgb.astype(np.float32)
    lum = c.mean(2)
    bird = cv2.GaussianBlur(mask, (0, 0), 2) > 0.3
    # Only small separate specks are sparkles: a foot or a tail tip the model missed is a
    # big piece, and keeps its colour.
    # Pieces are cut on the keyed alpha alone, so a sparkle hugging a wing (inside the
    # model's blurred bird) still counts; the body is the largest piece and never one.
    solid = alpha > 0.05
    n, labels, stats, _ = cv2.connectedComponentsWithStats(solid.astype(np.uint8), connectivity=8)
    limit = max(60 * scale * scale, 0.002 * bird.sum())
    small = np.zeros(n, bool)
    if n > 1:
        area = stats[:, cv2.CC_STAT_AREA]
        sat = c.max(2) - c.min(2)
        # A white sparkle is colourless; a foot or tail tip the key split off is not.
        pale = np.bincount(labels.ravel(), weights=sat.ravel(), minlength=n) / np.maximum(area, 1) < 50
        small[1:] = (area[1:] < limit) | (pale[1:] & (area[1:] < 0.03 * bird.sum()))
        small[int(np.argmax(np.where(np.arange(n) == 0, -1, area)))] = False
    faint = (~bird) & (alpha > 0.02) & ~solid
    loose = (small[labels] & solid) | faint
    rgb[loose] = blue * (0.75 + 0.25 * (lum[loose] / 255.0))[:, None]
    alpha[loose] = alpha[loose] * np.clip(lum[loose] / 180.0, 0.25, 1.0)
    r, g, b = c[..., 0], c[..., 1], c[..., 2]
    feathers = bird & (alpha > 0.5) & (b - r > 30) & (g - r > 10)
    glow = cv2.GaussianBlur(feathers.astype(np.float32), (0, 0), 7 * scale) * 0.85
    glow = np.clip(glow, 0, 0.55)
    under = glow * (1 - alpha)
    out = alpha + under
    nz = out > 1e-4
    rgb[nz] = (rgb[nz].astype(np.float32) * alpha[nz][:, None] + blue * under[nz][:, None]) / out[nz][:, None]
    alpha[:] = out


# ---------------------------------------------------------------- model-guided pockets

_SESSIONS = {}


def model_mask(a, model=None):
    """
    Foreground probability (0..1, frame-sized) from a salient-object model in ~/.u2net
    (rembg's u2netp ONNX file, 4.7 MB). The model knows
    the pet's shape, so a pocket of ground between an arm and a cheek reads background
    while the same white in the teeth or an eye highlight reads pet.
    """
    import os
    import onnxruntime as ort
    from PIL import Image
    home = os.path.expanduser("~/.u2net")
    if model is None:
        # Pinned: one model for every frame, or a loop changes its edges halfway through.
        model = "u2netp"
    sess = _SESSIONS.get(model)
    if sess is None:
        sess = _SESSIONS[model] = ort.InferenceSession(f"{home}/{model}.onnx", providers=["CPUExecutionProvider"])
    size = 1024 if model.startswith("isnet") else 320
    img = Image.fromarray(a.astype(np.uint8)).resize((size, size), Image.LANCZOS)
    x = np.asarray(img).astype(np.float32) / 255.0
    if model.startswith("isnet"):
        x = (x - 0.5) / 1.0
    else:
        x = x / max(x.max(), 1e-6)
        x = (x - np.array([0.485, 0.456, 0.406])) / np.array([0.229, 0.224, 0.225])
    x = x.transpose(2, 0, 1)[None].astype(np.float32)
    out = sess.run(None, {sess.get_inputs()[0].name: x})[0][0, 0]
    out = (out - out.min()) / max(out.max() - out.min(), 1e-6)
    h, w = a.shape[:2]
    return np.asarray(Image.fromarray((out * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)).astype(np.float32) / 255.0


def model_pockets(a, rgb, alpha, mask, ground=None):
    """
    Clears pale, colourless pixels the model calls background but the border flood could
    not reach — the pockets between limbs. Only near-ground colours are touched, so a
    thin coloured part the coarse model misses (a beak tip, a hoof) keeps its own alpha.
    Their alpha comes from how far they are from the ground's white, unmixed like an edge.
    """
    if ground is None:
        border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
        ground = np.median(border, axis=0)
    dist = np.abs(a - ground).max(2)
    sat = a.max(2) - a.min(2)
    pale = (dist < 70) & (sat < 18)
    bg = cv2.GaussianBlur(mask, (0, 0), 1.5) < 0.5
    target = pale & bg & (alpha > 0.02)
    if not target.any():
        _feet_edges(a, rgb, alpha, mask, ground)
        return
    # Grow a step into the soft rim so no white ring is left around a cleared pocket.
    target |= _dilate(target, 2) & pale & (alpha > 0.02)
    # What is left of a pocket is ground greyed by the pet's own shade: draw it the way
    # the floor shadow is drawn — translucent black — never as a pale veil.
    alpha[target] = np.minimum(alpha[target], np.clip(dist[target] / 255 * 1.6, 0, 0.45))
    rgb[target] = 0

    # Darker and still colourless where the model sees ground, down at the feet: that is
    # floor shadow caught between them — translucent black like the rest of the shadow.
    h = a.shape[0]
    low = np.zeros(bg.shape, bool); low[int(h * 0.5):] = True
    dusk = bg & low & (sat < 18) & ~pale & (alpha > 0.02)
    # Right at the floor the shadow picks up the pet's own warm colour (a cream cat's
    # shadow reads brownish). Where the model is sure it is ground, that is shadow too.
    solid = alpha > 0.5
    ys = np.where(solid.any(1))[0]
    if len(ys):
        top, bottom = ys.min(), ys.max()
        floor = np.zeros(bg.shape, bool); floor[top + int(0.88 * (bottom - top)):] = True
        sure = cv2.GaussianBlur(mask, (0, 0), 1.5) < 0.1
        lum = a.mean(2)
        dusk |= floor & sure & (sat < 40) & (lum < ground.mean() - 15) & (alpha > 0.02)
    alpha[dusk] = np.minimum(alpha[dusk], np.clip(dist[dusk] / 255 * 1.6, 0, 0.45))
    rgb[dusk] = 0

    # The ring a cleared pocket leaves, and the pale line along the feet: unmix them.
    ring = _dilate(target | dusk, 3) & ~(target | dusk) & (alpha > 0.02) & (rgb.max(2) >= 1)
    decontaminate(a, rgb, alpha, ring, ground, solid_hint=~bg)
    _feet_edges(a, rgb, alpha, mask, ground)


def _feet_edges(a, rgb, alpha, mask, ground):
    h = a.shape[0]
    solid = alpha > 0.5
    ys = np.where(solid.any(1))[0]
    if len(ys) == 0:
        return
    top, bottom = ys.min(), ys.max()
    feet = np.zeros(alpha.shape, bool); feet[top + int(0.8 * (bottom - top)):] = True
    shade = rgb.max(2) < 1          # floor shadow, already translucent black
    edge = feet & (_dilate(alpha < 0.5, 2)) & (alpha > 0.02) & ~shade
    decontaminate(a, rgb, alpha, edge, ground, solid_hint=cv2.GaussianBlur(mask, (0, 0), 1.5) > 0.5)


def decontaminate(a, rgb, alpha, region, ground, solid_hint=None, sigma=3.0):
    """
    Re-solves alpha on [region] by unmixing each pixel between the ground's white and
    the pet's own colour nearby: a = alpha*F + (1-alpha)*G. F is a blur of the pet's
    fully opaque, non-pale pixels around it. This takes the last 1–2 px white ring off a
    cleared pocket and the pale line under the feet, where the colour rules saw "pet".
    """
    if not region.any():
        return
    sat = a.max(2) - a.min(2)
    dist = np.abs(a - ground).max(2)
    fg = (alpha > 0.98) & ((sat > 18) | (dist > 70))
    if solid_hint is not None:
        fg &= solid_hint
    w = cv2.GaussianBlur(fg.astype(np.float32), (0, 0), sigma)
    F = np.dstack([cv2.GaussianBlur(a[..., c] * fg, (0, 0), sigma) for c in range(3)]) / np.maximum(w, 1e-4)[..., None]
    ok = region & (w > 0.05)
    d = F[ok] - ground
    num = ((a[ok] - ground) * d).sum(1)
    den = np.maximum((d * d).sum(1), 1.0)
    est = np.clip(num / den, 0, 1)
    alpha[ok] = np.minimum(alpha[ok], est)
    m = np.maximum(alpha[ok], 1e-3)[:, None]
    rgb[ok] = np.clip((a[ok] - ground * (1 - m)) / m, 0, 255)


def green_floor(a, rgb, alpha, mask):
    """
    On a green key the floor under the feet can carry pale, colourless leftovers (baked in
    from the start frame, or the contact shadow despilled to grey). Where the model is sure
    it is ground, at the bottom of the pet, they become translucent-black shadow.
    """
    solid = alpha > 0.5
    ys = np.where(solid.any(1))[0]
    if len(ys) == 0:
        return
    top, bottom = ys.min(), ys.max()
    floor = np.zeros(alpha.shape, bool); floor[top + int(0.88 * (bottom - top)):] = True
    sure = cv2.GaussianBlur(mask, (0, 0), 1.5) < 0.1
    c = rgb.astype(np.float32)
    sat = c.max(2) - c.min(2)
    lum = c.mean(2)
    shade = floor & sure & (sat < 40) & (alpha > 0.02)
    alpha[shade] = np.minimum(alpha[shade], np.clip((255 - lum[shade]) / 255 * 1.6, 0, 0.45))
    rgb[shade] = 0
