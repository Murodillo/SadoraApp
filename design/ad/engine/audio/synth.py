"""
The DSP kit for the Sadora ad sound. numpy + scipy.signal only, 48 kHz, float64.

Everything the music, SFX and mix need: oscillators and modal partials, envelopes, filters,
moving band-pass noise (STFT synthesis), panning, a synthetic convolution reverb, the
BS.1770 K-weighting / loudness / true-peak meters, and 24-bit WAV I/O.
"""
import wave
import zlib

import numpy as np
from scipy import signal

SR = 48000
TAU = 2 * np.pi


# ---------------------------------------------------------------- basics
def ns(sec):
    """Seconds -> whole samples."""
    return max(0, int(round(sec * SR)))


def tvec(dur):
    return np.arange(ns(dur)) / SR


def db(x):
    return 10.0 ** (np.asarray(x, float) / 20.0)


def todb(x, floor=1e-12):
    return 20 * np.log10(np.maximum(np.abs(x), floor))


def rng_for(name, salt=0):
    """A deterministic RNG per event name, so a cue sounds the same in both languages."""
    return np.random.default_rng(zlib.crc32(f"{name}|{salt}".encode()))


def midi_hz(m):
    return 440.0 * 2.0 ** ((np.asarray(m, float) - 69) / 12)


def stereo(x):
    return x if x.ndim == 2 else np.stack([x, x], 1)


def mono(x):
    return x if x.ndim == 1 else x.mean(1)


def peak_norm(x, peak=1.0):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def fade(x, fin=0.0025, fout=0.0025):
    """Raised-cosine fade in/out; the last sample lands on zero. Works on (n,) or (n, 2)."""
    n = len(x)
    if n == 0:
        return x
    w = np.ones(n)
    a = min(ns(fin), n // 2)
    b = min(ns(fout), n // 2)
    if a > 0:
        w[:a] = 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if b > 0:
        w[n - b:] = 0.5 + 0.5 * np.cos(np.pi * (np.arange(b) + 1) / b)
    return x * (w if x.ndim == 1 else w[:, None])


def smoothstep(u):
    u = np.clip(u, 0, 1)
    return u * u * (3 - 2 * u)


def bell_env(n, peak=0.5, a=2.0, b=2.0):
    """Asymmetric bell 0 -> 1 -> 0 over n samples, peaking at fraction `peak`."""
    u = np.linspace(0, 1, n, endpoint=False) + 0.5 / max(n, 1)
    up = np.clip(u / peak, 0, 1) ** a
    dn = np.clip((1 - u) / (1 - peak), 0, 1) ** b
    return np.where(u < peak, up, dn)


def attack_env(n, att):
    """Raised-cosine rise over `att` seconds, then 1."""
    w = np.ones(n)
    k = min(ns(att), n)
    if k > 0:
        w[:k] = 0.5 - 0.5 * np.cos(np.pi * np.arange(k) / k)
    return w


def hf_weight(f):
    """House rule: nothing harsh above 8 kHz. Partials roll off above 5 kHz, vanish at 9.5."""
    f = np.asarray(f, float)
    w = np.where(f > 5000, (5000 / np.maximum(f, 1)) ** 3, 1.0)
    return np.where(f > 9500, 0.0, w)


def saturate(x, drive=1.5):
    return np.tanh(drive * x) / np.tanh(drive)


# ---------------------------------------------------------------- oscillators
def modal(freqs, amps, taus, dur, attack=0.001, rng=None, phase0=False):
    """Sum of exponentially decaying sines (modal synthesis). Partials are cut at -70 dB."""
    n = ns(dur)
    out = np.zeros(n)
    rng = rng or np.random.default_rng(0)
    for f, a, tau in zip(freqs, amps, taus):
        a = a * float(hf_weight(f))
        if a <= 0 or f <= 0 or f >= 0.45 * SR:
            continue
        m = min(n, ns(tau * 8.1) + 1)
        t = np.arange(m) / SR
        ph = 0.0 if phase0 else rng.uniform(0, TAU)
        out[:m] += a * np.sin(TAU * f * t + ph) * np.exp(-t / tau)
    return out * attack_env(n, attack)


def sweep_sine(dur, f0, f1, tau_f, amp_tau, attack=0.002, phase=0.0):
    """Sine whose pitch glides exponentially f0 -> f1 (time constant tau_f) and decays."""
    t = tvec(dur)
    f = f1 + (f0 - f1) * np.exp(-t / tau_f)
    ph = phase + TAU * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / amp_tau) * attack_env(len(t), attack)


def glide_sine(dur, f0, f1, glide, amp_tau, attack=0.003):
    """Sine gliding f0 -> f1 along a smoothstep over `glide` s (a pop or a boop)."""
    t = tvec(dur)
    f = f0 * (f1 / f0) ** smoothstep(t / glide)
    ph = TAU * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / amp_tau) * attack_env(len(t), attack)


# ---------------------------------------------------------------- noise and filters
def white(n, rng):
    return rng.standard_normal(n)


def pink(n, rng):
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    X[1:] /= np.sqrt(f[1:])
    X[0] = 0
    y = np.fft.irfft(X, n)
    return y / (np.std(y) + 1e-12)


def _sos(kind, f, order=2):
    if kind == "band":
        lo, hi = f
        return signal.butter(order, [max(lo, 10), min(hi, 0.45 * SR)], "bandpass", fs=SR, output="sos")
    return signal.butter(order, min(max(f, 5), 0.45 * SR), kind, fs=SR, output="sos")


def lp(x, f, order=2):
    return signal.sosfilt(_sos("low", f, order), x, axis=0)


def hp(x, f, order=2):
    return signal.sosfilt(_sos("high", f, order), x, axis=0)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos("band", (lo, hi), order), x, axis=0)


def peq(x, f, gain_db, q=1.0):
    """RBJ peaking EQ."""
    A = 10 ** (gain_db / 40)
    w0 = TAU * f / SR
    al = np.sin(w0) / (2 * q)
    b = np.array([1 + al * A, -2 * np.cos(w0), 1 - al * A])
    a = np.array([1 + al / A, -2 * np.cos(w0), 1 - al / A])
    return signal.lfilter(b / a[0], a / a[0], x, axis=0)


def band_noise(dur, fc, bw, rng, nfft=1024, hop=256):
    """
    Noise whose pass-band follows the picture: centre fc(u) Hz and width bw(u) octaves
    (Gaussian in log-frequency), u = 0..1 across the event. STFT synthesis with random
    phase and Hann overlap-add, each frame normalised to equal energy, so the caller's
    amplitude envelope alone sets the level. Returns unit-RMS noise.
    """
    n = ns(dur)
    if n == 0:
        return np.zeros(0)
    total = n + nfft
    nfr = total // hop + 1
    u = np.clip((np.arange(nfr) * hop - nfft / 2) / n, 0, 1)  # frame centre, output time
    fcs = np.asarray(fc(u) if callable(fc) else np.full(nfr, fc), float)
    bws = np.asarray(bw(u) if callable(bw) else np.full(nfr, bw), float)
    freqs = np.fft.rfftfreq(nfft, 1 / SR)
    lf = np.log2(np.maximum(freqs, 10.0))
    sig = np.maximum(bws, 0.05) / 2.3548
    mag = np.exp(-0.5 * ((lf[None, :] - np.log2(np.maximum(fcs, 20))[:, None]) / sig[:, None]) ** 2)
    mag *= hf_weight(freqs)[None, :]
    mag[:, freqs < 25] = 0
    mag /= np.sqrt((mag ** 2).sum(1, keepdims=True)) + 1e-12
    ph = rng.uniform(0, TAU, mag.shape)
    frames = np.fft.irfft(mag * np.exp(1j * ph), nfft, axis=1)
    frames *= np.hanning(nfft + 1)[:-1][None, :]
    buf = np.zeros(nfr * hop + nfft)
    for k in range(nfr):
        buf[k * hop:k * hop + nfft] += frames[k]
    out = buf[nfft:nfft + n]
    return out / (np.sqrt(np.mean(out ** 2)) + 1e-12)


def interp_curve(points):
    """points = [(u, value), ...] -> callable(u) with log interpolation (for frequencies)."""
    us = np.array([p[0] for p in points], float)
    vs = np.log(np.array([p[1] for p in points], float))
    return lambda u: np.exp(np.interp(u, us, vs))


def lin_curve(points):
    us = np.array([p[0] for p in points], float)
    vs = np.array([p[1] for p in points], float)
    return lambda u: np.interp(u, us, vs)


# ---------------------------------------------------------------- space
def pan(x, p):
    """Equal-power pan of a mono signal; p in -1..1, scalar or per-sample array."""
    th = (np.clip(p, -1, 1) + 1) * np.pi / 4
    return np.stack([x * np.cos(th), x * np.sin(th)], 1)


def make_ir(rt60, length=None, predelay=0.012, lo_mult=1.25, hi_mult=0.45, hi_cut=6500,
            er_gain=0.35, seed=1):
    """
    A synthetic stereo room: decorrelated noise in three bands with their own decay
    (warm: lows ring longer, highs die first), a few early reflections, a predelay.
    Each channel is normalised to unit energy, so wet level = dry level for white input.
    """
    rng = np.random.default_rng(seed)
    L = length or rt60 * 1.15
    n = ns(L)
    t = np.arange(n) / SR
    ir = np.zeros((n, 2))
    e = lambda rt: np.exp(-6.91 * t / rt)
    for ch in range(2):
        w = rng.standard_normal(n)
        low = lp(w, 280, 2)
        high = hp(w, 3200, 2)
        mid = bp(w, 280, 3200, 2)
        tail = low * e(rt60 * lo_mult) + mid * e(rt60) + high * e(rt60 * hi_mult)
        tail = lp(tail, hi_cut, 2) * (1 - np.exp(-t / 0.006))
        ir[:, ch] = tail
    # early reflections, alternating sides
    ers = rng.uniform(0.004, 0.055, 9)
    for i, d in enumerate(sorted(ers)):
        k = ns(d)
        ir[k, i % 2] += er_gain * (1 - d / 0.07) * rng.choice([-1, 1]) * 6
    for ch in range(2):
        ir[:, ch] /= np.sqrt(np.sum(ir[:, ch] ** 2)) + 1e-12
    pad = np.zeros((ns(predelay), 2))
    ir = np.vstack([pad, ir])
    return fade(ir, 0.0005, 0.08)


def reverb(x, ir, wet_db=0.0):
    """Wet-only convolution, cropped to len(x). Mono input feeds both IR channels."""
    n = len(x)
    out = np.zeros((n, 2))
    for ch in range(2):
        src = x if x.ndim == 1 else x[:, ch]
        out[:, ch] = signal.oaconvolve(src, ir[:, ch])[:n]
    return out * db(wet_db)


def reverb_full(x, ir, wet_db=0.0):
    """Wet-only convolution keeping the whole tail."""
    n = len(x) + len(ir) - 1
    out = np.zeros((n, 2))
    for ch in range(2):
        src = x if x.ndim == 1 else x[:, ch]
        out[:, ch] = signal.oaconvolve(src, ir[:, ch])
    return out * db(wet_db)


class Bus:
    """A stereo timeline buffer. Every event added gets >= 2.5 ms fades at both ends."""

    def __init__(self, length_s):
        self.n = ns(length_s)
        self.x = np.zeros((self.n, 2))

    def add(self, sig, t_start, gain_db=0.0, pan_pos=0.0, fades=True):
        if fades:
            sig = fade(sig, 0.0025, 0.0025)
        if sig.ndim == 1:
            sig = pan(sig, pan_pos)
        sig = sig * db(gain_db)
        i0 = int(round(t_start * SR))
        j0 = 0
        if i0 < 0:
            j0, i0 = -i0, 0
        m = min(len(sig) - j0, self.n - i0)
        if m > 0:
            self.x[i0:i0 + m] += sig[j0:j0 + m]
        return i0 / SR, (i0 + max(m, 0)) / SR


# ---------------------------------------------------------------- meters (ITU-R BS.1770-4)
K_SOS = np.array([
    [1.53512485958697, -2.69169618940638, 1.19839281085285, 1.0, -1.69065929318241, 0.73248077421585],
    [1.0, -2.0, 1.0, 1.0, -1.99004745483398, 0.99007225036621],
])


def kweight(x):
    return signal.sosfilt(K_SOS, x, axis=0)


def _chan_power(x):
    """K-weighted squared signal summed over channels (G = 1 for L and R)."""
    y = kweight(x)
    return y ** 2 if y.ndim == 1 else (y ** 2).sum(1)


def integrated_lufs(x):
    p = _chan_power(x)
    blk, hop = ns(0.4), ns(0.1)
    c = np.concatenate([[0], np.cumsum(p)])
    starts = np.arange(0, len(p) - blk + 1, hop)
    z = (c[starts + blk] - c[starts]) / blk
    lk = -0.691 + 10 * np.log10(np.maximum(z, 1e-20))
    g = z[lk > -70]
    if len(g) == 0:
        return -np.inf
    rel = -0.691 + 10 * np.log10(np.mean(g)) - 10
    g2 = z[(lk > -70) & (lk > rel)]
    return -0.691 + 10 * np.log10(np.mean(g2))


def windowed_lufs(x, win=0.4, hop=0.01):
    """Loudness over sliding K-weighted windows. Returns (centre times, LUFS)."""
    p = _chan_power(x)
    blk, h = ns(win), ns(hop)
    c = np.concatenate([[0], np.cumsum(p)])
    centres = np.arange(0, len(p), h)
    a = np.clip(centres - blk // 2, 0, len(p))
    b = np.clip(centres + blk // 2, 0, len(p))
    z = (c[b] - c[a]) / blk
    return centres / SR, -0.691 + 10 * np.log10(np.maximum(z, 1e-20))


def true_peak_db(x, os_factor=4):
    xs = x if x.ndim == 2 else x[:, None]
    m = 0.0
    for ch in range(xs.shape[1]):
        y = signal.resample_poly(xs[:, ch], os_factor, 1)
        m = max(m, np.max(np.abs(y)))
    return 20 * np.log10(max(m, 1e-12))


# ---------------------------------------------------------------- WAV I/O
def write_wav(path, x, bits=24):
    x = np.clip(np.asarray(x, float), -1.0, 1.0 - 2 ** -23)
    if x.ndim == 1:
        x = x[:, None]
    nch = x.shape[1]
    if bits == 24:
        q = np.round(x * (2 ** 23 - 1)).astype("<i4")
        raw = q.reshape(-1).view(np.uint8).reshape(-1, 4)[:, :3].tobytes()
        width = 3
    else:
        q = np.round(x * 32767).astype("<i2")
        raw = q.reshape(-1).tobytes()
        width = 2
    with wave.open(path, "wb") as w:
        w.setnchannels(nch)
        w.setsampwidth(width)
        w.setframerate(SR)
        w.writeframes(raw)


def read_wav(path):
    with wave.open(path, "rb") as w:
        nch, width, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(n)
    assert sr == SR, f"{path}: {sr} Hz, expected {SR}"
    if width == 2:
        x = np.frombuffer(raw, "<i2").astype(float) / 32768
    elif width == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3)
        i = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8) | (b[:, 2].astype(np.int32) << 16))
        i = np.where(i & 0x800000, i - 0x1000000, i)
        x = i.astype(float) / 2 ** 23
    elif width == 4:
        x = np.frombuffer(raw, "<i4").astype(float) / 2 ** 31
    else:
        raise ValueError(width)
    x = x.reshape(-1, nch)
    return x[:, 0] if nch == 1 else x


def msum(*xs):
    """Sum arrays of different lengths (mono or stereo), zero-padding the short ones."""
    st = any(x.ndim == 2 for x in xs)
    n = max(len(x) for x in xs)
    out = np.zeros((n, 2) if st else n)
    for x in xs:
        out[:len(x)] += stereo(x) if st else x
    return out
