"""
Type: Bricolage Grotesque for display lines, Figtree for every UI string.

Text is shaped with HarfBuzz (kerning, the variable axes) and drawn glyph by glyph with
Skia, so a line can be tracked, gradient-filled, wrapped, or animated one letter at a
time. Characters a face lacks (emoji, Cyrillic) fall back to the system's faces.
"""
import functools, os
import numpy as np
import skia
import uharfbuzz as hb

from core import FONTS, clamp, hexc

FACES = {
    "display": ("BricolageGrotesque.ttf", {"wght": 700, "opsz": 96, "wdth": 100}),
    "display8": ("BricolageGrotesque.ttf", {"wght": 800, "opsz": 96, "wdth": 100}),
    "ui": ("Figtree.ttf", {"wght": 400}),
    "ui5": ("Figtree.ttf", {"wght": 500}),
    "ui6": ("Figtree.ttf", {"wght": 600}),
    "ui7": ("Figtree.ttf", {"wght": 700}),
}
FALLBACKS = ["Apple Color Emoji", "Helvetica Neue", "Arial Unicode MS"]


def _tag(s):
    return (ord(s[0]) << 24) | (ord(s[1]) << 16) | (ord(s[2]) << 8) | ord(s[3])


class Face:
    def __init__(self, file, axes):
        path = os.path.join(FONTS, file) if not os.path.isabs(file) else file
        base = skia.Typeface.MakeFromFile(path)
        if axes:
            VP = skia.FontArguments.VariationPosition
            coords = VP.Coordinates([VP.Coordinate(_tag(k), float(v)) for k, v in axes.items()])
            fa = skia.FontArguments()
            fa.setVariationDesignPosition(VP(coords))
            self.tf = base.makeClone(fa)
        else:
            self.tf = base
        blob = hb.Blob.from_file_path(path)
        self.hbface = hb.Face(blob)
        self.hbfont = hb.Font(self.hbface)
        if axes:
            self.hbfont.set_variations({k: float(v) for k, v in axes.items()})
        self.upem = self.hbface.upem
        self.cmap = set(self.hbface.unicodes)

    def has(self, ch):
        return ord(ch) in self.cmap


@functools.lru_cache(maxsize=None)
def face(name):
    if name in FACES:
        return Face(*FACES[name])
    raise KeyError(name)


@functools.lru_cache(maxsize=None)
def system_tf(family):
    return skia.FontMgr().matchFamilyStyle(family, skia.FontStyle.Normal())


def _fallback_for(ch):
    for fam in FALLBACKS:
        tf = system_tf(fam)
        if tf is not None and tf.unicharToGlyph(ord(ch)):
            return tf
    return None


class Glyph:
    __slots__ = ("ch", "x", "adv", "tf", "gid", "index")

    def __init__(self, ch, x, adv, tf, gid, index):
        self.ch, self.x, self.adv, self.tf, self.gid, self.index = ch, x, adv, tf, gid, index


@functools.lru_cache(maxsize=4096)
def shape(fname, text, size, track_em=0.0):
    """Glyph positions for `text` at `size` px; tracking in em. Returns (glyphs, width)."""
    f = face(fname)
    glyphs, x = [], 0.0
    track = track_em * size
    # Split into runs the face can render and runs that need a fallback.
    runs, cur, cur_fb = [], "", None
    for ch in text:
        fb = None if (f.has(ch) or ch in "  ") else "fb"
        if cur and fb != cur_fb:
            runs.append((cur, cur_fb)); cur = ""
        cur += ch; cur_fb = fb
    if cur:
        runs.append((cur, cur_fb))
    index = 0
    for run, fb in runs:
        if fb is None:
            buf = hb.Buffer()
            buf.add_str(run)
            buf.guess_segment_properties()
            hb.shape(f.hbfont, buf, {"liga": False, "kern": True})
            scale = size / f.upem
            for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
                ch = run[info.cluster] if info.cluster < len(run) else ""
                adv = pos.x_advance * scale
                glyphs.append(Glyph(ch, x + pos.x_offset * scale, adv, f.tf, info.codepoint, index + info.cluster))
                x += adv + track
            index += len(run)
        else:
            for ch in run:
                tf = _fallback_for(ch)
                if tf is None:
                    index += 1
                    continue
                font = skia.Font(tf, size * (0.92 if tf.getFamilyName() == "Apple Color Emoji" else 1.0))
                gid = tf.unicharToGlyph(ord(ch))
                adv = font.getWidths([gid])[0]
                glyphs.append(Glyph(ch, x, adv, tf, gid, index))
                x += adv + track
                index += 1
    width = x - (track if glyphs else 0)
    return tuple(glyphs), width


def width(fname, text, size, track_em=0.0):
    return shape(fname, text, size, track_em)[1]


def metrics(fname, size):
    m = skia.Font(face(fname).tf, size).getMetrics()
    return -m.fAscent, m.fDescent, m.fCapHeight


def draw(canvas, fname, text, size, x, y, color="#F3F0FA", alpha=1.0, track=0.0, align="left",
         shader=None, letter=None, upto=None, paint=None):
    """
    Draws a single line with its baseline at y. `align` is left / center / right about x.
    `letter(i, glyph) -> (dx, dy, alpha, scale)` animates each letter; `upto` draws only the
    first `upto` characters (typing). Returns the line width.
    """
    glyphs, w = shape(fname, text, size, track)
    if align == "center":
        x -= w / 2
    elif align == "right":
        x -= w
    p = paint or skia.Paint(AntiAlias=True)
    if paint is None:
        if shader is not None:
            p.setShader(shader)
        else:
            p.setColor(hexc(color) if isinstance(color, str) else color)
    base_alpha = p.getAlphaf() * clamp(alpha)
    fonts = {}
    for g in glyphs:
        if upto is not None and g.index >= upto:
            break
        if g.ch in (" ", " "):
            continue
        key = id(g.tf)
        if key not in fonts:
            fonts[key] = skia.Font(g.tf, size * (0.92 if g.tf.getFamilyName() == "Apple Color Emoji" else 1.0))
            fonts[key].setSubpixel(True)
            fonts[key].setEdging(skia.Font.Edging.kAntiAlias)
        font = fonts[key]
        dx = dy = 0.0
        a, sc = 1.0, 1.0
        if letter is not None:
            dx, dy, a, sc = letter(g.index, g)
            if a <= 0:
                continue
        q = skia.Paint(p)
        q.setAlphaf(clamp(base_alpha * a))
        gx, gy = x + g.x + dx, y + dy
        if sc != 1.0:
            canvas.save()
            canvas.translate(gx + g.adv / 2, gy)
            canvas.scale(sc, sc)
            canvas.translate(-(gx + g.adv / 2), -gy)
        blob = skia.TextBlob.MakeFromPosText(g.ch, [skia.Point(gx, gy)], font) if g.tf.getFamilyName() == "Apple Color Emoji" or not g.gid else None
        if blob is None:
            builder = skia.TextBlobBuilder()
            builder.allocRunPos(font, [g.gid], [skia.Point(gx, gy)])
            blob = builder.make()
        canvas.drawTextBlob(blob, 0, 0, q)
        if sc != 1.0:
            canvas.restore()
    return w


def wrap(fname, text, size, max_w, track=0.0):
    """Greedy word wrap; returns a list of (line, start_index)."""
    words, lines, cur, start, i = text.split(" "), [], "", 0, 0
    for word in words:
        trial = (cur + " " + word) if cur else word
        if cur and width(fname, trial, size, track) > max_w:
            lines.append((cur, start))
            start = i
            cur = word
        else:
            cur = trial
        i += len(word) + 1
    if cur:
        lines.append((cur, start))
    return lines


def draw_block(canvas, fname, text, size, x, y, max_w, leading=1.35, color="#F3F0FA", alpha=1.0,
               upto=None, track=0.0, spans=None):
    """
    A wrapped paragraph, first baseline at y. `upto` types it on character by character.
    `spans` = [(start, end, color, fname)] recolours or re-weights a substring.
    Returns (height, caret_xy) — the caret is where the next character would go.
    """
    lines = wrap(fname, text, size, max_w, track)
    caret = (x, y)
    for n, (line, start) in enumerate(lines):
        by = y + n * size * leading
        if upto is not None and start >= upto:
            break
        k = None if upto is None else max(0, upto - start)
        if spans:
            # Draw segment by segment so a span can change colour or weight.
            cx = x
            cuts = sorted({0, len(line)} | {clamp(s - start, 0, len(line)) for s, e, *_ in spans}
                          | {clamp(e - start, 0, len(line)) for s, e, *_ in spans})
            cuts = [int(c) for c in cuts]
            for a, b in zip(cuts, cuts[1:]):
                seg = line[a:b]
                col, fn = color, fname
                for s, e, c, f in spans:
                    if s - start <= a and b <= e - start:
                        col, fn = c, f
                lim = None if k is None else max(0, min(len(seg), k - a))
                if lim == 0 and k is not None and k <= a:
                    break
                draw(canvas, fn, seg, size, cx, by, color=col, alpha=alpha, track=track, upto=lim)
                cx += width(fn, seg, size, track) if lim is None or lim >= len(seg) else width(fn, seg[:lim], size, track)
                caret = (cx, by)
        else:
            draw(canvas, fname, line, size, x, by, color=color, alpha=alpha, track=track, upto=k)
            shown = line if k is None else line[:k]
            caret = (x + width(fname, shown, size, track), by)
    return len(lines) * size * leading, caret
