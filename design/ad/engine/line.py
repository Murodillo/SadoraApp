"""
The path: one glowing line, and the mark it writes.

`Pen` is the single shared stroke. The same object wipes the S on (exactly as Brand.kt's
DstIn mask does), becomes the cycle ring, the curl of the Gul, the typing cursor, the
threads, the four branches and the baseline under the type. `glow_line` gives any pen
segment its light; `draw_mark` / `draw_wordmark` / `draw_lockup` are Brand.kt to the unit.
"""
import functools, math
import numpy as np
import skia

from core import (BRAND, C, OUT, POP, DRAW, brand_shader, clamp, hexc, mix, paint, ph, ramp,
                  svg_path)
import text as T

# ---------------------------------------------------------------- Brand.kt vectors, verbatim

MARK_OUTLINE = (
    "M162.5,31.7L161.2,29.1L159.6,26.5L157.8,24.1L155.9,21.7L153.7,19.5L151.4,17.4L148.9,15.4"
    "L146.2,13.5L143.4,11.8L140.4,10.2L137.3,8.8L134.1,7.5L130.7,6.3L127.3,5.3L123.8,4.5"
    "L120.2,3.8L116.6,3.3L112.9,3.0L109.2,2.9L105.4,2.9L101.6,3.1L97.8,3.5L94.0,4.1L90.3,4.9"
    "L86.5,5.9L82.8,7.1L79.2,8.5L75.6,10.1L72.1,12.0L68.6,14.2L65.3,16.5L62.2,19.2L59.2,22.0"
    "L56.4,25.0L54.0,28.2L51.8,31.5L49.9,35.0L48.2,38.6L46.9,42.3L45.8,46.0L45.1,49.9"
    "L44.6,53.7L44.4,57.7L44.4,61.6L44.8,65.6L45.4,69.6L46.2,73.5L47.3,77.4L48.7,81.3"
    "L50.3,85.2L52.1,89.0L54.1,92.7L56.3,96.4L58.8,100.0L61.5,103.6L64.3,107.0L67.4,110.4"
    "L70.6,113.7L74.0,116.8L77.6,119.9L81.4,122.9L85.4,125.7L89.5,128.4L93.7,130.9"
    "L97.4,133.1L100.9,135.2L104.3,137.5L107.6,139.7L110.7,142.1L113.8,144.4L116.6,146.8"
    "L119.4,149.2L122.0,151.7L124.4,154.1L126.7,156.6L128.8,159.0L130.7,161.5L132.5,163.9"
    "L134.1,166.3L135.4,168.7L136.6,171.0L137.6,173.3L138.5,175.6L139.1,177.8L139.6,180.1"
    "L139.9,182.2L140.0,184.4L139.9,186.6L139.6,188.9L139.1,191.1L138.4,193.5L137.4,195.8"
    "L136.1,198.3L134.5,200.8L132.6,203.4L130.4,206.0L128.7,207.8L126.7,209.5L124.6,211.2"
    "L122.2,212.9L119.7,214.4L117.1,215.8L114.3,217.1L111.4,218.3L108.4,219.3L105.3,220.2"
    "L102.1,221.0L98.9,221.5L95.5,221.9L92.2,222.1L88.8,222.2L85.4,222.0L82.0,221.6"
    "L78.5,221.1L75.1,220.3L71.8,219.2L68.4,218.0L65.1,216.5L61.9,214.7L58.8,212.7"
    "L55.8,210.4L52.8,207.8L50.0,204.9L47.4,201.7L44.9,198.2L42.5,194.4L40.4,190.3"
    "L38.6,185.8L37.4,186.2L39.2,190.8L41.1,195.2L43.1,199.2L45.4,203.1L47.9,206.6"
    "L50.5,209.9L53.3,213.0L56.3,215.8L59.4,218.4L62.6,220.8L66.0,222.9L69.4,224.8"
    "L73.0,226.4L76.6,227.8L80.4,229.0L84.1,230.0L87.9,230.7L91.8,231.3L95.7,231.6"
    "L99.5,231.7L103.4,231.6L107.2,231.4L111.0,230.9L114.8,230.2L118.5,229.3L122.1,228.3"
    "L125.6,227.1L129.0,225.6L132.4,224.0L135.6,222.2L138.7,220.3L141.6,218.0L145.0,215.0"
    "L148.1,211.8L150.9,208.4L153.3,205.0L155.4,201.4L157.2,197.7L158.6,193.9L159.7,190.0"
    "L160.4,186.1L160.7,182.2L160.7,178.2L160.4,174.2L159.7,170.3L158.8,166.4L157.6,162.6"
    "L156.1,158.8L154.3,155.1L152.3,151.5L150.2,148.0L147.8,144.5L145.2,141.1L142.4,137.8"
    "L139.4,134.5L136.3,131.3L133.0,128.2L129.6,125.2L126.0,122.3L122.3,119.5L118.4,116.7"
    "L114.5,114.1L110.4,111.5L106.3,109.1L102.8,107.0L99.5,104.9L96.3,102.7L93.3,100.4"
    "L90.4,98.0L87.7,95.6L85.1,93.1L82.7,90.6L80.4,88.1L78.3,85.5L76.3,82.9L74.5,80.3"
    "L72.8,77.7L71.4,75.1L70.1,72.4L68.9,69.8L67.9,67.2L67.1,64.6L66.4,62.1L65.9,59.6"
    "L65.6,57.1L65.4,54.6L65.4,52.1L65.6,49.7L65.9,47.3L66.4,44.9L67.1,42.5L68.0,40.2"
    "L69.1,37.8L70.4,35.5L72.0,33.1L73.8,30.8L75.9,28.6L78.1,26.5L80.4,24.5L82.9,22.7"
    "L85.5,20.9L88.2,19.4L91.0,18.0L93.9,16.7L97.0,15.5L100.1,14.6L103.2,13.7L106.4,13.1"
    "L109.7,12.5L113.0,12.2L116.2,12.0L119.5,11.9L122.8,12.0L126.0,12.3L129.2,12.7"
    "L132.4,13.2L135.5,14.0L138.5,14.8L141.4,15.9L144.2,17.1L146.9,18.4L149.5,19.9"
    "L151.9,21.6L154.2,23.4L156.3,25.4L158.3,27.5L160.0,29.8L161.5,32.3Z"
)
MARK_STROKE = "M162,32C148,4 95,-2 68,25C41,52 58,96 100,120C142,144 168,182 136,212C112,234 56,236 38,186"
MARK_DOT = "M55,149 C54.6,154 54.2,158 53.4,161 A8.5,8.5 0 1 1 43.1,151.7 C46.5,150 50.5,149.4 55,149 Z"
WORD_STROKES = [
    "M30,7 C27,2 17,0 10,3 C3,6 3,14 10,17 L24,23 C31,26 31,35 24,38 C17,41 7,39 4,34",
    "M60,40 L77,0 L94,40",
    "M124,0 V40 H134 C161,40 161,0 134,0 Z",
    "M197,0 A16,20 0 1,0 197,40 A16,20 0 1,0 197,0",
    "M244,40 V0 H258 C274,0 274,22 258,22 H244 M258,22 L272,40",
    "M300,40 L317,0 L334,40",
]
MARK_VB = (20.0, -10.0, 160.0, 250.0)
WORD_VB = (-2.0, -3.0, 338.0, 47.0)
TAGLINE = "EVERY WOMAN. EVERY MOMENT."
DOT_VIOLET = C["b1"]


@functools.lru_cache(maxsize=None)
def _paths():
    return dict(outline=svg_path(MARK_OUTLINE), stroke=svg_path(MARK_STROKE), dot=svg_path(MARK_DOT),
                words=[svg_path(d) for d in WORD_STROKES])


# ---------------------------------------------------------------- the pen


class Pen:
    """
    A stroke with an arc-length parameter. Built from a skia.Path (2D, any units) or from a
    polyline of 2D points. `segment(a, b)` is the part between fractions a and b.
    """

    def __init__(self, path=None, points=None, closed=False):
        if path is None:
            path = skia.Path()
            pts = np.asarray(points, np.float64)
            path.moveTo(*pts[0])
            for p in pts[1:]:
                path.lineTo(*p)
            if closed:
                path.close()
        self.path = path
        self.measure = skia.PathMeasure(path, False)
        self.length = self.measure.getLength()

    def segment(self, a, b):
        out = skia.Path()
        a, b = clamp(a), clamp(b)
        if b > a:
            self.measure.getSegment(self.length * a, self.length * b, out, True)
        return out

    def at(self, s):
        pos, tan = self.measure.getPosTan(self.length * clamp(s))
        return (pos.x(), pos.y()), (tan.x(), tan.y())


@functools.lru_cache(maxsize=None)
def mark_pen():
    return Pen(_paths()["stroke"])


def glow_line(canvas, path, width, shader=None, color=None, alpha=1.0, glow=1.0, core=True,
              cap=skia.Paint.kRound_Cap):
    """
    Light along a path already in screen space: a wide soft halo, a tighter glow and a
    bright core. The halo and glow add (kPlus) so crossings brighten like real light.
    """
    if path.isEmpty() or alpha <= 0:
        return

    def p(w, a, blur=None, plus=False, hot=False):
        if hot:
            q = paint(color=skia.Color(255, 250, 255), stroke=w, cap=cap)
        else:
            q = paint(color=None if shader else (color if color is not None else hexc(C["b2"])),
                      shader=shader, stroke=w, cap=cap, blur=blur,
                      blend=skia.BlendMode.kPlus if plus else None)
        q.setAlphaf(clamp(a))
        return q

    if glow > 0:
        canvas.drawPath(path, p(width * 7.0, 0.16 * glow * alpha, blur=width * 2.6, plus=True))
        canvas.drawPath(path, p(width * 2.6, 0.42 * glow * alpha, blur=width * 0.9, plus=True))
    if core:
        canvas.drawPath(path, p(width, alpha))
        canvas.drawPath(path, p(max(1.0, width * 0.36), 0.55 * alpha, hot=True))


def pen_head(canvas, x, y, r, alpha=1.0, color=None):
    """The bright tip that leads the line."""
    if alpha <= 0:
        return
    col = color or hexc(C["b1"])
    halo = skia.GradientShader.MakeRadial(skia.Point(x, y), r * 5,
                                          [skia.Color(255, 255, 255, int(200 * alpha)),
                                           skia.ColorSetA(col, int(120 * alpha)),
                                           skia.ColorSetA(col, 0)], [0.0, 0.18, 1.0])
    q = skia.Paint(AntiAlias=True, Shader=halo, BlendMode=skia.BlendMode.kPlus)
    canvas.drawCircle(x, y, r * 5, q)


# ---------------------------------------------------------------- the mark (Brand.kt)


def draw_mark(canvas, height, progress=1.0, dot=1.0, tone="gradient", dot_squash=(1.0, 1.0),
              dot_extra=1.0):
    """
    SadoraMark at `height` px, top-left at the canvas origin; width is height·160/250.
    `progress` wipes the letter on along the pen's path; `dot` is the pop (may overshoot).
    """
    P = _paths()
    vx, vy, vw, vh = MARK_VB
    s = height / vh
    canvas.save()
    canvas.scale(s, s)
    canvas.translate(-vx, -vy)
    if tone == "gradient":
        sh = skia.GradientShader.MakeLinear([skia.Point(0, 0), skia.Point(0, vh)],
                                            [hexc(C["b1"]), hexc(C["b2"]), hexc(C["b3"])], [0.0, 0.45, 1.0])
        fill = skia.Paint(AntiAlias=True, Shader=sh)
        dot_col = hexc(DOT_VIOLET)
    else:
        fill = skia.Paint(AntiAlias=True, Color=hexc(C["text"] if tone == "white" else "#141414"))
        dot_col = fill.getColor()
    if progress >= 1.0:
        canvas.drawPath(P["outline"], fill)
    elif progress > 0.0:
        # Brand.kt: the letter, then DstIn the pen's stroke over it. In Skia a DstIn *draw*
        # only touches the stroke's own pixels, so the stroke goes in its own layer and the
        # layer is composited with DstIn — that clears everything the pen hasn't reached.
        bounds = skia.Rect.MakeXYWH(vx, vy, vw, vh)
        canvas.saveLayer(bounds, None)
        canvas.drawPath(P["outline"], fill)
        canvas.saveLayer(bounds, skia.Paint(BlendMode=skia.BlendMode.kDstIn))
        wipe = mark_pen().segment(0.0, progress)
        canvas.drawPath(wipe, skia.Paint(AntiAlias=True, Color=skia.ColorBLACK, Style=skia.Paint.kStroke_Style,
                                         StrokeWidth=44, StrokeCap=skia.Paint.kRound_Cap,
                                         StrokeJoin=skia.Paint.kRound_Join))
        canvas.restore()
        canvas.restore()
    if dot > 0.0:
        b = P["dot"].getBounds()
        cx, cy = b.centerX(), b.centerY()
        canvas.save()
        k = dot * dot_extra
        canvas.translate(cx, cy)
        canvas.scale(k * dot_squash[0], k * dot_squash[1])
        canvas.translate(-cx, -cy)
        q = skia.Paint(AntiAlias=True, Color=dot_col)
        q.setAlphaf(clamp(dot))
        canvas.drawPath(P["dot"], q)
        canvas.restore()
    canvas.restore()


def mark_point(height, s):
    """A point on the pen's path (fraction s) in the mark's pixel space at `height`."""
    (x, y), tan = mark_pen().at(s)
    vx, vy, vw, vh = MARK_VB
    k = height / vh
    return ((x - vx) * k, (y - vy) * k), tan


def dot_center(height):
    b = _paths()["dot"].getBounds()
    vx, vy, vw, vh = MARK_VB
    k = height / vh
    return ((b.centerX() - vx) * k, (b.centerY() - vy) * k), (b.width() * k)


def mark_size(height):
    return height * MARK_VB[2] / MARK_VB[3], height


# ---------------------------------------------------------------- wordmark + lockup


def draw_wordmark(canvas, width, letters=None, color=None):
    """SADORA as six monoline strokes, `width` px wide, top-left at origin."""
    P = _paths()
    vx, vy, vw, vh = WORD_VB
    s = width / vw
    col = hexc(color or C["text"])
    canvas.save()
    canvas.scale(s, s)
    canvas.translate(-vx, -vy)
    for i, path in enumerate(P["words"]):
        t = 1.0 if letters is None else letters[i]
        if t <= 0:
            continue
        canvas.save()
        canvas.translate(0, (1 - t) * 8)
        q = skia.Paint(AntiAlias=True, Color=col, Style=skia.Paint.kStroke_Style, StrokeWidth=3.2,
                       StrokeCap=skia.Paint.kRound_Cap, StrokeJoin=skia.Paint.kRound_Join)
        q.setAlphaf(clamp(t))
        canvas.drawPath(path, q)
        canvas.restore()
    canvas.restore()


def lockup_metrics(size):
    """SadoraLogoReveal's column: mark, 0.2·size gap, wordmark, 0.075·w gap, tagline."""
    mw, mh = mark_size(size)
    ww = size * 1.45
    wh = ww * WORD_VB[3] / WORD_VB[2]
    tag_size = ww * 0.058 * 1.0
    gap1 = size * 0.2
    gap2 = ww * 0.075
    asc, desc, cap = T.metrics("ui6", tag_size)
    total = mh + gap1 + wh + gap2 + asc + desc
    return dict(mw=mw, mh=mh, ww=ww, wh=wh, gap1=gap1, gap2=gap2, tag=tag_size, asc=asc, desc=desc,
                total=total, width=max(mw, ww))


def draw_lockup(canvas, cx, top, size, progress=1.0, dot=1.0, letters=None, tagline=1.0,
                tagline_text=TAGLINE, dot_squash=(1.0, 1.0), dot_extra=1.0, word_color=None):
    """The vertical lockup centred on cx, its top edge at `top`."""
    m = lockup_metrics(size)
    canvas.save()
    canvas.translate(cx - m["mw"] / 2, top)
    draw_mark(canvas, m["mh"], progress, dot, dot_squash=dot_squash, dot_extra=dot_extra)
    canvas.restore()
    wy = top + m["mh"] + m["gap1"]
    canvas.save()
    canvas.translate(cx - m["ww"] / 2, wy)
    draw_wordmark(canvas, m["ww"], letters, color=word_color)
    canvas.restore()
    if tagline > 0:
        ty = wy + m["wh"] + m["gap2"] + m["asc"]
        T.draw(canvas, "ui6", tagline_text, m["tag"], cx, ty, color=C["textAccent"], alpha=tagline,
               track=0.18, align="center")
    return m


def reveal_state(clock_ms, compressed=False):
    """Brand.kt's SadoraLogoReveal at a clock in ms: (stroke, dot, letters[6], tagline)."""
    if compressed:
        draw_ms, dot_delay, dot_ms = 800, 650, 450
        letter_delay, stagger, letter_ms = 650, 80, 400
        tag_delay, tag_ms = 1200, 500
    else:
        draw_ms, dot_delay, dot_ms = 1500, 1250, 600
        letter_delay, stagger, letter_ms = 1200, 80, 500
        tag_delay, tag_ms = 1900, 700

    def phase(d, dur):
        return clamp((clock_ms - d) / dur)
    stroke = DRAW(phase(0, draw_ms))
    dot = POP(phase(dot_delay, dot_ms))
    letters = [OUT(phase(letter_delay + i * stagger, letter_ms)) for i in range(6)]
    tag = OUT(phase(tag_delay, tag_ms))
    return stroke, dot, letters, tag
