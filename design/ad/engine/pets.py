"""
The companions, moved only as whole sprites and their own clips.

Frames come from build/ad/pets/<pet>_<pose>/NNN.webp (re-cut from the app's source videos
at 512 px / 24 fps by cut_pets.sh). While a cut is missing the app's own 220 px frames
stand in, and failing that the 512 px still from design/pets/cut. Nothing is redrawn or
recoloured: a pet can be scaled, squashed, stretched, tilted, faded — and played.
"""
import functools, glob, math, os

import skia

from core import PETS, ROOT, clamp, image

APP_ANIM = os.path.join(ROOT, "design", "pets", "anim")
CUT = os.path.join(ROOT, "design", "pets", "cut")
POSE_DIR = {"idle": "idle", "happy": "happy", "think": "think", "wave": "idle", "sleep": "sleep"}
APP_POSE = {"idle": "wave", "happy": "happy", "think": "think", "sleep": "sleep", "wave": "wave"}


@functools.lru_cache(maxsize=None)
def frames(pet, pose):
    d = os.path.join(PETS, f"{pet}_{POSE_DIR.get(pose, pose)}")
    fs = sorted(glob.glob(os.path.join(d, "*.webp")))
    if len(fs) >= 200:
        return fs, 24
    d = os.path.join(APP_ANIM, f"{pet}_{APP_POSE.get(pose, pose)}")
    fs = sorted(glob.glob(os.path.join(d, "*.webp")))
    if fs:
        return fs, 12
    still = os.path.join(CUT, f"{pet}_{'idle' if pose in ('wave',) else pose}.png")
    return [still], 1


def frame_at(pet, pose, t, loop=True, start=0):
    fs, fps = frames(pet, pose)
    i = start + int(max(0.0, t) * fps)
    i = i % len(fs) if loop else min(i, len(fs) - 1)
    return image(fs[i])


def draw(canvas, pet, pose, t, cx, base, height, sx=1.0, sy=1.0, rot=0.0, alpha=1.0, loop=True,
         start=0, flip=False):
    """
    The pet standing on `base` (its feet), centred on cx, `height` px tall as a sprite box.
    Squash and stretch pivot at the feet, the way a landing would.
    """
    if alpha <= 0 or height <= 1:
        return
    img = frame_at(pet, pose, t, loop, start)
    s = height
    canvas.save()
    canvas.translate(cx, base)
    if rot:
        canvas.rotate(rot)
    canvas.scale(sx * (-1 if flip else 1), sy)
    p = skia.Paint(AntiAlias=True)
    p.setAlphaf(clamp(alpha))
    # The cut puts the feet 3 % above the frame's bottom edge.
    canvas.drawImageRect(img, skia.Rect.MakeXYWH(-s / 2, -s * 0.97, s, s),
                         skia.SamplingOptions(skia.CubicResampler.Mitchell()), p)
    canvas.restore()


def hop(t, t_land, height=80.0, air=0.38, squash=0.16):
    """
    A hop that lands at t_land: returns (dy, sx, sy). Before take-off the pet crouches,
    in the air it stretches, on landing it squashes and settles (the pop curve's feel).
    """
    up = t_land - air
    if t < up - 0.08:
        return 0.0, 1.0, 1.0
    if t < up:  # crouch
        k = (t - (up - 0.08)) / 0.08
        return 0.0, 1 + 0.08 * k, 1 - 0.10 * k
    if t < t_land:  # flight: a parabola, stretched on the way up
        k = (t - up) / air
        dy = -height * 4 * k * (1 - k)
        st = 0.10 * (1 - k)
        return dy, 1 - st * 0.6, 1 + st
    k = t - t_land  # landing
    if k < 0.36:
        d = math.exp(-k * 9) * math.cos(k * 26)
        return 0.0, 1 + squash * d, 1 - squash * d
    return 0.0, 1.0, 1.0
