"""
The Sadora chime: F5 - A5 - C6 rising, marimba (fundamental + 3.9x + 9.2x) layered with
soft glass, ~65 ms apart, spread slightly across the stereo field, in a short airy room.

The sonic logo is the same motif slowed to 0.6x (pitch kept, every gap and decay
stretched by 1/0.6) with a low F3 bell under it and a longer room.
"""
import numpy as np

from instruments import glass, low_bell, marimba
from synth import SR, Bus, fade, make_ir, midi_hz, msum, ns, reverb_full, rng_for

NOTES = [77, 81, 84]          # F5, A5, C6
GAP = 0.065                   # s between notes at speed 1
PANS = [-0.28, 0.0, 0.28]
VELS = [0.80, 0.90, 1.00]

_AIR = make_ir(0.85, predelay=0.015, lo_mult=0.9, hi_mult=0.6, hi_cut=7000, seed=11)
_HALL = make_ir(1.9, predelay=0.022, lo_mult=1.1, hi_mult=0.5, hi_cut=6500, seed=12)


def _motif(speed, glass_amt, tag):
    length = 3.2 / speed
    bus = Bus(length)
    for i, (m, p, v) in enumerate(zip(NOTES, PANS, VELS)):
        f = float(midi_hz(m))
        rng = rng_for(f"{tag}{m}")
        note = msum(marimba(f, v, rng, decay=1 / speed), glass_amt * glass(f, v, rng, decay=1 / speed))
        bus.add(note, i * GAP / speed, 0.0, p)
    return bus.x


def chime(dry_only=False):
    """Stereo chime, first note at sample 0 (its sync point). Peak ~1."""
    dry = _motif(1.0, 0.45, "chime")
    if dry_only:
        return dry
    wet = reverb_full(dry, _AIR, -10)
    out = np.zeros_like(wet)
    out[:len(dry)] += dry
    out += wet
    out = out[:ns(2.6)]
    out = fade(out, 0.0005, 0.25)
    return out / np.max(np.abs(out))


def sonic_logo():
    """The chime at 0.6x speed (pitch kept) + a low F3 bell, in a longer room."""
    speed = 0.6
    dry = _motif(speed, 0.75, "logo")
    bell = low_bell(float(midi_hz(53)), T=3.2, vel=0.55, rng=rng_for("logo_bell"))
    n = max(len(dry), len(bell))
    mix = np.zeros((n, 2))
    mix[:len(dry)] += dry
    mix[:len(bell), 0] += bell * 0.72
    mix[:len(bell), 1] += bell * 0.68
    w1 = reverb_full(mix, _HALL, -8)
    w2 = reverb_full(dry, _AIR, -12)
    out = np.zeros((max(len(w1), len(w2), n), 2))
    out[:n] += mix
    out[:len(w1)] += w1
    out[:len(w2)] += w2
    out = out[:ns(4.2)]
    out = fade(out, 0.0005, 0.9)
    return out / np.max(np.abs(out))
