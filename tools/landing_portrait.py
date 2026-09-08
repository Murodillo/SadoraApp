"""The landing page's line-art portrait, from anchor points.

The woman in the "Nega shunday" section is a handful of smooth strokes: the face in
one pull from the hairline to the far shoulder, the nape, three sweeps of hair, the
hairline along the temple, then the brow, the closed eye and two lashes. Each stroke
is a Catmull-Rom spline through the anchors below, converted to cubic Béziers, so the
line is smooth everywhere and a feature is tuned by moving one point.

Regenerate the SVG block and paste it over the <svg class="figure portrait-svg"> in
landing/index.html:

    python3 tools/landing_portrait.py > /tmp/portrait.svg

The stroke order and the data-dur / data-delay attributes are the drawing choreography
the page plays when the portrait scrolls into view; drawFigure() in the page reads them.
"""


def catmull(points, tension=0.5):
    """Catmull-Rom through `points` as an SVG cubic Bézier path."""
    p = points
    n = len(p)
    d = "M%.1f,%.1f" % p[0]
    for i in range(n - 1):
        p0 = p[i - 1] if i > 0 else p[0]
        p1 = p[i]
        p2 = p[i + 1]
        p3 = p[i + 2] if i + 2 < n else p[n - 1]
        c1 = (p1[0] + (p2[0] - p0[0]) * tension / 3, p1[1] + (p2[1] - p0[1]) * tension / 3)
        c2 = (p2[0] - (p3[0] - p1[0]) * tension / 3, p2[1] - (p3[1] - p1[1]) * tension / 3)
        d += " C%.1f,%.1f %.1f,%.1f %.1f,%.1f" % (c1 + c2 + p2)
    return d


# ---- viewBox 0 0 300 400, face turned to the right ----------------------

# The face: hairline, forehead, brow, nasion, nose, lips, chin, neck, near shoulder.
FACE = [
    (170, 64), (186, 78), (199, 96), (206, 116), (207, 132),
    (203, 143),
    (206, 154), (212, 164), (219, 173), (221, 180),
    (216, 185), (209, 187),
    (208, 195), (213, 201), (209, 205), (213, 212), (207, 219),
    (211, 230), (203, 241), (186, 247),
    (178, 266), (176, 290), (188, 313), (226, 336), (262, 348),
]

# The back of the neck into the far shoulder.
NAPE = [(150, 252), (147, 280), (140, 304), (118, 328), (84, 344), (46, 352)]

# Hair: the outer sweep, an inner sweep, a middle strand, and the hairline at the temple.
HAIR_OUT = [(170, 64), (150, 44), (120, 40), (92, 58), (72, 96), (66, 144), (72, 194), (86, 238), (92, 284), (84, 330), (72, 370), (58, 390)]
HAIR_IN = [(174, 70), (154, 58), (128, 64), (104, 92), (94, 138), (98, 186), (110, 230), (116, 278), (108, 328), (96, 366)]
HAIR_MID = [(160, 92), (140, 112), (128, 150), (128, 196), (134, 236), (140, 276), (134, 320)]
TEMPLE = [(170, 64), (165, 90), (160, 118), (158, 148), (156, 178), (160, 200)]

# Closed eye, two lashes, brow.
EYE = [(178, 149), (188, 153), (199, 149)]
LASH1 = [(198, 147), (204, 152)]
LASH2 = [(194, 152), (199, 158)]
BROW = [(172, 130), (186, 122), (203, 126)]

PARTS = {
    "face": catmull(FACE, 0.55),
    "nape": catmull(NAPE),
    "hairOut": catmull(HAIR_OUT),
    "hairIn": catmull(HAIR_IN),
    "hairMid": catmull(HAIR_MID),
    "temple": catmull(TEMPLE),
    "eye": catmull(EYE, 0.6),
    "lash1": catmull(LASH1),
    "lash2": catmull(LASH2),
    "brow": catmull(BROW, 0.6),
}

# Stroke order and timing (ms): the face first in one long pull, the nape, the hair
# sweeps, the temple line, then the brow, the eye and the lashes.
STROKES = [
    # part      class   width  dur   delay  extra attributes
    ("face",    "ln",   "2.4", 2400, 0,     ""),
    ("nape",    "ln",   "2.4", 1100, 900,   ""),
    ("hairOut", "thin", "1.9", 1800, 1300,  ' opacity=".9"'),
    ("hairIn",  "thin", "1.6", 1600, 1650,  ' opacity=".75"'),
    ("hairMid", "thin", "1.3", 1300, 2000,  ' opacity=".55"'),
    ("temple",  "thin", "1.3", 900,  2300,  ' opacity=".6"'),
    ("brow",    "ln",   "1.8", 500,  2700,  ""),
    ("eye",     "ln",   "1.8", 500,  2900,  ""),
    ("lash1",   "ln",   "1.4", 250,  3250,  ""),
    ("lash2",   "ln",   "1.4", 250,  3350,  ""),
]

# Blooms along the outer sweep of hair: x, y, scale, colour token, pop order.
BLOOMS = [
    (126, 44, ".9", "var(--secondary)", 0),
    (70, 122, "1.35", "var(--primary)", 1),
    (76, 198, "1.05", "var(--secondary)", 2),
    (90, 264, ".85", "var(--accent)", 3),
    (80, 334, ".7", "var(--secondary)", 4),
]


def bloom(x, y, scale, colour, order):
    # The placement transform sits on an outer <g>: the pop animation writes
    # `transform` on .bl and would otherwise wipe it out.
    return ('        <g transform="translate(%d,%d) scale(%s)">'
            '<g class="bl" style="--i:%d; color:%s"><use href="#bloomArt"/></g></g>'
            % (x, y, scale, order, colour))


def svg():
    lines = "\n".join(
        '        <path class="%s" style="stroke-width:%s" data-dur="%d" data-delay="%d"%s d="%s"/>'
        % (cls, w, dur, delay, extra, PARTS[part])
        for part, cls, w, dur, delay, extra in STROKES
    )
    blooms = "\n".join(bloom(*b) for b in BLOOMS)
    return '''<svg class="figure portrait-svg" id="portrait" viewBox="0 0 300 400" data-replay="1" role="img" aria-label="Sochiga gul taqqan ayolning bir chiziqda chizilgan profili">
        <defs>
          <!-- Tovlanuvchi gradient: ranglar brend palitrasi bo'ylab aylanadi, gradient o'zi sekin buriladi -->
          <linearGradient id="portraitGrad" gradientUnits="userSpaceOnUse" x1="40" y1="30" x2="260" y2="380">
            <stop offset="0" stop-color="#B06CFF"><animate attributeName="stop-color" values="#B06CFF;#63D8FF;#F68CCB;#8A8CFF;#B06CFF" dur="9s" repeatCount="indefinite"/></stop>
            <stop offset=".5" stop-color="#8A8CFF"><animate attributeName="stop-color" values="#8A8CFF;#B06CFF;#63D8FF;#F68CCB;#8A8CFF" dur="9s" repeatCount="indefinite"/></stop>
            <stop offset="1" stop-color="#F68CCB"><animate attributeName="stop-color" values="#F68CCB;#8A8CFF;#B06CFF;#63D8FF;#F68CCB" dur="9s" repeatCount="indefinite"/></stop>
            <animateTransform attributeName="gradientTransform" type="rotate" from="0 150 200" to="360 150 200" dur="16s" repeatCount="indefinite"/>
          </linearGradient>
          <radialGradient id="penGlow"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset=".35" stop-color="#E7D6FF" stop-opacity=".7"/><stop offset="1" stop-color="#B06CFF" stop-opacity="0"/></radialGradient>
        </defs>
%s
%s
        <!-- Qalam uchi: yuz chizig'i bo'ylab chiziq bilan birga yuradi -->
        <circle class="pen" r="9" fill="url(#penGlow)" style="offset-path: path('%s')"/>
      </svg>''' % (lines, blooms, PARTS["face"])


if __name__ == "__main__":
    print(svg())
