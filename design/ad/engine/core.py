"""
The renderer's ground floor: curves, colours, SVG paths, the camera and assets.

Everything is drawn with Skia — the same 2D engine Compose uses on Android — so the mark
and the UI are rasterised the way the app rasterises them. The world is in pixels: a
flat "station" at depth DIST in front of the camera maps 1:1 onto the screen, which is
how every station's home pose is defined.
"""
import functools, math, os
import numpy as np
import skia

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
BUILD = os.path.join(ROOT, "build", "ad")
FONTS = os.path.join(BUILD, "fonts")
PETS = os.path.join(BUILD, "pets")
DRAWABLE = os.path.join(ROOT, "sadora-client", "shared", "src", "commonMain", "composeResources", "drawable")

FPS = 60
FOCAL = 1500.0       # pixels; a plane DIST away maps 1:1
DIST = 1500.0

# ---------------------------------------------------------------- curves (the app's own)


@functools.lru_cache(maxsize=None)
def bezier(x1, y1, x2, y2):
    """CSS cubic-bezier as a function of t in [0, 1]. Overshoot (y > 1) is kept."""
    def sample(t, a, b):
        return ((1 - 3 * b + 3 * a) * t + (3 * b - 6 * a)) * t * t + 3 * a * t

    def slope(t, a, b):
        return 3 * (1 - 3 * b + 3 * a) * t * t + 2 * (3 * b - 6 * a) * t + 3 * a

    def f(x):
        if x <= 0:
            return 0.0
        if x >= 1:
            return 1.0
        t = x
        for _ in range(8):
            d = sample(t, x1, x2) - x
            s = slope(t, x1, x2)
            if abs(d) < 1e-6 or abs(s) < 1e-6:
                break
            t -= d / s
        t = min(1.0, max(0.0, t))
        return sample(t, y1, y2)
    return f


EMPH = bezier(0.2, 0, 0, 1)        # emphasized
GENTLE = bezier(0.4, 0, 0.2, 1)    # gentle
POP = bezier(0.34, 1.56, 0.64, 1)  # pop, overshoots
DRAW = bezier(0.65, 0, 0.25, 1)    # Brand.kt DrawEasing
OUT = bezier(0, 0, 0.58, 1)        # Brand.kt OutEasing
IN = bezier(0.42, 0, 1, 1)
LINEAR = lambda x: min(1.0, max(0.0, x))


def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def ph(t, a, b):
    """Linear phase of t through [a, b], clamped to [0, 1]."""
    if b <= a:
        return 1.0 if t >= b else 0.0
    return clamp((t - a) / (b - a))


def tw(t, span, ease=EMPH):
    """Eased phase of t through span = (a, b)."""
    return ease(ph(t, span[0], span[1]))


def at(t, start, dur, ease=EMPH):
    return ease(ph(t, start, start + dur))


def lerp(a, b, k):
    return a + (b - a) * k


def lerpv(a, b, k):
    return tuple(x + (y - x) * k for x, y in zip(a, b))


def smooth(x):
    x = clamp(x)
    return x * x * (3 - 2 * x)


def bell(t, a, b):
    """0 → 1 → 0 across [a, b], smooth."""
    k = ph(t, a, b)
    return math.sin(math.pi * k) ** 2 if 0 < k < 1 else 0.0


# ---------------------------------------------------------------- colour


def hexc(h, a=1.0):
    h = h.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return skia.Color(r, g, b, int(round(clamp(a) * 255)))


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255


def mix(h1, h2, k, a=1.0):
    c = rgb(h1) * (1 - k) + rgb(h2) * k
    return skia.Color(*[int(round(v * 255)) for v in c], int(round(clamp(a) * 255)))


def ramp(stops, k, a=1.0):
    """A colour from [(pos, '#hex'), ...] at k."""
    k = clamp(k)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if k <= p1:
            return mix(c0, c1, 0 if p1 == p0 else (k - p0) / (p1 - p0), a)
    return hexc(stops[-1][1], a)


# Tokens: Colors.kt dark set, Brand.kt, PhaseColors, StagePalettes.
C = dict(
    bg="#0F0D24", surface="#1B1838", surface2="#272348", line="#2E2A52", lineStrong="#78729F",
    text="#F3F0FA", muted="#A39DBF", muted2="#948EB0", primary="#8E7BFF", textAccent="#B2A6FF",
    secondary="#FF7EC4", accent="#63D8FF", success="#3FCF98", warm="#FFC46B", danger="#FF5C64",
    b1="#B06CFF", b2="#8A8CFF", b3="#F68CCB", deep1="#2A1D5C", deep2="#0E0B1F",
    orb1="#C9A1FF", orb2="#8E5CFF", orb3="#5B3FE0", orb4="#3A2A9A", haloBlue="#7BB6FF",
    period="#FF4F9A", follicular="#4FB8FF", fertile="#FF63C8", luteal="#8E7BFF",
    heroStart="#6A4FF0", heroEnd="#C93C88", warmInk="#2A1145",
)
BRAND = [(0.0, C["b1"]), (0.45, C["b2"]), (1.0, C["b3"])]
STAGES = [  # StagePalettes: start, end — Sikl, Homiladorlik, Tug'ruqdan keyin, Menopauza
    ("#7B61FF", "#FF6FB8"),
    ("#FFB020", "#FF8E92"),
    ("#FF8AA3", "#FFB020"),
    ("#63D8FF", "#2FBF8F"),
]


def brand_shader(x0, y0, x1, y1, a=1.0):
    return skia.GradientShader.MakeLinear(
        [skia.Point(x0, y0), skia.Point(x1, y1)],
        [hexc(C["b1"], a), hexc(C["b2"], a), hexc(C["b3"], a)], [0.0, 0.45, 1.0])


def paint(color=None, shader=None, stroke=None, cap=skia.Paint.kRound_Cap, alpha=None,
          blur=None, blend=None, aa=True):
    p = skia.Paint(AntiAlias=aa)
    if color is not None:
        p.setColor(color)
    if shader is not None:
        p.setShader(shader)
    if alpha is not None:
        p.setAlphaf(clamp(alpha))
    if stroke is not None:
        p.setStyle(skia.Paint.kStroke_Style)
        p.setStrokeWidth(stroke)
        p.setStrokeCap(cap)
        p.setStrokeJoin(skia.Paint.kRound_Join)
    if blur:
        p.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, blur))
    if blend is not None:
        p.setBlendMode(blend)
    return p


# ---------------------------------------------------------------- SVG paths


def _tokens(d):
    import re
    for m in re.finditer(r"[MmLlHhVvCcSsQqAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", d):
        yield m.group(0)


def svg_path(d):
    """An SVG path string as a skia.Path (M L H V C A Z, absolute and relative)."""
    p = skia.Path()
    toks = list(_tokens(d))
    i, cmd = 0, None
    cx = cy = sx = sy = 0.0

    def num():
        nonlocal i
        v = float(toks[i]); i += 1
        return v

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]; i += 1
        rel = cmd.islower()
        c = cmd.upper()
        if c == "Z":
            p.close(); cx, cy = sx, sy
            continue
        if c == "M":
            x, y = num(), num()
            if rel: x += cx; y += cy
            p.moveTo(x, y); cx, cy, sx, sy = x, y, x, y
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = num(), num()
            if rel: x += cx; y += cy
            p.lineTo(x, y); cx, cy = x, y
        elif c == "H":
            x = num() + (cx if rel else 0); p.lineTo(x, cy); cx = x
        elif c == "V":
            y = num() + (cy if rel else 0); p.lineTo(cx, y); cy = y
        elif c == "C":
            v = [num() for _ in range(6)]
            if rel: v = [v[k] + (cx if k % 2 == 0 else cy) for k in range(6)]
            p.cubicTo(*v); cx, cy = v[4], v[5]
        elif c == "A":
            rx, ry, rot, large, sweep, x, y = [num() for _ in range(7)]
            if rel: x += cx; y += cy
            p.arcTo(rx, ry, rot,
                    skia.Path.ArcSize.kLarge_ArcSize if large else skia.Path.ArcSize.kSmall_ArcSize,
                    skia.PathDirection.kCW if sweep else skia.PathDirection.kCCW, x, y)
            cx, cy = x, y
        else:
            raise ValueError(f"unsupported path command {cmd}")
    return p


def path_points(path, step=1.0):
    """A path's outline sampled every `step` units: (N, 2) array, plus its length."""
    m = skia.PathMeasure(path, False)
    L = m.getLength()
    n = max(2, int(L / step) + 1)
    pts = []
    for k in range(n):
        pos, _ = m.getPosTan(L * k / (n - 1))
        pts.append((pos.x(), pos.y()))
    return np.array(pts, np.float32), L


# ---------------------------------------------------------------- camera


def look(pos, target, roll=0.0):
    """Camera basis: right, down, forward (screen x right, y down, z into the scene)."""
    f = np.asarray(target, np.float64) - np.asarray(pos, np.float64)
    f /= np.linalg.norm(f)
    r = np.cross(np.array([0.0, 1.0, 0.0]), f)
    if np.linalg.norm(r) < 1e-6:
        r = np.array([1.0, 0, 0])
    r /= np.linalg.norm(r)
    d = np.cross(f, r)
    if roll:
        cr, sr = math.cos(roll), math.sin(roll)
        r, d = r * cr + d * sr, -r * sr + d * cr
    return r, d, f


class Camera:
    def __init__(self, pos, target, W, H, focal=FOCAL, roll=0.0, shift=(0.0, 0.0)):
        self.pos = np.asarray(pos, np.float64)
        self.r, self.d, self.f = look(pos, target, roll)
        self.W, self.H, self.focal = W, H, focal
        self.cx, self.cy = W / 2 + shift[0], H / 2 + shift[1]

    def to_cam(self, P):
        q = np.asarray(P, np.float64) - self.pos
        return np.stack([q @ self.r, q @ self.d, q @ self.f], -1)

    def project(self, P):
        """World points (…, 3) → screen (…, 2) and camera depth (…)."""
        c = self.to_cam(P)
        z = np.maximum(c[..., 2], 1e-3)
        x = self.cx + self.focal * c[..., 0] / z
        y = self.cy + self.focal * c[..., 1] / z
        return np.stack([x, y], -1), c[..., 2]

    def plane_matrix(self, origin, ux, uy):
        """skia.Matrix mapping local (u, v) → screen for the plane origin + u·ux + v·uy."""
        o, a, b = self.to_cam(origin), self.to_cam(np.asarray(origin) + ux) - self.to_cam(origin), \
            self.to_cam(np.asarray(origin) + uy) - self.to_cam(origin)
        F, cx, cy = self.focal, self.cx, self.cy
        K = np.array([[F, 0, cx], [0, F, cy], [0, 0, 1.0]])
        M = K @ np.stack([a, b, o], 1)
        M = M / M[2, 2]
        return skia.Matrix.MakeAll(*M[0], *M[1], *M[2]), o[2]


class Plane:
    """A flat station: w × h local pixels, centred at `center`, facing −z unless rotated."""

    def __init__(self, center, w, h, yaw=0.0, pitch=0.0, roll=0.0, scale=1.0):
        self.center = np.asarray(center, np.float64)
        self.w, self.h, self.scale = w, h, scale
        cy, sy = math.cos(yaw), math.sin(yaw)
        cp, sp = math.cos(pitch), math.sin(pitch)
        cr, sr = math.cos(roll), math.sin(roll)
        ux = np.array([cy, 0, sy]); uy = np.array([0, 1.0, 0])
        uy = uy * cp + np.cross(ux, uy) * sp * -1
        ux, uy = ux * cr + uy * sr, -ux * sr + uy * cr
        self.ux, self.uy = ux * scale, uy * scale

    def world(self, u, v):
        """Local pixel (u, v), origin at the top-left corner, to world."""
        return self.center + (u - self.w / 2) * self.ux + (v - self.h / 2) * self.uy

    def matrix(self, cam):
        origin = self.world(0, 0)
        return cam.plane_matrix(origin, self.ux, self.uy)

    def depth(self, cam):
        return float(cam.to_cam(self.center)[2])

    def visible(self, cam, near=60.0):
        cs = [self.world(0, 0), self.world(self.w, 0), self.world(0, self.h), self.world(self.w, self.h)]
        return min(cam.to_cam(c)[2] for c in cs) > near


def home(plane, dist=DIST):
    """The camera pose that maps this plane 1:1 onto the screen (centred)."""
    n = np.cross(plane.uy, plane.ux)
    n /= np.linalg.norm(n)
    s = np.linalg.norm(plane.ux)
    return plane.center + n * dist * s, plane.center


# ---------------------------------------------------------------- images


@functools.lru_cache(maxsize=512)
def image(path):
    data = skia.Data.MakeFromFileName(path)
    img = skia.Image.MakeFromEncoded(data)
    if img is None:
        raise FileNotFoundError(path)
    return img


def drawable(name):
    return image(os.path.join(DRAWABLE, name + ".png"))


def draw_image(canvas, img, x, y, w, h, alpha=1.0, smooth=True):
    p = skia.Paint(AntiAlias=True)
    p.setAlphaf(clamp(alpha))
    canvas.drawImageRect(img, skia.Rect.MakeXYWH(x, y, w, h),
                         skia.SamplingOptions(skia.CubicResampler.Mitchell()) if smooth else skia.SamplingOptions(),
                         p)
