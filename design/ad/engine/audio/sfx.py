"""
Every visible move gets a sound. Each voice below is synthesised from scratch (noise
shaped in the STFT domain, modal partials, swept sines); `cue_sheet(B)` lays them on the
picture's beats and `render(B)` returns the two SFX buses plus the cue list:

    duck - ordinary cues; the mix pulls these 6 dB down under words
    hero - the chime, sonic logo, slam, t0 landing and heart pop; never ducked

A cue is (name, beat, t, voice, gain dB, pan, hero). `t` is the picture time the sound is
locked to; `voice()` returns (signal, sync offset), so a whoosh that must END on a beat
starts early by its own length. All gains are peak dBFS of the event on the SFX bus.
"""
import numpy as np

import chime as chime_mod
from instruments import felt_piano, glass, harp, kalimba, pluck_glide
from synth import (SR, TAU, Bus, attack_env, band_noise, bell_env, bp, db, fade, glide_sine,
                   lp, make_ir, midi_hz, modal, msum, ns, pan, peak_norm, reverb_full, rng_for,
                   smoothstep, stereo, sweep_sine, white, hp)

ROOM = make_ir(0.6, predelay=0.008, lo_mult=0.9, hi_mult=0.55, hi_cut=6500, seed=31)
AIR = make_ir(1.1, predelay=0.015, lo_mult=1.0, hi_mult=0.5, hi_cut=6000, seed=32)


def _verb(x, ir=ROOM, wet_db=-12.0):
    w = reverb_full(x, ir, wet_db)
    return msum(stereo(x), w)


def _norm(x):
    return peak_norm(x, 1.0)


# ---------------------------------------------------------------- air and motion
def whoosh(dur, f_lo, f_hi, rng, peak=0.55, bw=(1.4, 0.9), body=0.0, rise_only=False,
           pans=None, width=0.25, end_f=None, a=2.2, b=1.6):
    """Moving band-pass noise. fc climbs f_lo -> f_hi at the peak, then falls (doppler)."""
    end_f = end_f or f_lo * 1.3
    if rise_only:
        fc = lambda u: f_lo * (f_hi / f_lo) ** (u ** 1.3)
    else:
        fc = lambda u: np.where(u < peak, f_lo * (f_hi / f_lo) ** smoothstep(u / peak),
                                f_hi * (end_f / f_hi) ** smoothstep((u - peak) / (1 - peak)))
    bwf = lambda u: bw[0] + (bw[1] - bw[0]) * u
    n = ns(dur)
    env = bell_env(n, peak, a, b) if not rise_only else np.linspace(0, 1, n) ** 2.2
    m = band_noise(dur, fc, bwf, rng)
    s = band_noise(dur, fc, bwf, rng)
    if body > 0:
        m = m + body * band_noise(dur, 170, 1.4, rng) * env ** 0.5
    p = np.zeros(n) if pans is None else np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(pans)), pans)
    out = pan(m * env, p)
    out[:, 0] += width * s * env * 0.7
    out[:, 1] -= width * s * env * 0.7
    return _norm(out)


def air_push(rng, dur=0.34):
    """A small camera push: soft, broad, low-mid air."""
    return whoosh(dur, 450, 1500, rng, peak=0.6, bw=(1.9, 1.6), width=0.3), 0.0


def rise_to(rng, dur, f_lo=350, f_hi=3000):
    """Rising air that ENDS on its beat."""
    x = whoosh(dur, f_lo, f_hi, rng, rise_only=True, bw=(1.6, 1.0), width=0.3)
    return fade(x, 0.003, 0.004), dur


def swish(rng, dur, f_lo=900, f_hi=3600, pans=None, peak=0.45):
    return whoosh(dur, f_lo, f_hi, rng, peak=peak, bw=(1.1, 0.7), pans=pans, width=0.15), 0.0


def big_whoosh(rng, dur, pans=None):
    return whoosh(dur, 220, 2400, rng, peak=0.4, bw=(1.8, 1.1), body=0.6, pans=pans, width=0.55,
                  end_f=260, a=1.8, b=1.3), 0.0


def shockwave(rng, dur=0.5):
    """An expanding ring of air from the slam: fast swell, band opening downward, widening."""
    n = ns(dur)
    u = np.linspace(0, 1, n)
    fc = lambda u: 2600 * (650 / 2600) ** (u ** 0.7)
    bw = lambda u: 0.9 + 1.6 * u
    m = band_noise(dur, fc, bw, rng)
    s = band_noise(dur, fc, bw, rng)
    env = (1 - np.exp(-u * dur / 0.018)) * np.exp(-u * dur / 0.16)
    out = np.stack([m + u * s, m - u * s], 1) * env[:, None]
    ring = glass(float(midi_hz(89)), 0.25, rng, decay=0.18)[:n]
    out[:len(ring)] += stereo(ring) * 0.15
    return _norm(out), 0.0


def thoom(rng):
    """Soft round impact under the slam: body sweep + low felt thump."""
    body = sweep_sine(0.7, 165, 56, 0.03, 0.17, attack=0.002)
    n = ns(0.7)
    thump = lp(white(n, rng), 450, 2) * np.exp(-np.arange(n) / SR / 0.02)
    mid = bp(white(n, rng), 120, 420) * np.exp(-np.arange(n) / SR / 0.035)
    x = body + 0.10 * thump / (np.max(np.abs(thump)) + 1e-9) + 0.12 * mid / (np.max(np.abs(mid)) + 1e-9)
    return _norm(_verb(x, ROOM, -14)), 0.0


def pen_stroke(rng, dur):
    """Felt-tip on paper: narrow noise whose level and brightness follow the stroke speed."""
    n = ns(dur)
    u = np.linspace(0, 1, n)
    speed = (0.3 + 0.7 * np.abs(np.sin(3 * np.pi * u)) ** 0.7) * smoothstep(u / 0.08) * smoothstep((1 - u) / 0.12)
    fl = lp(white(n, rng), 30, 2)
    fl = 1 + 0.35 * fl / (np.max(np.abs(fl)) + 1e-9)
    hi = band_noise(dur, lambda v: 2400 + 900 * np.interp(v, u, speed), 0.9, rng)
    lo = band_noise(dur, 900, 1.2, rng)
    x = (hi + 0.35 * lo) * speed * fl
    return _norm(pan(x, np.interp(u, [0, 0.5, 1], [-0.12, 0.1, -0.05]))), 0.0


# ---------------------------------------------------------------- small hits
def tick(rng, f=2000.0, body=0.3):
    """A soft clay tick: three short modes, a felted click, a little low body."""
    d = 0.08
    x = modal([f, 2.32 * f, 4.1 * f], [1, 0.3, 0.1], [0.012, 0.006, 0.003], d, attack=0.0006, rng=rng)
    nn = ns(0.004)
    x[:nn] += 0.25 * bp(white(nn, rng), 2000, 6000) * np.exp(-np.arange(nn) / SR / 0.0008)
    x += body * sweep_sine(d, f / 2.6, f / 3.2, 0.004, 0.008, attack=0.0008)
    return _norm(x), 0.0


def wood_tick(rng, f=2300.0):
    x = modal([f, 1.73 * f, 2.9 * f, f / 2.1], [1, 0.5, 0.25, 0.35], [0.02, 0.012, 0.006, 0.015], 0.12,
              attack=0.0006, rng=rng)
    return _norm(_verb(x, ROOM, -16)), 0.0


def tap(rng, f=380.0):
    x = sweep_sine(0.14, f * 1.5, f, 0.006, 0.03, attack=0.0015)
    nn = ns(0.005)
    x[:nn] += 0.15 * lp(white(nn, rng), 4000) * np.exp(-np.arange(nn) / SR / 0.001)
    return _norm(x), 0.0


def pop(rng, f0=350.0, f1=720.0, tau=0.05, glide=0.025, verb=-16):
    x = glide_sine(0.3, f0, f1, glide, tau, attack=0.002)
    x += 0.18 * glide_sine(0.3, 2 * f0, 2 * f1, glide, tau * 0.6, attack=0.002)
    return _norm(_verb(x, ROOM, verb)), 0.0


def bloop(rng, f0=500.0, f1=760.0):
    """A warm glowing bloop (today's bead)."""
    x = glide_sine(0.7, f0, f1, 0.06, 0.16, attack=0.008)
    x += 0.22 * glide_sine(0.7, 2 * f0, 2 * f1, 0.06, 0.1, attack=0.008)
    x = msum(x, 0.12 * glass(2 * f1, 0.6, rng, decay=0.3))
    return _norm(_verb(x, AIR, -10)), 0.0


def boop(rng, f0=480.0, f1=720.0):
    x = glide_sine(0.22, f0, f1, 0.05, 0.06, attack=0.004)
    return _norm(_verb(x, ROOM, -14)), 0.0


def thup(rng, f=110.0):
    """A companion landing: soft low thud."""
    x = sweep_sine(0.35, f * 1.8, f, 0.012, 0.07, attack=0.0015)
    n = len(x)
    x += 0.3 * lp(white(n, rng), 400) * np.exp(-np.arange(n) / SR / 0.012) / 3
    return _norm(x), 0.0


def blip(rng, f=1320.0):
    x = glide_sine(0.14, f, f * 1.12, 0.02, 0.03, attack=0.003)
    return _norm(_verb(x, ROOM, -14)), 0.0


def key_tap(rng):
    k = rng.uniform(0.88, 1.12)
    n = ns(0.05)
    t = np.arange(n) / SR
    click = bp(white(n, rng), 1500 * k, 4500 * k) * np.exp(-t / 0.0012)
    thock = sweep_sine(0.05, 300 * k, 220 * k, 0.004, 0.012, attack=0.0008)
    return _norm(click / (np.max(np.abs(click)) + 1e-9) * 0.6 + thock * 0.8), 0.0


def card_slide(rng, dur=0.24):
    x = whoosh(dur, 1100, 2200, rng, peak=0.6, bw=(1.4, 1.2), width=0.1)
    t, _ = tap(rng, 300)
    return _norm(msum(x, np.concatenate([np.zeros((ns(dur - 0.03), 2)), stereo(t) * 0.35]))), 0.0


def card_land(rng):
    x = sweep_sine(0.28, 220, 120, 0.01, 0.05, attack=0.0015)
    n = len(x)
    x += 0.4 * lp(white(n, rng), 1000) * np.exp(-np.arange(n) / SR / 0.008) / 3
    tk, _ = tick(rng, 3000, 0.0)
    x = msum(x, 0.1 * tk)
    return _norm(_verb(x, ROOM, -16)), 0.0


def tick_ding(rng):
    tk, _ = tick(rng, 2400, 0.2)
    d = glass(1760.0, 1.0, rng, decay=0.22, attack=0.002)
    gap = np.zeros(ns(0.055))
    x = msum(0.7 * tk, np.concatenate([gap, 0.5 * d]))
    return _norm(_verb(x, ROOM, -12)), 0.0


def bloom_pluck(rng):
    """A petal unfolding: a soft pluck bending up a minor third, with a breath of air."""
    x = pluck_glide(float(midi_hz(69)), float(midi_hz(72)), 0.8, rng, glide=0.09, T=0.5)
    a = whoosh(0.3, 1400, 3200, rng, peak=0.5, bw=(1.2, 1.0), width=0.2)
    return _norm(_verb(msum(stereo(x), 0.12 * a), AIR, -12)), 0.0


def clay_pop(rng, f=300.0):
    x = glide_sine(0.25, f, f * 1.75, 0.03, 0.045, attack=0.002)
    x += 0.5 * sweep_sine(0.25, f * 0.6, f * 0.5, 0.01, 0.03, attack=0.002)
    return _norm(_verb(x, ROOM, -16)), 0.0


def bright_pluck(rng, m):
    return _norm(_verb(kalimba(m, 0.7, rng), AIR, -12)), 0.0


def dot_pop(rng):
    """The dot: a round pop with a small overshoot bounce 90 ms later."""
    a, _ = pop(rng, 330, 700, 0.06, 0.025, -40)
    b, _ = pop(rng, 420, 620, 0.04, 0.02, -40)
    x = msum(mono_(a), np.concatenate([np.zeros(ns(0.09)), 0.38 * mono_(b)]))
    return _norm(_verb(x, ROOM, -14)), 0.0


def mono_(x):
    return x if x.ndim == 1 else x.mean(1)


def heart_pop(rng):
    """Warm, round, satisfying: a rising pop over a falling body, a glassy tink, a soft room."""
    p = glide_sine(0.4, 420, 880, 0.03, 0.07, attack=0.002)
    body = glide_sine(0.5, 230, 165, 0.09, 0.15, attack=0.003)
    tink = glass(1760.0, 0.6, rng, decay=0.15, attack=0.002)
    x = msum(0.8 * p, 0.9 * body, 0.12 * tink)
    return _norm(_verb(x, AIR, -11)), 0.0


def sparkle(rng, n=5, f_lo=2400, f_hi=5200, gap=0.03):
    out = np.zeros((ns(0.6 + gap * n), 2))
    for i in range(n):
        f = f_lo * (f_hi / f_lo) ** (i / max(n - 1, 1)) * rng.uniform(0.97, 1.03)
        g = modal([f, 2.0 * f], [1, 0.25], [rng.uniform(0.07, 0.14), 0.04], 0.5, attack=0.002, rng=rng)
        s = pan(g * (1 - 0.1 * i), rng.uniform(-0.6, 0.6))
        i0 = ns(i * gap * rng.uniform(0.85, 1.15))
        out[i0:i0 + len(s)] += s[:len(out) - i0]
    return _norm(_verb(out, AIR, -10)), 0.0


def shimmer(rng, dur, f_lo=1800, f_hi=5000, density=26, sweep="up", width=0.7):
    """Soft glassy grains + a breath of high air, swept across `dur`."""
    n = ns(dur)
    out = np.zeros((n + ns(0.5), 2))
    env = bell_env(n, 0.4, 1.6, 1.4)
    k = int(max(4, density * dur))
    us = np.sort(rng.uniform(0, 1, k))
    for u in us:
        v = u if sweep == "up" else (1 - u if sweep == "down" else rng.uniform(0, 1))
        f = f_lo * (f_hi / f_lo) ** v * rng.uniform(0.97, 1.03)
        g = modal([f, 1.5 * f], [1, 0.2], [rng.uniform(0.06, 0.14), 0.03], 0.45, attack=0.006, rng=rng)
        a = float(np.interp(u, np.linspace(0, 1, n), env))
        s = pan(g * a, rng.uniform(-width, width))
        i0 = ns(u * dur)
        out[i0:i0 + len(s)] += s[:len(out) - i0]
    air = band_noise(dur, lambda u: 3500 * (6000 / 3500) ** u, 1.0, rng) * env * 0.08
    out[:n, 0] += air
    out[:n, 1] += air[::-1] * 0.9
    return _norm(_verb(out, AIR, -10)), 0.0


def split_shimmer(rng):
    """The line splitting four ways: four small shimmers fanning out to four positions."""
    out = np.zeros((ns(1.2), 2))
    for i, p in enumerate((-0.75, -0.25, 0.25, 0.75)):
        x, _ = shimmer(rng, 0.42, 1500 * 1.12 ** i, 3800 * 1.12 ** i, 22, "up", 0.08)
        x = x.mean(1)
        s = pan(x, p)
        i0 = ns(0.03 * i)
        m = min(len(s), len(out) - i0)
        out[i0:i0 + m] += s[:m]
    return _norm(out), 0.0


def type_whoosh(rng, dur=0.42):
    """Letters rising: a rising band of air with four tiny letter ticks inside."""
    x = whoosh(dur, 600, 3200, rng, peak=0.75, bw=(1.3, 0.9), width=0.2)
    for i in range(4):
        tk, _ = tick(rng, 1900 + 250 * i, 0.1)
        i0 = ns(0.08 + i * 0.06)
        x[i0:i0 + len(tk)] += stereo(tk)[:len(x) - i0] * 0.25
    return _norm(x), 0.0


# ---------------------------------------------------------------- objects
def water_pour(rng, dur=0.45):
    """Granular bubbles (rising-chirp resonances) over a fluttering pour."""
    n = ns(dur)
    out = np.zeros(n + ns(0.06))
    env = bell_env(n, 0.3, 1.2, 1.5)
    for _ in range(70):
        u = rng.uniform(0, 1)
        f0 = 350 * (1400 / 350) ** rng.uniform(0, 1)
        tb = rng.uniform(0.008, 0.03)
        L = ns(tb * 5)
        t = np.arange(L) / SR
        f = f0 * (1 + 1.2 * t / tb)
        b = np.sin(TAU * np.cumsum(np.minimum(f, 6000)) / SR) * np.exp(-t / tb) * attack_env(L, 0.001)
        i0 = ns(u * dur)
        m = min(L, len(out) - i0)
        out[i0:i0 + m] += b[:m] * float(np.interp(u, np.linspace(0, 1, n), env)) * rng.uniform(0.3, 1.0)
    pour = band_noise(dur, lambda u: 900 + 400 * u, 1.6, rng)
    fl = lp(white(n, rng), 18, 2)
    fl = 1 + 0.6 * fl / (np.max(np.abs(fl)) + 1e-9)
    out[:n] += 0.35 * pour * fl * env / 3.0
    out[:ns(0.12)] += 0.25 * glide_sine(0.12, 300, 500, 0.05, 0.03, attack=0.003)
    st = np.stack([out, np.concatenate([out[ns(0.0015):], np.zeros(ns(0.0015))])], 1)
    return _norm(_verb(st, ROOM, -14)), 0.0


def paper_flip(rng):
    """A page turning: fast bright sweep, crinkle grains, a soft flap at the end."""
    dur = 0.24
    x = whoosh(dur, 1500, 4500, rng, peak=0.35, bw=(1.2, 1.0), end_f=2500, width=0.25)
    n = len(x)
    cr = np.zeros(n)
    for _ in range(40):
        i0 = ns(rng.uniform(0.0, 0.16))
        L = ns(0.006)
        g = bp(white(L, rng), 2000, 6000) * np.exp(-np.arange(L) / SR / rng.uniform(0.0008, 0.002))
        m = min(L, n - i0)
        cr[i0:i0 + m] += g[:m] * rng.uniform(0.2, 1.0)
    flap = lp(white(ns(0.04), rng), 600, 2) * np.exp(-np.arange(ns(0.04)) / SR / 0.012)
    cr = cr / (np.max(np.abs(cr)) + 1e-9) * 0.35
    i0 = ns(0.16)
    cr[i0:i0 + len(flap)] += 0.5 * flap / (np.max(np.abs(flap)) + 1e-9)
    return _norm(x + pan(cr, 0.05)), 0.0


def coin(rng, f):
    """A small bright coin landing: inharmonic ring with a beating pair, a tiny click."""
    r = [1.0, 1.0 + 2.5 / f, 2.32, 3.96, 5.6]
    a = [1.0, 0.6, 0.5, 0.3, 0.12]
    tau = [0.42, 0.38, 0.26, 0.16, 0.09]
    x = modal([f * k for k in r], a, tau, 1.2, attack=0.0008, rng=rng)
    tk, _ = tick(rng, 3200, 0.0)
    x = msum(x, 0.25 * tk)
    return _norm(_verb(x, AIR, -12)), 0.0


def badge_flip(rng):
    """Whoosh-flip (a spinning card) then a warm bronze 'shing'."""
    w = whoosh(0.2, 700, 2600, rng, peak=0.5, bw=(1.2, 0.9), width=0.3)
    n = len(w)
    w *= (0.65 + 0.35 * np.sin(TAU * 15 * np.arange(n) / SR))[:, None]
    f = 520.0
    sh = modal([f * k for k in (1, 1.004, 2.08, 2.92, 4.1, 5.3, 6.6)], [1, 0.7, 0.7, 0.5, 0.35, 0.2, 0.1],
               [0.9, 0.85, 0.7, 0.5, 0.35, 0.25, 0.15], 1.6, attack=0.003, rng=rng)
    hiss = band_noise(0.3, 4500, 1.0, rng) * np.exp(-np.arange(ns(0.3)) / SR / 0.06) * 0.08
    sh[:len(hiss)] += hiss
    x = msum(0.6 * w, np.concatenate([np.zeros((ns(0.12), 2)), pan(sh, 0.1)]))
    return _norm(_verb(x, AIR, -10)), 0.0


def chime_voice(rng):
    return chime_mod.chime(), 0.0


def sonic_voice(rng):
    return chime_mod.sonic_logo(), 0.0


# ---------------------------------------------------------------- the cue sheet
def cue_sheet(B):
    C = []
    bead_dt = (B["beads"][1] - B["beads"][0]) / 28          # one bead per frame
    week_dt = (B["week_fill"][1] - B["week_fill"][0]) / 7     # Du..Ya, the app's list stagger

    def add(name, beat, t, voice, gain, pan_pos=0.0, hero=False):
        C.append(dict(name=name, beat=beat, t=float(t), voice=voice, gain_db=float(gain),
                      pan=float(pan_pos), hero=hero))

    # S1 - hook
    add("strands_rise", "slam", B["slam"], lambda r: rise_to(r, B["slam"], 500, 3200), -20)
    add("slam_thoom", "slam", B["slam"], thoom, -6, 0, True)
    add("slam_shockwave", "slam", B["slam"], lambda r: shockwave(r, 0.5), -15, 0, True)
    w0, w1 = B["s1_write"]
    add("pen_s1", "s1_write[0]", w0, lambda r: pen_stroke(r, w1 - w0), -24)
    for i in range(6):
        add(f"s1_letter_{i + 1}", "s1_letters", B["s1_letters"] + 0.08 * i,
            lambda r, i=i: tick(r, 1900 + 120 * i, 0.25), -23, -0.25 + 0.1 * i)
    add("bloom", "bloom[0]", B["bloom"][0], bloom_pluck, -17, 0.1)
    add("wave_blip", "wave[0]", B["wave"][0], lambda r: blip(r, 1320), -24, 0.15)
    add("s1_exit", "s1_exit", B["s1_exit"], lambda r: swish(r, 0.42, 700, 2600, [0.0, 0.0, -0.1], 0.5), -18)

    # S2 - cycle
    d0, d1 = B["dive"]
    add("dive", "dive[0]", d0, lambda r: big_whoosh(r, d1 - d0 + 0.35), -15)
    r0, r1 = B["ring_wrap"]
    add("ring_wrap", "ring_wrap[0]", r0,
        lambda r: swish(r, r1 - r0 + 0.1, 800, 3000, [-0.7, 0.0, 0.7, 0.0, -0.7], 0.5), -19)
    for i in range(0, 28, 2):
        add(f"bead_{i + 1:02d}", "beads[0]", B["beads"][0] + i * bead_dt,
            lambda r, i=i: tick(r, 1500 * (2600 / 1500) ** (i / 26), 0.35), -25,
            0.6 * np.sin(TAU * i / 28))
    f0, f1 = B["fill"]
    add("fill_shimmer", "fill[0]", f0, lambda r: shimmer(r, f1 - f0 + 0.1, 1600, 4200, 30, "up"), -21)
    add("today_bloop", "today", B["today"], bloop, -17)
    add("estimate_tap", "estimate", B["estimate"], lambda r: tap(r, 520), -24, 0.15)

    # S3 - today
    p0, p1 = B["phone_rise"]
    add("phone_rise", "phone_rise[0]", p0,
        lambda r: (whoosh(p1 - p0, 300, 1800, r, peak=0.65, bw=(1.6, 1.2), width=0.3), 0.0), -19)
    add("greeting_pop", "greeting", B["greeting"], lambda r: pop(r, 520, 900, 0.04, 0.02), -22, 0.1)
    add("ai_card_slide", "ai_card", B["ai_card"], card_slide, -21, 0.1)
    add("push_water", "push_water", B["push_water"], air_push, -23)
    add("water_pour", "water", B["water"], water_pour, -15, -0.1)
    add("water_250ml_pop", "water", B["water"] + 0.1, lambda r: pop(r, 600, 1050, 0.035, 0.018), -21, 0.15)
    add("push_meds", "push_meds", B["push_meds"], air_push, -23)
    add("meds_paper_flip", "meds", B["meds"], paper_flip, -19, 0.05)
    add("meds_chime", "meds", B["meds"], chime_voice, -13, 0, True)
    add("push_mood", "push_mood", B["push_mood"], air_push, -23)
    add("mood_tick", "mood", B["mood"] - 0.06, lambda r: tick(r, 2100, 0.2), -24, 0.1)
    add("mood_pop", "mood", B["mood"], lambda r: pop(r, 300, 560, 0.07, 0.03), -16, 0.1)

    # S4 - streak & Gul (the music drops on B["drop"])
    add("leap", "leap", B["leap"], lambda r: swish(r, 0.36, 600, 3200, [0.0, 0.2], 0.55), -18)
    g0, g1 = B["bloom_gul"]
    add("gul_curl", "bloom_gul[0]", g0, lambda r: swish(r, g1 - g0 + 0.1, 900, 2800, [-0.3, 0.3, -0.1], 0.5), -21)
    add("gul_bloom", "bloom_gul[1]", g1, bloom_pluck, -18, 0.05)
    add("streak_card", "streak_card", B["streak_card"], card_slide, -22, -0.1)
    for i in range(3):
        add(f"streak_roll_{i + 1}", "streak_roll", B["streak_roll"] + 0.05 * i,
            lambda r, i=i: tick(r, 2800 + 200 * i, 0.0), -27, -0.15)
    for i in range(7):
        add(f"week_{i + 1}", "week_fill[0]", B["week_fill"][0] + i * week_dt,
            lambda r, i=i: tick(r, 1400 * (1.06 ** i), 0.4), -25, -0.45 + 0.15 * i)
    for i, t in enumerate(B["coins"]):
        add(f"coin_{i + 1}", f"coins[{i}]", t, lambda r, i=i: coin(r, (1568.0, 1760.0, 2093.0)[i]), -19,
            (-0.3, 0.0, 0.3)[i])
    add("badge_flip", "badge", B["badge"], badge_flip, -17, 0.05)

    # S5 - Sadora AI
    add("glide_ai", "glide_ai[0]", B["glide_ai"][0], lambda r: air_push(r, 0.36), -22)
    add("question_pop", "question_lands", B["question_lands"], lambda r: pop(r, 380, 760, 0.05, 0.025), -18, -0.15)
    a, b = B["typing"]
    r = rng_for("typing_times")
    k = max(3, int(round((b - a) * 14)))
    for i in range(k):
        t = a if i == 0 else float(np.clip(a + i * (b - a) / k + r.normal(0, 0.008), a, b - 0.02))
        add(f"typing_{i + 1:02d}", "typing[0]", t, key_tap, -26 + r.uniform(-2, 1), 0.2 + r.uniform(-0.1, 0.1))
    add("footnote_shimmer", "footnote", B["footnote"], lambda r: shimmer(r, 0.35, 2600, 4800, 18, "flat", 0.4), -26)

    # S6 - doctor
    t0_, t1_ = B["thread"]
    add("thread_swish", "thread[0]", t0_, lambda r: swish(r, t1_ - t0_, 1400, 4200, [-0.2, 0.2], 0.4), -22)
    add("doctor_card_land", "doctor_card", B["doctor_card"], card_land, -18)
    add("verified_tick_ding", "verified", B["verified"], tick_ding, -19, 0.2)
    for i in range(2):
        add(f"window_clock_{i + 1}", "window", B["window"] + 0.3 * i,
            lambda r, i=i: wood_tick(r, 2300 if i == 0 else 1900), -22, 0.25)
    add("rx_swish", "rx", B["rx"], lambda r: swish(r, 0.4, 800, 2600, [0.3, 0.0], 0.5), -19)
    add("press_tap", "press", B["press"], lambda r: tap(r, 440), -19)
    q0, q1 = B["ride"]
    add("ride_swoosh", "ride[0]", q0, lambda r: swish(r, q1 - q0 + 0.06, 700, 2400, [0.0, -0.15], 0.6), -19)
    add("rx_land", "rx_lands", B["rx_lands"], card_land, -19)
    add("rx_chime", "rx_lands", B["rx_lands"], chime_voice, -13, 0, True)

    # S7 - Yaqinim
    s0, s1 = B["stretch"]
    add("stretch", "stretch[0]", s0, lambda r: (whoosh(s1 - s0 + 0.3, 400, 2400, r, peak=0.55, bw=(1.5, 1.0),
                                                        pans=[-0.85, 0.85], width=0.2), 0.0), -19)
    add("partner_view_pop", "partner_view", B["partner_view"], lambda r: pop(r, 460, 820, 0.04, 0.02), -21, 0.2)
    c0, c1 = B["chip_fly"]
    add("chip_fly", "chip_fly[0]", c0, lambda r: swish(r, c1 - c0, 900, 3200, [-0.7, 0.7], 0.6), -19)
    add("chip_land", "chip_fly[1]", c1, lambda r: tap(r, 520), -20, 0.7)
    add("heart_pop", "reply", B["reply"], heart_pop, -11, 0, True)
    add("heart_sparkle", "reply", B["reply"] + 0.08, lambda r: sparkle(r, 5, 2400, 5000, 0.03), -24)

    # S8 - every stage (the biggest move)
    u0, u1 = B["pullback"]
    add("pullback", "pullback[0]", u0, lambda r: big_whoosh(r, u1 - u0 + 0.55, [0.0, 0.0]), -12)
    add("split_shimmer", "split", B["split"], split_shimmer, -20)
    for i, t in enumerate(B["stage_hits"]):
        p = (-0.45, -0.15, 0.15, 0.45)[i]
        m = (74, 77, 81, 84)[i] + 12
        add(f"stage_{i + 1}_pop", f"stage_hits[{i}]", t, lambda r, i=i: clay_pop(r, 280 + 30 * i), -17, p)
        add(f"stage_{i + 1}_pluck", f"stage_hits[{i}]", t, lambda r, m=m: bright_pluck(r, m), -20, p)
        add(f"stage_{i + 1}_hop", f"stage_hits[{i}]", t + 0.12,
            lambda r, i=i: boop(r, 500 * 1.06 ** i, 760 * 1.06 ** i), -21, p)

    # S9 - type
    v0, v1 = B["converge"]
    add("converge", "converge[0]", v0, lambda r: swish(r, v1 - v0 + 0.1, 2400, 900, [-0.5, 0.5, 0.0], 0.6), -19)
    add("type1_whoosh", "type1", B["type1"], type_whoosh, -20)
    add("type2_whoosh", "type2", B["type2"], type_whoosh, -20)
    cs0, cs1 = B["colour_split"]
    add("colour_split", "colour_split[0]", cs0, lambda r: shimmer(r, cs1 - cs0, 1800, 4400, 24, "up"), -23)
    cm0 = B["colour_merge"][0]
    cm_len = min(B["colour_merge"][1] - cm0, B["breath"][0] - cm0 - 0.12)
    add("colour_merge", "colour_merge[0]", cm0, lambda r: shimmer(r, cm_len, 4400, 1800, 24, "down"), -23)

    # S10 - hero (B["breath"] is silent here: only the music's reversed swell plays)
    t0 = B["t0"]
    for i, (dt, p) in enumerate(zip((-0.012, 0.0, 0.010, 0.022, 0.036), (0.0, -0.6, 0.6, -0.3, 0.3))):
        add(f"t0_land_{i + 1}", "t0", t0 + dt, lambda r, i=i: thup(r, 105 + 9 * i), -12, p, True)
    m0, m1 = B["mark_draw"]
    add("pen_mark", "mark_draw[0]", m0, lambda r: pen_stroke(r, m1 - m0), -25)
    add("dot_pop", "dot_pop[0]", B["dot_pop"][0], dot_pop, -13)
    for i in range(6):
        add(f"letter_{i + 1}", "letters", B["letters"] + 0.08 * i,
            lambda r, i=i: tick(r, 1900 + 120 * i, 0.25), -23, -0.25 + 0.1 * i)
    add("sonic_logo", "sonic", B["sonic"], sonic_voice, -9, 0, True)
    bb0, bb1 = B["bounce"]
    for i, t in enumerate((bb0, bb0 + (bb1 - bb0) / 2)):
        add(f"bounce_{i + 1}", "bounce[0]", t, lambda r, i=i: boop(r, 420 + 40 * i, 640 + 50 * i), -21)

    # S11 - end card
    add("signature_shimmer", "signature", B["signature"], lambda r: shimmer(r, 0.6, 2000, 4200, 16, "up", 0.5), -24)
    return [c for c in C if c is not None]


def render(B, length):
    duck, hero = Bus(length), Bus(length)
    cues = []
    for c in cue_sheet(B):
        sig, off = c["voice"](rng_for(c["name"]))
        start = c["t"] - off
        bus = hero if c["hero"] else duck
        a, b = bus.add(sig, start, c["gain_db"], c["pan"])
        end = start + len(sig) / SR
        cues.append(dict(t=round(c["t"], 4), name=c["name"], beat=c["beat"], gain_db=round(c["gain_db"], 2),
                         pan=round(c["pan"], 2), hero=c["hero"], start=round(start, 4),
                         end=round(min(end, length), 4), tail_faded_at_end=bool(end > length)))
    # the breath: nothing from the SFX between breath[0] and the t0 landing
    tt = np.arange(duck.n) / SR
    br0, t0 = B["breath"][0], B["t0"]
    g = np.ones(duck.n)
    out_ = (tt > br0 - 0.08) & (tt < t0 - 0.02)
    g[out_] = 0.0
    ramp = (tt > br0 - 0.08) & (tt <= br0)
    g[ramp] = 0.5 + 0.5 * np.cos(np.pi * (tt[ramp] - (br0 - 0.08)) / 0.08)
    g[out_ & ~ramp] = 0.0
    up = (tt >= t0 - 0.02) & (tt < t0 - 0.015)
    g[up] = 0.5 - 0.5 * np.cos(np.pi * (tt[up] - (t0 - 0.02)) / 0.005)
    end = B["fade_out"]
    fo = np.clip((end[1] - tt) / (end[1] - end[0]), 0, 1)
    g *= 0.5 - 0.5 * np.cos(np.pi * fo)
    return {"duck": duck.x * g[:, None], "hero": hero.x * g[:, None], "cues": cues}
