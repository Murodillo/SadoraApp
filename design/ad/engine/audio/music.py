"""
The score: 92 BPM, F major, felt piano + kalimba/harp + a round sub, doira/claps/shaker
from the drop. Every time comes from timeline.beats(); the bar grid is counted back from
B["drop"] so a downbeat lands exactly on it.

    intro   0 -> drop        sub from t=0, sub hit on the slam, piano chords, kalimba arps
    drop    drop -> lift     + doira (dum / tak / ka + jingles), claps on 2 & 4, swung 16th shaker
    lift    stage_hits[0]    riser + reversed B-flat swell into it, fuller voicing (pad, octaves,
                             harp gliss), four rising bell accents on the stage words
    breath  breath[0] -> t0  everything out but a reversed reverb swell of the end chord
    end     t0 -> 25.2       full F chord + low hit + kalimba sparkle; fades over fade_out

render(B) returns three stereo buses so the mix can duck them differently:
    bed   - everything the hero cues may push down
    hits  - the slam boom and the t0 chord/low hit (not ducked by their own hero cue)
    swell - the breath's reversed swell (ducked only by the loudness guarantee)
"""
import numpy as np
from scipy import signal

from instruments import clap, doira_dum, doira_tak, felt_piano, glass, harp, kalimba, pad_note, shaker_hit
from synth import (SR, Bus, band_noise, fade, hp, lp, make_ir, midi_hz, ns, reverb, reverb_full,
                   rng_for, saturate, sweep_sine, db, msum)

BPM = 92.0
BEAT = 60.0 / BPM
BAR = 4 * BEAT
S16 = BEAT / 4
SWING = 0.14          # odd 16ths sit 14 % of a 16th late

# Open, warm voicings (MIDI). Sub roots stay in 43-74 Hz.
VOICING = {
    "Fadd9":  [41, 48, 55, 57, 60],
    "Dm9":    [38, 45, 53, 60, 64],
    "Bbmaj7": [46, 53, 57, 62],
    "Csus4":  [36, 43, 53, 55, 60],
    "C":      [36, 43, 52, 55, 60],
    "Fmaj7":  [41, 48, 57, 64, 69],
    "Gm9":    [43, 50, 58, 65, 69],
    "Bbmaj9": [46, 53, 60, 62, 69],
    "C9sus4": [36, 46, 53, 55, 62],
    "C9":     [36, 46, 52, 55, 62],
    "Fend":   [41, 48, 53, 57, 60, 67, 69, 72],
}
SUB = {"Fadd9": 29, "Dm9": 38, "Bbmaj7": 34, "Csus4": 36, "C": 36, "Fmaj7": 29, "Gm9": 31,
       "Bbmaj9": 34, "C9sus4": 36, "C9": 36, "Fend": 29}
ARP = {
    "Fadd9":  [65, 69, 72, 76, 79],
    "Dm9":    [62, 65, 69, 72, 76],
    "Bbmaj7": [62, 65, 69, 72, 74],
    "Csus4":  [60, 65, 67, 72, 74],
    "C":      [60, 64, 67, 72, 74],
    "Fmaj7":  [65, 69, 72, 76, 79],
    "Gm9":    [62, 65, 67, 70, 74],
    "Bbmaj9": [62, 65, 69, 72, 74],
    "C9sus4": [60, 65, 67, 70, 74],
    "C9":     [60, 64, 67, 70, 74],
}
PAD = {"Bbmaj9": [58, 62, 65, 69], "C9sus4": [58, 62, 65, 67], "C9": [58, 62, 64, 67],
       "Fend": [53, 57, 60, 65, 67, 69]}
ACCENTS = [74, 77, 81, 84]       # D5 F5 A5 C6: rising over B-flat maj9 -> C9sus4
ACCENT_PANS = [-0.45, -0.15, 0.15, 0.45]

# Stem balance inside the music (dB). The mix sets the music level as a whole.
GAIN = dict(piano=1.0, kal=-9.0, harp=-1.0, sub=-16.0, dum=-4.0, tak=6.0, clap=-5.0,
            shaker=-7.0, pad=-14.0, bells=-14.0, riser=-20.0, rise_rev=-9.0,
            slam=0.0, t0_boom=-5.0, t0_piano=-1.0, t0_pad=-5.0, sparkle=-5.0, swell=-3.0)

HALL = make_ir(2.2, predelay=0.02, lo_mult=1.2, hi_mult=0.45, hi_cut=6000, seed=21)
HALL_LONG = make_ir(3.4, predelay=0.03, lo_mult=1.2, hi_mult=0.5, hi_cut=6000, seed=22)
ROOM = make_ir(0.45, predelay=0.006, lo_mult=0.9, hi_mult=0.6, hi_cut=7000, er_gain=0.5, seed=23)


def chord_plan(B):
    """[(name, start, end)] for the whole cue, on a bar grid whose downbeat is B["drop"]."""
    bar = lambda k: B["drop"] + k * BAR
    lift, br0 = B["stage_hits"][0], B["breath"][0]
    assert bar(-2) > B["slam"] + 0.5, "intro too short for the two-bar lead-in"
    assert bar(2) < lift - BEAT, "lift arrives before the third bar after the drop"
    plan = [("Fadd9", 0.0, bar(-2)), ("Dm9", bar(-2), bar(-1)),
            ("Bbmaj7", bar(-1), bar(-1) + 2 * BEAT), ("Csus4", bar(-1) + 2 * BEAT, bar(-1) + 3 * BEAT),
            ("C", bar(-1) + 3 * BEAT, bar(0)),
            ("Fmaj7", bar(0), bar(1)), ("Dm9", bar(1), bar(2)), ("Gm9", bar(2), lift)]
    k = 0
    while bar(k) < lift + 1.5 * BEAT:
        k += 1
    c9 = bar(k + 1) if bar(k + 1) < br0 - BEAT else br0 - 2 * BEAT
    plan += [("Bbmaj9", lift, bar(k)), ("C9sus4", bar(k), c9), ("C9", c9, br0)]
    return plan


def _chord_at(plan, t):
    for name, a, b in plan:
        if a - 1e-6 <= t < b:
            return name, a, b
    return plan[-1]


def _step_time(B, s):
    return B["drop"] + s * S16 + (SWING * S16 if s % 2 else 0.0)


def _steps(B, t_from, t_to):
    s0 = int(np.ceil((t_from - B["drop"]) / S16))
    s1 = int(np.floor((t_to - B["drop"]) / S16))
    return [s for s in range(s0, s1 + 1) if t_from <= _step_time(B, s) < t_to]


def _chain(x, lp_hz=None, hp_hz=None, ir=None, send_db=-12.0, dry=1.0):
    y = x
    if hp_hz:
        y = hp(y, hp_hz, 2)
    if lp_hz:
        y = lp(y, lp_hz, 2)
    out = dry * y
    if ir is not None:
        out = out + reverb(y, ir, send_db)
    return out


def _reversed_tail(dry, ir, length, skip=0.04, shape=1.3):
    """Reverse the wet tail of `dry` (stereo) -> a swell that peaks at its last sample."""
    wet = reverb_full(dry, ir)
    seg = wet[ns(skip):ns(skip) + ns(length)][::-1].copy()
    u = np.linspace(0, 1, len(seg))[:, None]
    seg *= u ** shape
    seg = fade(seg, 0.01, 0.004)
    return seg / (np.max(np.abs(seg)) + 1e-12)


def _render_chord(name, vel, hold, notes=None):
    notes = notes or VOICING[name]
    n = ns(hold + 0.6)
    x = np.zeros((n, 2))
    for m in notes:
        s = felt_piano(m, hold, vel)
        x[:len(s)] += s[:n]
    return x


def render(B, length):
    rng = rng_for("music", 1)
    plan = chord_plan(B)
    bar = lambda k: B["drop"] + k * BAR
    drop, lift, br0, t0 = B["drop"], B["stage_hits"][0], B["breath"][0], B["t0"]
    end = B["fade_out"][1]
    riser_zone = (lift - 0.75, lift)
    in_zone = lambda t: riser_zone[0] <= t < riser_zone[1]

    piano, kal, hrp, padb, bells = (Bus(length) for _ in range(5))
    dum, tak, claps, shk, rise = (Bus(length) for _ in range(5))
    hits_piano, hits_dry, hits_pad, hits_kal = (Bus(length) for _ in range(4))
    swell = Bus(length)

    # ---------------- felt piano strikes: (t, chord, voice-index set, vel, extra notes)
    strikes = []
    for name, a, b in plan:
        if a < drop - 1e-6:
            v = 0.34 if name in ("Csus4", "C") else 0.42
            strikes.append((max(a, B["slam"]), name, set(range(len(VOICING[name]))), v, []))
    for s in _steps(B, drop, br0 - 0.05):
        t = _step_time(B, s)
        ss = s % 16
        name, a, b = _chord_at(plan, t)
        nv = len(VOICING[name])
        upper = set(range(nv - 3, nv))
        if in_zone(t) or (lift <= t < lift + 0.3):
            continue
        if ss == 0:
            strikes.append((t, name, set(range(nv)), 0.55 if t >= lift else 0.5, []))
        elif ss == 6:
            strikes.append((t, name, upper, 0.30, []))
        elif ss == 12 and t >= lift:
            strikes.append((t, name, upper, 0.28, [VOICING[name][-1] + 12]))
    # the lift lands off the grid, on the first stage word: fuller, with octaves
    v = VOICING["Bbmaj9"]
    strikes.append((lift, "Bbmaj9", set(range(len(v))), 0.62, [v[0] - 12, v[-1] + 12]))
    strikes.sort(key=lambda x: x[0])
    for i, (t, name, voices, vel, extra) in enumerate(strikes):
        _, a, b = _chord_at(plan, t + 1e-4)
        notes = VOICING[name]
        for vi in voices:
            nxt = next((s[0] for s in strikes[i + 1:] if vi in s[2]), b)
            hold = min(nxt, b) - t + 0.05
            jit = rng.normal(0, 0.004) if vi > 0 else 0.0
            piano.add(felt_piano(notes[vi], hold, vel * rng.uniform(0.94, 1.04)), t + jit)
        for m in extra:
            nxt = next((s[0] for s in strikes[i + 1:] if len(s[2]) == len(VOICING[s[1]])), b)
            piano.add(felt_piano(m, min(nxt, b) - t + 0.05, vel * 0.8), t)

    # ---------------- kalimba arpeggio (3-3-2 feel), from just after the slam
    KAL = {0: 0.55, 3: 0.42, 6: 0.46, 8: 0.40, 11: 0.50, 14: 0.42}
    KAL_LIFT = {10: 0.36, 15: 0.34}
    order = [0, 2, 4, 1, 3, 2, 4, 1]
    idx = 0
    for s in _steps(B, B["slam"] + 0.3, br0 - 0.12):
        t = _step_time(B, s)
        ss = s % 16
        vel = KAL.get(ss) or (KAL_LIFT.get(ss) if t >= lift else None)
        if vel is None or in_zone(t):
            continue
        name, _, _ = _chord_at(plan, t)
        tones = ARP[name]
        m = tones[order[idx % len(order)] % len(tones)]
        kal.add(kalimba(m, vel + rng.uniform(-0.05, 0.05)), t + rng.normal(0, 0.003), 0.0,
                -0.3 if idx % 2 else 0.3)
        idx += 1

    # ---------------- harp: answers from the drop, a gliss at the lift
    for s in _steps(B, drop, br0 - 0.12):
        t = _step_time(B, s)
        ss = s % 16
        steps = (2, 6, 10, 14) if t >= lift else (2, 10)
        if ss not in steps or in_zone(t) or (lift <= t < lift + 0.35):
            continue
        name, _, _ = _chord_at(plan, t)
        m = ARP[name][-1 if (s // 8) % 2 else -2]
        hrp.add(harp(m, 0.34 if t >= lift else 0.30), t, 0.0, 0.4 if ss in (2, 10) else -0.4)
    gl = [58, 62, 65, 69, 72, 74, 77, 81]
    for i, m in enumerate(gl):
        hrp.add(harp(m, 0.28 + 0.035 * i), lift + i * 0.038, 0.0, -0.5 + i / 7)

    # ---------------- doira, claps, shaker (drop -> breath), plus a pickup into the drop
    DUM = {0: 1.0, 7: 0.55, 10: 0.8}
    TAK = {3: 0.50, 6: 0.65, 11: 0.45, 14: 0.70}
    KA = {1: 0.22, 5: 0.25, 9: 0.20, 13: 0.25, 15: 0.30}
    SHK = [0.32, 0.20, 0.55, 0.24]
    for s, kind, vel in ((-3, "ka", 0.2), (-2, "tak", 0.32), (-1, "tak", 0.48)):
        t = _step_time(B, s)
        tak.add(doira_tak(vel, rng_for("pick", s), sizzle=0.35 if kind == "ka" else 0.5), t, 0.0, 0.15)
    for s in _steps(B, drop, br0 - 0.06):
        t = _step_time(B, s)
        ss = s % 16
        up = t >= lift
        z = in_zone(t)
        r = rng_for("drum", s)
        if not z:
            if ss in DUM or (up and ss == 8):
                dum.add(doira_dum(DUM.get(ss, 0.45), r), t, 0.0, 0.0)
            if ss in TAK:
                tak.add(doira_tak(TAK[ss] * (1.12 if up else 1.0), r), t + r.normal(0, 0.002), 0.0, 0.15)
            if ss in KA:
                tak.add(doira_tak(KA[ss], r, sizzle=0.3), t + r.normal(0, 0.002), -4.0, -0.2)
            if ss in (4, 12):
                claps.add(clap(0.65 if up else 0.55, r), t, 0.0, 0.0)
        u = (t - riser_zone[0]) / 0.75 if z else 0.0
        shk.add(shaker_hit(SHK[ss % 4] * (1.15 if up else 1.0) * (1 + 0.6 * u) * r.uniform(0.9, 1.1), r),
                t + r.normal(0, 0.002), 0.0, 0.35)
    dum.add(doira_dum(1.0, rng_for("liftdum")), lift, 0.0, 0.0)

    # ---------------- lift: riser + reversed swell into stage_hits[0], pad, bell accents
    rl = 1.5
    for ch_seed, p in ((1, -0.6), (2, 0.6)):
        r = rng_for("riser", ch_seed)
        nz = band_noise(rl, lambda u: 350 * (4200 / 350) ** (u ** 1.5), lambda u: 1.6 - 0.9 * u, r)
        u = np.linspace(0, 1, len(nz))
        rise.add(nz * u ** 2.6, lift - rl, 0.0, p)
    bb = _render_chord("Bbmaj9", 0.55, 1.2)
    rise.add(_reversed_tail(bb, HALL_LONG, rl) * db(GAIN["rise_rev"] - GAIN["riser"]), lift - rl)
    for name, a, b in plan:
        if a >= lift - 1e-6:
            for m in PAD[name]:
                padb.add(pad_note(m, b - a, att=0.15 if a == lift else 0.3, rel=0.35, vel=0.5), a)
    for i, t in enumerate(B["stage_hits"]):
        m = ACCENTS[i]
        f = float(midi_hz(m))
        r = rng_for("accent", i)
        x = msum(glass(f, 0.8, r, decay=0.8), 0.8 * harp(m, 0.6, r))
        bells.add(x, t, 0.0, ACCENT_PANS[i])
        bells.add(pad_note(m - 12, 0.25, att=0.08, rel=0.7, vel=0.45), t, -6.0)

    # ---------------- the sub: from frame 0, glides between roots, out at the breath
    N = ns(length)
    tt = np.arange(N) / SR
    f = np.full(N, float(midi_hz(29)))
    for name, a, b in plan:
        f[ns(a):ns(b)] = float(midi_hz(SUB[name]))
    p = np.exp(-1 / (0.04 * SR))
    lf = signal.lfilter([1 - p], [1, -p], np.log(f), zi=[np.log(f[0]) * p])[0]
    amp = np.where(tt < B["slam"], 0.15 + 0.35 * (tt / B["slam"]) ** 2, 0.5)
    amp = np.where(tt >= drop, 0.55, amp)
    amp = np.where(tt >= lift, 0.62, amp)
    for name, a, b in plan[1:]:
        amp *= 1 - 0.4 * np.exp(-((tt - a + 0.02) / 0.04) ** 2)
    amp *= np.clip((br0 - tt) / 0.06, 0, 1)
    pa = np.exp(-1 / (0.015 * SR))
    amp = signal.lfilter([1 - pa], [1, -pa], amp, zi=[amp[0] * pa])[0]
    sub = saturate(amp * np.sin(2 * np.pi * np.cumsum(np.exp(lf)) / SR), 1.8)
    sub = fade(sub, 0.003, 0.003)

    # ---------------- hits: the slam boom and the t0 chord
    boom = saturate(msum(sweep_sine(1.8, 110, 43.65, 0.045, 0.45), 0.25 * sweep_sine(1.0, 220, 87.3, 0.045, 0.25)), 1.4)
    air = band_noise(0.5, lambda u: 320 * (130 / 320) ** u, 1.2, rng_for("slamair"))
    air *= np.exp(-np.arange(len(air)) / SR / 0.12) * 0.12
    hits_dry.add(boom * db(GAIN["slam"]), B["slam"])
    hits_dry.add(air, B["slam"], GAIN["slam"])
    ring = end - t0
    t0boom = saturate(sweep_sine(min(4.0, ring), 92, 43.65, 0.06, 1.25), 1.3)
    hits_dry.add(t0boom * db(GAIN["t0_boom"]), t0)
    tail_sub = np.sin(2 * np.pi * float(midi_hz(29)) * np.arange(ns(ring)) / SR) * 0.22
    hits_dry.add(fade(tail_sub, 0.25, 0.3) * db(GAIN["t0_boom"]), t0 + 0.05)
    for m in (29, 41):
        hits_piano.add(felt_piano(m, ring, 0.6), t0)
    for m in VOICING["Fend"]:
        hits_piano.add(felt_piano(m, ring, 0.58), t0 + rng.normal(0, 0.003) * (m > 41))
    for m in PAD["Fend"]:
        hits_pad.add(pad_note(m, ring, att=0.06, rel=0.05, vel=0.5), t0)
    for dt, m, v, pp in ((0.16, 84, 0.50, 0.3), (0.34, 79, 0.42, -0.3), (0.58, 89, 0.38, 0.4), (0.90, 81, 0.30, -0.2)):
        hits_kal.add(kalimba(m, v, decay=1.4), t0 + dt, 0.0, pp)

    # ---------------- the breath: reversed reverb tail of the end chord, peaking at t0
    sl = min(1.3, t0 - 0.2)
    fe = _render_chord("Fend", 0.6, 1.5)
    for m in PAD["Fend"]:
        pn = pad_note(m, 1.5, att=0.02, rel=0.3, vel=0.5)
        fe[:len(pn)] += pn[:len(fe)]
    swell.add(_reversed_tail(fe, HALL_LONG, sl, shape=4.0), t0 - sl)

    # ---------------- stem chains
    g = GAIN
    stems = {
        "piano": _chain(piano.x, 3400, 90, HALL, -9) * db(g["piano"]),
        "kal": _chain(kal.x, 7000, 150, HALL, -8) * db(g["kal"]),
        "harp": _chain(hrp.x, 7000, 120, HALL, -8) * db(g["harp"]),
        "pad": _chain(padb.x, 5000, 100, HALL, -6) * db(g["pad"]),
        "bells": _chain(bells.x, 7000, 150, HALL, -5) * db(g["bells"]),
        "dum": _chain(dum.x, 6000, 35, ROOM, -12) * db(g["dum"]),
        "tak": _chain(tak.x, 7800, 200, ROOM, -10) * db(g["tak"]),
        "clap": _chain(claps.x, 6000, 300, ROOM, -2) * db(g["clap"]),
        "shaker": _chain(shk.x, 8000, 3000, ROOM, -14) * db(g["shaker"]),
        "riser": rise.x * db(g["riser"]),
        "sub": np.stack([sub, sub], 1) * db(g["sub"]),
    }
    bed = sum(stems.values())
    gate = np.clip((br0 - tt) / 0.10, 0, 1)
    gate = 0.5 - 0.5 * np.cos(np.pi * gate)
    bed *= gate[:, None]

    hits = (hits_dry.x
            + _chain(hits_piano.x, 3600, 30, HALL, -8) * db(g["t0_piano"])
            + _chain(hits_pad.x, 5000, 100, HALL, -6) * db(g["t0_pad"])
            + _chain(hits_kal.x, 7000, 150, HALL, -6) * db(g["sparkle"]))
    sw = swell.x * db(g["swell"])

    fo = np.clip((end - tt) / (end - B["fade_out"][0]), 0, 1)
    fo = (0.5 - 0.5 * np.cos(np.pi * fo))[:, None]
    return {"bed": bed * fo, "hits": hits * fo, "swell": sw * fo, "plan": plan, "stems": stems}
