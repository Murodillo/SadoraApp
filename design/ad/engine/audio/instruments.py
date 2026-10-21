"""
The instruments: each returns one note or hit as a float array (mono, or stereo (n, 2)).
Levels are relative (about 1.0 peak at vel 1); the music and SFX scores set the mix.
"""
import numpy as np

from synth import (SR, TAU, attack_env, bp, hf_weight, lp, midi_hz, modal, ns, pan, peq,
                   rng_for, sweep_sine, white, hp)


# ---------------------------------------------------------------- felt piano
def felt_piano(m, hold, vel=0.5, rng=None, release=0.45, pan_pos=-0.18, double=True):
    """
    A muted felt piano: soft hammer (slow 4-10 ms rise, few highs), two slightly beating
    strings per partial, two-stage decay, warm partial roll-off, a quiet felt thump, a
    damper release after `hold`, and a detuned double (+4 cents, 4 ms late) on the other side.
    """
    f0 = float(midi_hz(m))
    rng = rng or rng_for(f"piano{m}", int(vel * 100))
    n = ns(hold + release + 0.02)
    t = np.arange(n) / SR
    inh = 0.00011 * (f0 / 100) ** 0.5
    nmax = int(max(3, min(16, 5200 / f0)))
    soft = 0.62 - 0.38 * vel
    t_slow = float(np.clip(6.5 * (110 / f0) ** 0.6, 1.0, 9.0))
    sig = np.zeros(n)
    dbl = np.zeros(n)
    for k in range(1, nmax + 1):
        fk = k * f0 * np.sqrt(1 + inh * k * k)
        ak = k ** -0.9 * np.exp(-(k - 1) * soft) * float(hf_weight(fk))
        if k == 1 and f0 < 90:
            ak *= 0.65
        tf = 0.22 / (1 + 0.3 * (k - 1))
        ts = t_slow / (1 + 0.33 * (k - 1))
        env = 0.5 * np.exp(-t / tf) + 0.5 * np.exp(-t / ts)
        beat = 2 ** (rng.uniform(0.3, 1.0) / 1200)
        p1, p2, p3 = rng.uniform(0, TAU, 3)
        sig += ak * env * (np.sin(TAU * fk * t + p1) + 0.8 * np.sin(TAU * fk * beat * t + p2))
        if double and k <= 6:
            dbl += ak * env * np.sin(TAU * fk * 2 ** (4 / 1200) * t + p3)
    att = attack_env(n, 0.010 - 0.006 * vel)
    sig *= att
    d = ns(0.004)
    dbl = np.concatenate([np.zeros(d), dbl[:n - d]]) * att
    rel = np.ones(n)
    k0 = ns(hold)
    if k0 < n:
        rel[k0:] = np.exp(-(t[k0:] - hold) / (release / 5))
    sig *= rel
    dbl *= rel
    nt = ns(0.03)
    thump = lp(white(nt, rng), 700) * np.exp(-np.arange(nt) / SR / 0.008)
    sig[:nt] += 0.08 * vel * thump
    g = vel ** 1.5 / 3.0
    return (pan(sig, pan_pos) + pan(0.45 * dbl, -pan_pos)) * g


# ---------------------------------------------------------------- plucks
def harp(m, vel=0.5, rng=None):
    """Modal harp string: harmonic partials shaped by the pluck point, bright fast attack."""
    f0 = float(midi_hz(m))
    rng = rng or rng_for(f"harp{m}", int(vel * 100))
    t1 = float(np.clip(2.6 * (220 / f0) ** 0.5, 0.5, 4.0))
    nmax = int(max(2, min(14, 7500 / f0)))
    ks = np.arange(1, nmax + 1)
    freqs = ks * f0 * np.sqrt(1 + 0.00008 * ks ** 2)
    amps = np.abs(np.sin(ks * np.pi * 0.12)) / ks ** 0.8 * np.exp(-(ks - 1) * (0.25 - 0.12 * vel))
    taus = t1 / (1 + 0.5 * (ks - 1)) ** 1.15
    s = modal(freqs, amps, taus, min(t1 * 4, 5.0), attack=0.0012, rng=rng)
    nn = ns(0.008)
    s[:nn] += 0.05 * bp(white(nn, rng), 1500, 5000) * np.exp(-np.arange(nn) / SR / 0.0015)
    return s * vel ** 1.4 * 0.6


def kalimba(m, vel=0.5, rng=None, decay=1.0):
    """Kalimba tine: fundamental + the tine's bright ~6x and ~11.6x modes, fast decay."""
    f0 = float(midi_hz(m))
    rng = rng or rng_for(f"kal{m}", int(vel * 100))
    T = float(np.clip(1.3 * (523 / f0) ** 0.35, 0.5, 2.2)) * decay
    freqs = [f0, 2.0 * f0, 5.95 * f0, 11.6 * f0]
    amps = [1.0, 0.05, 0.20 * (0.5 + vel) * min(1.0, (6000 / (5.95 * f0)) ** 2), 0.04]
    taus = [T, T * 0.3, 0.07, 0.025]
    s = modal(freqs, amps, taus, T * 5, attack=0.0008, rng=rng)
    nn = ns(0.004)
    s[:nn] += 0.03 * lp(white(nn, rng), 3000) * np.exp(-np.arange(nn) / SR / 0.001)
    return s * vel ** 1.3 * 0.7


def pluck_glide(f0, f1, vel=0.6, rng=None, glide=0.07, T=0.6):
    """A gentle rising pluck (petal / bloom): harp-like partials whose pitch bends up."""
    rng = rng or rng_for(f"pg{f0}")
    n = ns(T * 4)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** np.clip(t / glide, 0, 1) ** 0.6
    ph = TAU * np.cumsum(f) / SR
    s = np.zeros(n)
    for k, (a, tau) in enumerate([(1.0, T), (0.35, T * 0.45), (0.18, T * 0.25), (0.08, T * 0.15)], 1):
        if k * f1 < 7000:
            s += a * np.sin(k * ph + rng.uniform(0, TAU)) * np.exp(-t / tau)
    return s * attack_env(n, 0.002) * vel


# ---------------------------------------------------------------- mallets, glass, bells
def marimba(f, vel=1.0, rng=None, decay=1.0):
    """Marimba bar (modal): fundamental + 3.9x + 9.2x partials, fast decay, soft mallet."""
    rng = rng or rng_for(f"mar{f:.1f}")
    T = float(np.clip(0.45 * (700 / f) ** 0.5, 0.15, 1.2)) * decay
    s = modal([f, 3.9 * f, 9.2 * f], [1.0, 0.32, 0.09], [T, 0.11 * decay, 0.035 * decay],
              T * 7, attack=0.0012, rng=rng, phase0=True)
    nn = ns(0.006)
    s[:nn] += 0.06 * bp(white(nn, rng), 300, 2200) * np.exp(-np.arange(nn) / SR / 0.0015)
    return s * vel


def glass(f, vel=1.0, rng=None, decay=1.0, attack=0.006):
    """Soft glass bell: sine partials with a slow beating pair on the fundamental (shimmer)."""
    rng = rng or rng_for(f"glass{f:.1f}")
    ratios = [1.0, 1.0 + 0.7 / f, 2.0, 2.76, 5.40]
    amps = [0.6, 0.45, 0.28, 0.12, 0.05]
    taus = [1.8, 1.6, 0.85, 0.5, 0.22]
    return modal([f * r for r in ratios], amps, [x * decay for x in taus],
                 2.0 * decay * 4, attack=attack, rng=rng) * vel


# A warm major-third bell (tierce 1.25 rather than the church minor third, to sit on F major).
BELL_RATIOS = [0.5, 1.0, 1.25, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166]
BELL_AMPS = [0.55, 1.0, 0.30, 0.30, 0.55, 0.22, 0.16, 0.14, 0.06]
BELL_TAUS = [1.0, 0.75, 0.50, 0.45, 0.45, 0.28, 0.25, 0.20, 0.10]


def low_bell(f, T=4.0, vel=1.0, rng=None):
    """Inharmonic bell, `f` = prime. Soft strike, long hum."""
    rng = rng or rng_for(f"bell{f:.1f}")
    s = modal([f * r for r in BELL_RATIOS], BELL_AMPS, [T * x for x in BELL_TAUS], T * 2.2,
              attack=0.004, rng=rng)
    return s * vel


# ---------------------------------------------------------------- pad
def pad_note(m, dur, att=0.4, rel=0.6, vel=0.5, rng=None, bright=0.0, width=0.6):
    """Three detuned voices of a soft additive pad with slow vibrato, spread L/C/R."""
    f0 = float(midi_hz(m))
    rng = rng or rng_for(f"pad{m}")
    n = ns(dur + rel)
    t = np.arange(n) / SR
    env = attack_env(n, att)
    k0 = ns(dur)
    if k0 < n:
        env[k0:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(n - k0) / (n - k0))
    out = np.zeros((n, 2))
    for v, (cents, p) in enumerate([(-7, -width), (0, 0.0), (7, width)]):
        fv = f0 * 2 ** (cents / 1200)
        vib = 1 + 0.0018 * np.sin(TAU * (4.1 + 0.4 * v) * t + rng.uniform(0, TAU))
        ph = TAU * np.cumsum(fv * vib) / SR
        s = np.zeros(n)
        for k in range(1, 9):
            if k * fv > 6000:
                break
            s += (k ** -1.9) * np.exp(-(k - 1) * (0.35 - bright)) * np.sin(k * ph + rng.uniform(0, TAU))
        out += pan(s, p)
    return out * env[:, None] * vel * 0.35


# ---------------------------------------------------------------- doira, claps, shaker
def _jingle(rng, amt, dur=0.22):
    """The doira's ring jingles: a burst of tiny metal collisions + short metallic ring."""
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    times = np.concatenate([[0.0], np.sort(rng.uniform(0.002, 0.05, 6))])
    for i, o in enumerate(times):
        L = ns(0.02)
        b = bp(white(L, rng), 4200, 7200) * np.exp(-np.arange(L) / SR / 0.004) * rng.uniform(0.5, 1.0)
        i0 = ns(o)
        m = min(L, n - i0)
        out[i0:i0 + m] += b[:m]
    ring = modal(np.array([4300, 5650, 6900, 7600]) * rng.uniform(0.98, 1.02),
                 [0.10, 0.07, 0.04, 0.025], [0.05, 0.04, 0.035, 0.03], dur, attack=0.0005, rng=rng)
    return (out + ring) * np.exp(-t / 0.07) * amt


def doira_dum(vel=1.0, rng=None):
    """Low 'dum': pitch-dropping membrane thump + frame modes + skin noise + a little jingle."""
    rng = rng or rng_for("dum", int(vel * 100))
    dur = 0.45
    n = ns(dur)
    t = np.arange(n) / SR
    body = sweep_sine(dur, 150, 78, 0.025, 0.16, attack=0.0015)
    fb = 92 * rng.uniform(0.99, 1.01)
    modes = modal([fb * r for r in (1.59, 2.14, 2.30, 2.65, 2.92)], [0.35, 0.25, 0.2, 0.15, 0.1],
                  [0.09, 0.07, 0.06, 0.05, 0.04], dur, attack=0.001, rng=rng)
    skin = lp(white(n, rng), 1200) * np.exp(-t / 0.012) * 0.25
    return (body + 0.6 * modes + skin) * vel + _jingle(rng, 0.25 * vel, dur)


def doira_tak(vel=1.0, rng=None, sizzle=0.5):
    """Bright rim 'tak': band-passed slap + high frame modes + jingle sizzle."""
    rng = rng or rng_for("tak", int(vel * 100))
    dur = 0.25
    n = ns(dur)
    t = np.arange(n) / SR
    slap = bp(white(n, rng), 1800, 4200) * np.exp(-t / 0.018)
    modes = modal(np.array([640, 1030, 1490, 2120]) * rng.uniform(0.99, 1.01), [1, 0.7, 0.5, 0.3],
                  [0.035, 0.028, 0.022, 0.016], dur, attack=0.0006, rng=rng)
    return (0.8 * slap / (np.max(np.abs(slap)) + 1e-9) + 0.5 * modes / (np.max(np.abs(modes)) + 1e-9)) * vel * 0.5 \
        + _jingle(rng, sizzle * vel, dur)


def clap(vel=1.0, rng=None):
    """Layered short noise bursts with four micro-onsets, band-passed; the room comes later."""
    rng = rng or rng_for("clap", int(vel * 100))
    n = ns(0.25)
    out = np.zeros(n)
    offs = [0.0] + list(np.cumsum(rng.uniform(0.007, 0.011, 3)))
    for i, o in enumerate(offs):
        tau = 0.005 if i < 3 else 0.055
        L = ns(0.004 + tau * 6)
        tt = np.arange(L) / SR
        b = white(L, rng) * np.exp(-tt / tau) * (0.8, 0.9, 1.0, 1.0)[i]
        i0 = ns(o)
        m = min(L, n - i0)
        out[i0:i0 + m] += b[:m]
    out = bp(out, 850, 2600)
    out = peq(out, 1300, 3, 1.2)
    return out / (np.max(np.abs(out)) + 1e-9) * vel


def shaker_hit(vel=1.0, rng=None):
    rng = rng or rng_for("shk", int(vel * 1000))
    n = ns(0.08)
    t = np.arange(n) / SR
    s = white(n, rng) * (1 - np.exp(-t / 0.004)) * np.exp(-t / 0.022)
    s = lp(hp(s, 4200, 2), 8000, 4)
    return s / (np.max(np.abs(s)) + 1e-9) * vel
