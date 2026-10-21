"""
The film's clock: every beat of "The Path", derived from the voice-over's own words.

The picture and the sound both read their times from here, so a hit is drawn and heard on
the same frame. `beats(lang)` loads the anchors vo_edit.py wrote for that language's take
and returns one flat dict of named times in seconds; a scene asks for `B["water"]`, never
for 5.16. Nothing here draws.
"""
import json, os

FPS = 60
LENGTH = 25.2
FRAMES = int(round(LENGTH * FPS))  # 1512

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
VO = os.path.join(ROOT, "build", "ad", "vo")

# Brand.kt, to the millisecond. The hero reveal plays these exactly; the hook plays a
# compressed version in the same order and on the same curves.
DRAW_MS, DOT_DELAY_MS, DOT_MS = 1500, 1250, 600
LETTER_DELAY_MS, LETTER_STAGGER_MS, LETTER_MS = 1200, 80, 500
TAGLINE_DELAY_MS, TAGLINE_MS = 1900, 700
STAGGER = 0.055  # the app's 55 ms list stagger


def anchors(lang):
    with open(os.path.join(VO, f"{lang}_anchors.json")) as f:
        return json.load(f)


def beats(lang="uz"):
    a = anchors(lang)
    w = a["words"]
    ends = {b["name"]: b["end"] for b in a["blocks"]}
    order = [b["end"] for b in a["blocks"]]

    def end_after(t):
        """The end of the voice block that is speaking at time t."""
        return min(e for e in order if e >= t - 1e-6)

    B = dict(w)
    B["lang"] = lang

    # S1 · Hook. The dot slams on frame 10; the pen writes the S while the camera settles.
    B["slam"] = 10 / FPS
    B["s1_write"] = (0.30, 1.10)
    B["s1_letters"] = 0.95
    B["s1_letter_len"] = 0.40
    B["bloom"] = (0.60, 1.10)
    B["wave"] = (1.05, 1.95)
    B["s1_exit"] = 1.78

    # S2 · Cycle. The line dives through the lock-up and wraps into the 28-day ring as
    # the voice says "stages".
    B["dive"] = (1.80, 2.32)
    B["ring_wrap"] = (w["stages"] - 0.16, w["stages"] + 0.30)
    B["beads"] = (w["stages"] - 0.06, w["stages"] - 0.06 + 28 / FPS)
    B["fill"] = (B["beads"][1] - 0.08, B["beads"][1] + 0.34)
    B["today"] = B["fill"][1] + 0.02
    B["estimate"] = B["today"] + 0.12

    # S3 · Today. The phone rises out of the ring; three ticks land on three words.
    B["phone_rise"] = (w["daily"] - 0.30, w["daily"] + 0.28)
    B["greeting"] = w["daily"]
    B["ai_card"] = w["daily"] + 0.22
    B["push_water"] = w["water"] - 0.32
    B["push_meds"] = w["meds"] - 0.28
    B["push_mood"] = w["mood"] - 0.28

    # S4 · Streak & Gul. After the third tick the light leaps out and curls into the
    # flower; the drop is the music's downbeat under the bloom. The streak plays under
    # "Savol tugʻilsa —" and hands over to the chat as "Sadora tushuntiradi" begins.
    B["leap"] = w["mood"] + 0.24
    B["drop"] = B["leap"] + 0.10
    B["bloom_gul"] = (B["drop"], B["drop"] + 0.36)
    B["streak_card"] = B["drop"] + 0.08
    B["streak_roll"] = B["drop"] + 0.22
    B["week_fill"] = (B["streak_roll"], B["streak_roll"] + 7 * STAGGER)
    B["coins"] = [B["drop"] + 0.40 + i * 0.12 for i in range(3)]
    B["badge"] = B["drop"] + 0.78

    # S5 · Sadora AI. The camera arrives as "Sadora tushuntiradi" starts; the question
    # pops as it lands and the answer types on the rest of the line. The footnote is part
    # of the answer bubble, so it is on screen for as long as the answer is.
    B["glide_ai"] = (B["badge"] + 0.42, max(B["badge"] + 0.80, w["explains"]))
    B["question_lands"] = B["glide_ai"][1] - 0.14
    B["typing"] = (B["glide_ai"][1] + 0.10, end_after(w["explains"]) + 0.15)
    B["footnote"] = B["typing"][0] + 0.10

    # S6 · Doctor.
    B["thread"] = (B["typing"][1], w["serious"] + 0.22)
    B["doctor_card"] = w["serious"]
    B["verified"] = w["doctor"]
    B["window"] = w["serious"] + 0.30
    B["rx"] = w["doctor"] + 0.24
    B["press"] = B["rx"] + 0.40
    B["ride"] = (B["press"] + 0.10, B["press"] + 0.50)
    B["rx_lands"] = B["ride"][1]

    # S7 · Yaqinim.
    B["stretch"] = (B["rx_lands"], w["loved"] + 0.38)
    B["partner_view"] = w["loved"] + 0.28
    B["chip_fly"] = (w["guess"], w["guess"] + 0.46)
    B["reply"] = w["know"]

    # S8 · Every stage. The biggest move: a fast pull-back as the line splits in four.
    B["pullback"] = (w["know"] + 0.40, w["cycle"] + 0.30)
    B["split"] = w["cycle"] - 0.30
    B["stage_hits"] = [w["cycle"], w["pregnancy"], w["motherhood"], w["menopause"]]
    B["stages_hold"] = end_after(w["menopause"])

    # S9 · Type.
    B["converge"] = (B["stages_hold"] - 0.10, w["every_woman"] + 0.05)
    B["type1"] = w["every_woman"]
    B["type2"] = w["every_moment"]
    B["colour_split"] = (w["every_woman"] + 0.45, w["every_woman"] + 0.95)
    B["colour_merge"] = (w["every_moment"] + 0.30, a["words"]["hero"] - 0.10)

    # S10 · Hero: a breath, then the companions land on the hit and Brand.kt plays exact.
    B["breath"] = (end_after(w["every_moment"]), w["hero"])
    t0 = w["hero"]
    B["t0"] = t0
    B["mark_draw"] = (t0, t0 + DRAW_MS / 1000)
    B["dot_pop"] = (t0 + DOT_DELAY_MS / 1000, t0 + (DOT_DELAY_MS + DOT_MS) / 1000)
    B["letters"] = t0 + LETTER_DELAY_MS / 1000
    B["tagline"] = (t0 + TAGLINE_DELAY_MS / 1000, t0 + (TAGLINE_DELAY_MS + TAGLINE_MS) / 1000)
    B["sonic"] = t0 + DRAW_MS / 1000
    B["bounce"] = (t0 + 1.55, t0 + 2.45)

    # S11 · End card.
    B["signature"] = B["tagline"][1] + 0.05
    B["platforms"] = B["signature"] + 0.20
    B["fade_out"] = (LENGTH - 0.30, LENGTH)

    # Scene spans, for the camera and for anyone reading a frame number.
    B["scenes"] = {
        "S1": (0.0, B["dive"][0]),
        "S2": (B["dive"][0], B["phone_rise"][0]),
        "S3": (B["phone_rise"][0], B["leap"]),
        "S4": (B["leap"], B["glide_ai"][0]),
        "S5": (B["glide_ai"][0], B["thread"][0]),
        "S6": (B["thread"][0], B["stretch"][0]),
        "S7": (B["stretch"][0], B["pullback"][0]),
        "S8": (B["pullback"][0], B["converge"][0]),
        "S9": (B["converge"][0], B["breath"][0]),
        "S10": (B["breath"][0], B["signature"] - 0.3),
        "S11": (B["signature"] - 0.3, LENGTH),
    }
    return B


def frame(t):
    return int(round(t * FPS))


if __name__ == "__main__":
    import sys
    B = beats(sys.argv[1] if len(sys.argv) > 1 else "uz")
    for k, v in B.items():
        if k == "scenes":
            for s, (a, b) in v.items():
                print(f"  {s:4s} {a:6.2f}–{b:6.2f}  f{frame(a)}–f{frame(b) - 1}")
        elif isinstance(v, tuple):
            print(f"{k:16s} {v[0]:6.3f}–{v[1]:6.3f}  f{frame(v[0])}–f{frame(v[1])}")
        elif isinstance(v, list):
            print(f"{k:16s} " + " ".join(f"{x:.3f}" for x in v))
        elif isinstance(v, float):
            print(f"{k:16s} {v:6.3f}  f{frame(v)}")
