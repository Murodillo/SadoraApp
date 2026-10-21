"""
The app's dark UI rebuilt from its tokens, and its clay drawn the way Clay.kt draws it.

Every size here is in pixels; stations pass `u` (pixels per dp) so a card is laid out in
the app's own dp and then scaled for the film. Colours come only from core.C.
"""
import math
import numpy as np
import skia

from core import C, STAGES, clamp, drawable, draw_image, hexc, mix, paint, rgb
import text as T


def _c(h, a=1.0):
    return hexc(h, a) if isinstance(h, str) else h


def clay_light(h):
    return mix(h, "#FFFFFF", 0.36)


def clay_dark(h):
    return mix(h, "#000000", 0.12)


def rrect(x, y, w, h, r):
    return skia.RRect.MakeRectXY(skia.Rect.MakeXYWH(x, y, w, h), r, r)


def shadow(canvas, x, y, w, h, r, u, alpha=0.45, lift=10):
    """A soft drop shadow (the dark theme has none in the app; the film's stage needs one)."""
    q = paint(color=skia.Color(4, 2, 14), blur=lift * u * 1.4)
    q.setAlphaf(alpha)
    canvas.drawRRect(rrect(x, y + lift * u * 0.6, w, h, r), q)


def clay_surface(canvas, x, y, w, h, r, u, fill=None, shader=None, gloss=1.0, streak=False,
                 alpha=1.0, drop=True, border=True):
    """Clay.kt claySurface on the dark theme, plus a stage shadow."""
    if drop:
        shadow(canvas, x, y, w, h, r, u, alpha=0.5 * alpha)
    canvas.save()
    canvas.clipRRect(rrect(x, y, w, h, r), True)
    if alpha < 1:
        canvas.saveLayerAlpha(skia.Rect.MakeXYWH(x - 2, y - 2, w + 4, h + 4), int(255 * clamp(alpha)))
    q = paint(color=_c(fill or C["surface"])) if shader is None else paint(shader=shader)
    canvas.drawRect(skia.Rect.MakeXYWH(x, y, w, h), q)
    lit = min(h * 0.5, 30 * u)
    under = min(h * 0.45, 22 * u)
    canvas.drawRect(skia.Rect.MakeXYWH(x, y, w, lit), paint(shader=skia.GradientShader.MakeLinear(
        [skia.Point(0, y), skia.Point(0, y + lit)], [skia.Color(255, 255, 255, int(255 * 0.07 * gloss)),
                                                       skia.Color(255, 255, 255, 0)])))
    canvas.drawRect(skia.Rect.MakeXYWH(x, y + h - under, w, under), paint(shader=skia.GradientShader.MakeLinear(
        [skia.Point(0, y + h - under), skia.Point(0, y + h)],
        [skia.Color(0, 0, 0, 0), skia.Color(0, 0, 0, int(255 * 0.20 * gloss))])))
    if streak and w > h * 1.4 and h >= 48 * u:
        canvas.drawRRect(rrect(x + h * 0.45, y + h * 0.11, w - h * 0.9, h * 0.15, h * 0.075),
                         paint(color=skia.Color(255, 255, 255, int(255 * 0.32 * gloss))))
    if alpha < 1:
        canvas.restore()
    canvas.restore()
    if border:
        b = paint(shader=skia.GradientShader.MakeLinear(
            [skia.Point(0, y), skia.Point(0, y + h)],
            [skia.Color(255, 255, 255, int(255 * 0.16 * alpha)), hexc(C["line"], 0.9 * alpha)]), stroke=1.0 * u)
        canvas.drawRRect(rrect(x + 0.5 * u, y + 0.5 * u, w - u, h - u, r), b)


def clay_bead(canvas, x, y, r, color, lift=None, alpha=1.0):
    """Clay.kt clayBead."""
    if r <= 0 or alpha <= 0:
        return
    lift = r * 0.26 if lift is None else lift
    canvas.drawCircle(x, y + lift, r * 1.04, paint(color=skia.ColorSetA(clay_dark(color), int(255 * 0.28 * alpha))))
    sh = skia.GradientShader.MakeRadial(skia.Point(x - r * 0.3, y - r * 0.3), r * 1.3,
                                        [clay_light(color), hexc(color), clay_dark(color)], [0.0, 0.7, 1.0])
    q = paint(shader=sh)
    q.setAlphaf(alpha)
    canvas.drawCircle(x, y, r, q)
    canvas.drawCircle(x - r * 0.32, y - r * 0.34, r * 0.26, paint(color=skia.Color(255, 255, 255, int(191 * alpha))))


def clay_ring_track(canvas, cx, cy, radius, stroke, track=None):
    o = skia.Rect.MakeXYWH(cx - radius, cy - radius, radius * 2, radius * 2)
    canvas.drawArc(o, 0, 360, False, paint(color=_c(track or C["surface2"]), stroke=stroke))
    ri = radius - stroke * 0.3
    canvas.drawArc(skia.Rect.MakeXYWH(cx - ri, cy - ri, ri * 2, ri * 2), 0, 360, False,
                   paint(color=skia.Color(0, 0, 0, int(255 * 0.30)), stroke=stroke * 0.38))


def clay_arc(canvas, cx, cy, radius, stroke, start, sweep, color, bead=True, alpha=1.0):
    """Clay.kt clayArc: shadow, body, darker underside, lit face, streak and an end bead."""
    if sweep <= 0:
        return

    def box(r):
        return skia.Rect.MakeXYWH(cx - r, cy - r, r * 2, r * 2)

    def arc(r, w, col, a, dy=0.0, st=start, sw=sweep):
        q = paint(color=col, stroke=w)
        q.setAlphaf(a * alpha)
        o = box(r)
        o.offset(0, dy)
        canvas.drawArc(o, st, sw, False, q)

    arc(radius, stroke * 1.05, clay_dark(color), 0.22, dy=stroke * 0.22)
    arc(radius, stroke, hexc(color), 1.0)
    arc(radius + stroke * 0.3, stroke * 0.36, clay_dark(color), 0.55)
    arc(radius - stroke * 0.12, stroke * 0.5, clay_light(color), 0.55)
    if sweep > 14:
        arc(radius - stroke * 0.2, stroke * 0.16, skia.ColorWHITE, 0.5, st=start + 5, sw=sweep - 10)
    if bead:
        e = math.radians(start + sweep)
        clay_bead(canvas, cx + radius * math.cos(e), cy + radius * math.sin(e), stroke * 0.62, color,
                  lift=stroke * 0.16, alpha=alpha)


def hero_shader(x0, y0, x1, y1):
    return skia.GradientShader.MakeLinear([skia.Point(x0, y0), skia.Point(x1, y1)],
                                          [hexc(C["heroStart"]), hexc(C["heroEnd"])])


def chip(canvas, x, y, label, u, fill=None, color=None, fname="ui6", size=13, pad=12, alpha=1.0,
         outline=None, icon=None, align="left"):
    """A pill: Radius.chip. Returns (w, h)."""
    s = size * u
    tw = T.width(fname, label, s)
    ic = (s * 1.25 + 6 * u) if icon else 0
    w = tw + 2 * pad * u + ic
    h = s * 2.15
    if align == "center":
        x -= w / 2
    if outline:
        q = paint(color=_c(outline), stroke=1.4 * u)
        q.setAlphaf(alpha)
        canvas.drawRRect(rrect(x, y, w, h, h / 2), q)
    else:
        clay_surface(canvas, x, y, w, h, h / 2, u, fill=fill or C["surface2"], alpha=alpha, drop=False)
    if icon:
        draw_image(canvas, drawable(icon), x + pad * u * 0.8, y + (h - s * 1.25) / 2, s * 1.25, s * 1.25, alpha=alpha)
    T.draw(canvas, fname, label, s, x + pad * u + ic, y + h / 2 + s * 0.36, color=color or C["text"], alpha=alpha)
    return w, h


def button(canvas, x, y, w, h, label, u, alpha=1.0, press=0.0, size=15):
    """The primary CTA: the hero gradient as clay, streaked; `press` sinks it."""
    k = 1 - 0.04 * press
    cx, cy = x + w / 2, y + h / 2
    canvas.save()
    canvas.translate(cx, cy)
    canvas.scale(k, k)
    canvas.translate(-cx, -cy)
    clay_surface(canvas, x, y, w, h, h / 2, u, shader=hero_shader(x, y, x + w, y + h), streak=True, alpha=alpha)
    T.draw(canvas, "ui7", label, size * u, cx, cy + size * u * 0.36, color="#FFFFFF", alpha=alpha, align="center")
    canvas.restore()


def icon(canvas, name, x, y, size, alpha=1.0, scale=1.0, rot=0.0):
    """A clay colour icon from the app (composeResources/drawable), centred on x, y."""
    if alpha <= 0 or scale <= 0:
        return
    s = size * scale
    canvas.save()
    canvas.translate(x, y)
    if rot:
        canvas.rotate(rot)
    draw_image(canvas, drawable(name), -s / 2, -s / 2, s, s, alpha=alpha)
    canvas.restore()


def check(canvas, x, y, r, color=None, progress=1.0, alpha=1.0, width=None):
    """A drawn tick in a clay bead."""
    clay_bead(canvas, x, y, r, color or C["success"], alpha=alpha)
    if progress <= 0:
        return
    p = skia.Path()
    pts = [(x - r * 0.42, y + r * 0.02), (x - r * 0.1, y + r * 0.34), (x + r * 0.46, y - r * 0.3)]
    p.moveTo(*pts[0]); p.lineTo(*pts[1]); p.lineTo(*pts[2])
    m = skia.PathMeasure(p, False)
    seg = skia.Path()
    m.getSegment(0, m.getLength() * clamp(progress), seg, True)
    q = paint(color=skia.ColorWHITE, stroke=width or r * 0.26)
    q.setAlphaf(alpha)
    canvas.drawPath(seg, q)


def avatar(canvas, cx, cy, r, initials, u, alpha=1.0):
    """An initials avatar on the hero gradient (no faces: every person here is fictional)."""
    sh = hero_shader(cx - r, cy - r, cx + r, cy + r)
    q = paint(shader=sh)
    q.setAlphaf(alpha)
    canvas.drawCircle(cx, cy, r, q)
    canvas.drawCircle(cx, cy - r * 0.35, r * 0.75, paint(color=skia.Color(255, 255, 255, int(28 * alpha))))
    T.draw(canvas, "ui7", initials, r * 0.78, cx, cy + r * 0.28, color="#FFFFFF", alpha=alpha, align="center")


def orb(canvas, cx, cy, r, t=0.0, alpha=1.0, mark=True):
    """Brand.kt AiOrb: halo, lit sphere, a turning rim light and the white mark."""
    import line as L
    breath = 1 + 0.025 * (1 - math.cos(t * math.pi * 2 / 3.2))
    halo = 1 + 0.06 * (1 - math.cos(t * math.pi * 2 / 3.2))
    rr = r * 1.18 * halo
    canvas.drawCircle(cx, cy, rr, paint(shader=skia.GradientShader.MakeRadial(
        skia.Point(cx, cy), rr, [hexc(C["b1"], 0.55 * alpha), hexc(C["haloBlue"], 0.25 * alpha), hexc(C["b1"], 0)],
        [0.0, 0.45, 0.72])))
    s = r * breath
    canvas.drawCircle(cx, cy, s, paint(shader=skia.GradientShader.MakeRadial(
        skia.Point(cx - r * 0.4, cy - r * 0.5), s * 1.2,
        [hexc(C["orb1"], alpha), hexc(C["orb2"], alpha), hexc(C["orb3"], alpha), hexc(C["orb4"], alpha)],
        [0.0, 0.35, 0.70, 1.0])))
    canvas.save()
    canvas.rotate((t * 45) % 360, cx, cy)
    sw = skia.GradientShader.MakeSweep(cx, cy, [skia.Color(255, 255, 255, 0), skia.Color(255, 255, 255, int(140 * alpha)),
                                                skia.Color(255, 255, 255, 0), skia.Color(255, 255, 255, 0),
                                                hexc(C["b3"], 0.5 * alpha), hexc(C["b3"], 0), hexc(C["b3"], 0)],
                                       [0.0, 0.12, 0.30, 0.60, 0.72, 0.90, 1.0])
    canvas.drawCircle(cx, cy, s - r * 0.035, paint(shader=sw, stroke=r * 0.07))
    canvas.restore()
    canvas.drawCircle(cx, cy, s, paint(color=skia.Color(255, 255, 255, int(89 * alpha)), stroke=r * 0.02))
    if mark:
        h = s * 0.46 * 2
        w = h * 160 / 250
        canvas.save()
        canvas.translate(cx - w / 2, cy - h / 2)
        L.draw_mark(canvas, h, tone="white")
        canvas.restore()


def pill_row(canvas, x, y, w, u, title, sub, state, icon_name="ic3d_meds", alpha=1.0, chime=0.0):
    """A medication row: icon, name, dose and the "Qabul qildim" action that flips to done."""
    h = 76 * u
    clay_surface(canvas, x, y, w, h, 20 * u, u, alpha=alpha)
    icon(canvas, icon_name, x + 40 * u, y + h / 2, 44 * u, alpha=alpha)
    T.draw(canvas, "ui6", title, 17 * u, x + 74 * u, y + 32 * u, color=C["text"], alpha=alpha)
    T.draw(canvas, "ui", sub, 13 * u, x + 74 * u, y + 54 * u, color=C["muted"], alpha=alpha)
    return h
