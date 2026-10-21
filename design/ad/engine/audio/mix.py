"""
The mix: voice up front, the music held >= 12 dB under every word, SFX under the voice
except the hero cues, then a -14 LUFS / -1.25 dBTP master.

    voice   HPF 80 Hz, +1.5 dB at 3 kHz, 2:1 soft-knee compression, a look-ahead peak
            limiter at -4 dBTP, -16 LUFS (as it plays, centred on both channels)
    music   sidechain duck from the voice envelope (40 ms look-ahead attack, 300 ms release),
            then a loudness guarantee: wherever a 400 ms K-weighted window centred in a voice
            block (speech-gated: voice within 10 LU of its integrated loudness, as in
            BS.1770) has the music closer than 12.6 dB to the voice, that window (+-0.4 s, so
            it rides phrases rather than pumping on syllables) is pushed down by the
            shortfall (same smoothing). Hero cues dip the music 5 dB
            (50 ms attack, 400 ms release).
    sfx     ordinary cues ducked 6 dB under words; hero cues untouched
    master  gain to -14 LUFS integrated, 4x-oversampled look-ahead true-peak limiter,
            iterated until both targets hold. The limiter's gain is applied to every stem,
            so the stems written out sum exactly to the mix.
"""
import numpy as np
from scipy import signal
from scipy.ndimage import maximum_filter1d, minimum_filter1d

from synth import (SR, db, hp, integrated_lufs, ns, peq, stereo, true_peak_db, windowed_lufs)

VO_TARGET = -16.0
MASTER_TARGET = -14.0
TP_LIMIT = -1.25
GAP_MIN = 12.0
GAP_AIM = 12.6
MUSIC_GAIN = -15.0      # music bus into the mix (dB)
SFX_GAIN = 0.0
MUSIC_DUCK = 8.0        # base voice duck on the music (dB)
SFX_DUCK = 6.0
HERO_DUCK = 5.0
GUARANTEE_SPREAD = 0.4  # s each side: the guarantee rides whole phrases, not syllables
EDGE = 0.10             # a window counts only if its centre is >= 100 ms inside the block (>= 75 % speech)
VO_CEILING = -4.0       # dBTP for the voice at -16 LUFS, so the master limiter barely works
CTRL = 1000             # control rate for gain curves (Hz)

# hero cue name prefix -> how long the music stays dipped (s)
HERO_HOLD = {"slam_": 0.30, "meds_chime": 0.45, "rx_chime": 0.45, "heart_pop": 0.25,
             "t0_land": 0.40, "sonic_logo": 1.60}


# ---------------------------------------------------------------- gain-curve tools
def _peak_hold(x, coef):
    """y[i] = max(x[i], y[i-1] * coef) for x >= 0, vectorised through a log-domain cummax."""
    la = np.log(coef)
    i = np.arange(len(x))
    lx = np.log(np.maximum(x, 1e-12))
    return np.exp(i * la + np.maximum.accumulate(lx - i * la))


def smooth_red(r, attack=0.04, release=0.30, rate=CTRL):
    """Reduction curve (dB, >= 0): exponential release, look-ahead exponential attack."""
    y = _peak_hold(r, np.exp(-1 / (release * rate)))
    y = _peak_hold(y[::-1], np.exp(-1 / (attack * rate)))[::-1]
    return np.where(y < 1e-6, 0.0, y)


def ctrl_to_gain(red, n):
    t = np.arange(n) / SR
    return db(-np.interp(t, np.arange(len(red)) / CTRL, red))


def to_ctrl_max(x, n_ctrl):
    """Per-control-step max of a sample-rate curve."""
    step = SR // CTRL
    m = len(x) // step
    out = np.zeros(n_ctrl)
    out[:m] = x[:m * step].reshape(m, step).max(1)
    return out


# ---------------------------------------------------------------- voice
def compress(x, thr_db=-24.0, ratio=2.0, knee=6.0, att=0.008, rel=0.12):
    a = np.exp(-1 / (0.005 * SR))
    env = signal.lfilter([1 - a], [1, -a], x ** 2)
    lvl = 10 * np.log10(env + 1e-12)
    over = lvl - thr_db
    s = 1 - 1 / ratio
    gr = np.where(over <= -knee / 2, 0.0,
                  np.where(over >= knee / 2, over * s, s * (over + knee / 2) ** 2 / (2 * knee)))
    n_ctrl = len(x) // (SR // CTRL) + 1
    g = to_ctrl_max(gr, n_ctrl)
    # attack 8 ms / release 120 ms, causal one-pole on the reduction
    y = np.zeros_like(g)
    aa, ar = np.exp(-1 / (att * CTRL)), np.exp(-1 / (rel * CTRL))
    prev = 0.0
    for i, v in enumerate(g):
        c = aa if v > prev else ar
        prev = c * prev + (1 - c) * v
        y[i] = prev
    return x * ctrl_to_gain(y, len(x)), y


def process_vo(v):
    v = hp(v, 80, 4)
    v = peq(v, 3000, 1.5, 0.9)
    v = v * db(VO_TARGET - integrated_lufs(stereo(v)))
    v, gr = compress(v)
    for _ in range(3):  # peak control: look-ahead limiter on the 4x-oversampled voice
        v = v * db(VO_TARGET - integrated_lufs(stereo(v)))
        v = v * limiter(v[:, None], VO_CEILING, release=0.05)
    v = v * db(VO_TARGET - integrated_lufs(stereo(v)))
    return v, gr


def vo_activity(v, n_ctrl):
    a = np.exp(-1 / (0.01 * SR))
    env = np.sqrt(np.maximum(signal.lfilter([1 - a], [1, -a], v ** 2), 0))
    lvl = 20 * np.log10(env + 1e-9)
    act = np.clip((lvl + 50) / 12, 0, 1)
    return to_ctrl_max(act, n_ctrl)


# ---------------------------------------------------------------- loudness guarantee
def gap_windows(vo_st, music, blocks, gated=True):
    """
    400 ms K-weighted windows centred inside a voice block, at least EDGE from its ends
    (so >= 75 % of every window is speech), and gated like BS.1770 integrated loudness:
    the voice in the window must be within 10 LU of its integrated loudness. A window
    that is mostly the pause before a word or a word's fade tail is not a "word".
    """
    tc, lv = windowed_lufs(vo_st)
    _, lm = windowed_lufs(music)
    gate = integrated_lufs(vo_st) - 10.0 if gated else -np.inf
    sel = np.zeros(len(tc), bool)
    owner = np.full(len(tc), -1)
    for k, b in enumerate(blocks):
        e = EDGE if gated else 0.0
        m = (tc >= b["start"] + e) & (tc <= b["end"] - e)
        owner[m] = k
        sel |= m
    sel &= lv >= gate
    owner[~sel] = -1
    return tc, lv, lm, sel, owner


def worst_gaps(vo_st, music, blocks, gated=True):
    tc, lv, lm, sel, owner = gap_windows(vo_st, music, blocks, gated)
    rows = []
    for k, b in enumerate(blocks):
        m = owner == k
        if not np.any(m):
            continue
        g = lv[m] - lm[m]
        j = int(np.argmin(g))
        rows.append(dict(block=b["name"], start=b["start"], end=b["end"], worst_gap=float(g[j]),
                         at=float(tc[m][j]), voice=float(lv[m][j]), music=float(lm[m][j]),
                         windows=int(m.sum())))
    return rows


# ---------------------------------------------------------------- limiter / master
def limiter(y, ceiling_db, lookahead=0.0015, release=0.08):
    """Look-ahead peak limiter on the 4x-oversampled peak of both channels."""
    n = len(y)
    pk = np.zeros(n)
    for ch in range(y.shape[1]):
        up = signal.resample_poly(y[:, ch], 4, 1)
        pk = np.maximum(pk, np.abs(up[:4 * n]).reshape(n, 4).max(1))
    pk = np.maximum(pk, np.roll(pk, 1))
    req = np.minimum(1.0, db(ceiling_db) / np.maximum(pk, 1e-9))
    L = ns(lookahead)
    m = minimum_filter1d(req, size=2 * L + 1)
    h = _peak_hold(1 - m, np.exp(-1 / (release * SR)))
    g = 1 - np.minimum(h, 1 - 1e-9)
    w = np.hanning(2 * L + 3)[1:-1]
    return np.convolve(np.pad(g, L, mode="edge"), w / w.sum(), mode="valid")


def master(mix):
    g = MASTER_TARGET - integrated_lufs(mix)
    ceiling = TP_LIMIT - 0.35
    for _ in range(12):
        y = mix * db(g)
        lg = limiter(y, ceiling)
        z = y * lg[:, None]
        L, tp = integrated_lufs(z), true_peak_db(z)
        if tp > TP_LIMIT - 0.05:
            ceiling -= tp - (TP_LIMIT - 0.12)
            continue
        if abs(L - MASTER_TARGET) < 0.03:
            break
        g += MASTER_TARGET - L
    return db(g) * lg, L, tp


# ---------------------------------------------------------------- the whole mix
def hero_red(cues, n_ctrl, exclude=()):
    r = np.zeros(n_ctrl)
    for c in cues:
        if not c["hero"]:
            continue
        hold = next((h for p, h in HERO_HOLD.items() if c["name"].startswith(p)), 0.3)
        if any(c["name"].startswith(e) for e in exclude):
            continue
        a, b = int(c["t"] * CTRL), int((c["t"] + hold) * CTRL)
        r[max(a, 0):min(b, n_ctrl)] = HERO_DUCK
    return smooth_red(r, attack=0.05, release=0.40)


def run(vo_raw, M, S, blocks, breath):
    n = len(vo_raw)
    n_ctrl = n // (SR // CTRL) + 1
    vo, comp_gr = process_vo(vo_raw)
    vo_st = stereo(vo)

    act = vo_activity(vo, n_ctrl)
    speech_r = smooth_red(MUSIC_DUCK * act)
    sfx_r = smooth_red(SFX_DUCK * act)
    hero_all = hero_red(S["cues"], n_ctrl)
    hero_hits = hero_red(S["cues"], n_ctrl, exclude=("slam_", "t0_land"))

    mg = db(MUSIC_GAIN)
    bed, hits, swell = M["bed"][:n] * mg, M["hits"][:n] * mg, M["swell"][:n] * mg
    corr = np.zeros(n_ctrl)
    # once the voice has stopped (the breath), the reversed swell is free of the guarantee
    tcx = np.arange(n_ctrl) / CTRL
    swell_mask = np.clip(1 - (tcx - breath[0]) / 0.05, 0, 1)

    def music_with(c):
        gb = ctrl_to_gain(speech_r + hero_all + c, n)[:, None]
        gh = ctrl_to_gain(speech_r + hero_hits + c, n)[:, None]
        gs = ctrl_to_gain(c * swell_mask, n)[:, None]
        return bed * gb + hits * gh + swell * gs

    music = music_with(corr)
    for _ in range(4):
        tc, lv, lm, sel, _o = gap_windows(vo_st, music, blocks)
        short = np.where(sel, GAP_AIM - (lv - lm), 0.0)
        if short.max() <= 0:
            break
        need = np.zeros(n_ctrl)
        for i in np.nonzero(short > 0)[0]:
            a, b = int((tc[i] - 0.2) * CTRL), int((tc[i] + 0.2) * CTRL) + 1
            need[max(a, 0):min(b, n_ctrl)] = np.maximum(need[max(a, 0):min(b, n_ctrl)], short[i])
        need = maximum_filter1d(need, size=int(GUARANTEE_SPREAD * CTRL) * 2 + 1)
        corr = corr + smooth_red(need)
        music = music_with(corr)

    sg = db(SFX_GAIN)
    sfx = S["duck"][:n] * sg * ctrl_to_gain(sfx_r, n)[:, None] + S["hero"][:n] * sg

    pre = vo_st + music + sfx
    gain, L, tp = master(pre)
    G = gain[:, None]
    stems = {"vo": vo_st * G, "music": music * G, "sfx": sfx * G}
    mix = stems["vo"] + stems["music"] + stems["sfx"]
    info = dict(lufs=L, tp=tp, master_gain_db=float(20 * np.log10(np.median(gain))),
                limiter_max_gr_db=float(-20 * np.log10(np.min(gain) / np.median(gain))),
                limiter_max_gr_at=float(np.argmin(gain) / SR),
                vo_comp_gr_mean=float(np.mean(comp_gr[comp_gr > 0.05])) if np.any(comp_gr > 0.05) else 0.0,
                duck_speech_max=float(speech_r.max()), corr_max=float(corr.max()),
                corr_mean_in_speech=float(np.mean(corr[(act > 0.5)])) if np.any(act > 0.5) else 0.0)
    return mix, stems, info
