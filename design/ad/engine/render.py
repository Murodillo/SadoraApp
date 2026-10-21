#!/usr/bin/env python3
"""
Renders "The Path".

    render.py still  uz 169 5.2 [7.4 …]          -> build/ad/stills/uz_169_5.200.png
    render.py board  uz 169                        -> a contact sheet of every beat
    render.py video  uz 169 [--preview] [--from 0 --to 25.2] [--workers 10] [--mblur 3]

Frames are rendered in worker processes, post-processed (motion blur on fast moves,
vignette, grain) and piped in order into ffmpeg. --preview renders at half size, 30 fps.
"""
import argparse, functools, math, os, subprocess, sys, time
from multiprocessing import Pool

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from core import BUILD

OUT = os.path.join(BUILD, "out")
STILLS = os.path.join(BUILD, "stills")
_film = None


def fmt_of(name):
    from film import FMT_169, FMT_916, Fmt
    base = FMT_169 if name == "169" else FMT_916
    return base


def init(lang, fmt_name, scale):
    global _film
    import skia  # noqa: F401
    from film import Film, Fmt
    f = fmt_of(fmt_name)
    _film = Film(lang, f)
    _film._scale = scale


@functools.lru_cache(maxsize=4)
def _grain(h, w, seed):
    rng = np.random.default_rng(seed)
    g = rng.normal(0, 1, (h, w)).astype(np.float32)
    return g


@functools.lru_cache(maxsize=2)
def _vignette(h, w):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
    r = np.sqrt(nx * nx * 0.8 + ny * ny * 0.8)
    return (1 - 0.22 * np.clip(r - 0.35, 0, 1) ** 1.6)[..., None].astype(np.float32)


def post(rgb, t, frame_no):
    """Vignette and a light, moving film grain (it also kills banding in the gradients)."""
    h, w = rgb.shape[:2]
    out = rgb.astype(np.float32) * _vignette(h, w)
    g = _grain(h, w, frame_no % 8)
    lum = out.mean(2, keepdims=True) / 255
    amp = 3.2 * (0.55 + 0.45 * (1 - lum))
    out += g[..., None] * amp
    return np.clip(out, 0, 255).astype(np.uint8)


def speed_at(t):
    """How fast the camera is moving (px/s on screen, roughly) — drives motion blur."""
    f = _film
    c0, c1 = f.camera(max(0, t - 0.01)), f.camera(t + 0.01)
    d = np.linalg.norm(c1.pos - c0.pos) + np.linalg.norm(c1.f - c0.f) * 1500
    return d / 0.02


def render_frame(args):
    t, frame_no, mblur, fps = args
    f = _film
    import skia
    W, H = f.W, f.H
    surf = skia.Surface(W, H)
    samples = 1
    if mblur > 1:
        sp = speed_at(t)
        samples = 1 if sp < 900 else (2 if sp < 2500 else mblur)
    if samples == 1:
        img = f.frame(t, surf)[..., :3].astype(np.float32)
    else:
        shutter = 0.5 / fps
        acc = None
        for i in range(samples):
            ts = t + shutter * ((i + 0.5) / samples - 0.5)
            a = f.frame(ts, surf)[..., :3].astype(np.float32)
            acc = a if acc is None else acc + a
        img = acc / samples
    out = post(img, t, frame_no)
    sc = getattr(f, "_scale", 1.0)
    if sc != 1.0:
        import cv2
        out = cv2.resize(out, (int(W * sc), int(H * sc)), interpolation=cv2.INTER_AREA)
    return out.tobytes()


def cmd_still(lang, fmt_name, times, scale=1.0):
    init(lang, fmt_name, scale)
    os.makedirs(STILLS, exist_ok=True)
    from PIL import Image
    paths = []
    for t in times:
        b = render_frame((t, int(t * 60), 1, 60))
        f = _film
        w, h = int(f.W * scale), int(f.H * scale)
        p = os.path.join(STILLS, f"{lang}_{fmt_name}_{t:06.3f}.png")
        Image.frombytes("RGB", (w, h), b).save(p)
        paths.append(p)
        print(p)
    return paths


def cmd_board(lang, fmt_name, times=None, cols=4, scale=0.25):
    init(lang, fmt_name, scale)
    from PIL import Image, ImageDraw
    B = _film.B
    if times is None:
        times = [0.05, B["slam"] + 0.02, 0.6, 1.3, 1.95, B["today"] + 0.2, B["greeting"] + 0.4, B["water"] + 0.3,
                 B["meds"] + 0.3, B["mood"] + 0.25, B["drop"] + 0.3, B["badge"] + 0.4, B["question_lands"] + 0.1,
                 (B["typing"][0] + B["typing"][1]) / 2, B["footnote"] + 0.3, B["verified"] + 0.2, B["press"],
                 B["rx_lands"] + 0.2, B["partner_view"] + 0.4, B["chip_fly"][1] + 0.1, B["reply"] + 0.4,
                 B["stage_hits"][0] + 0.3, B["stage_hits"][2] + 0.3, B["stages_hold"], B["type1"] + 0.5,
                 B["type2"] + 0.5, B["t0"] + 0.05, B["letters"] + 0.2, B["sonic"] + 0.3, 24.6]
    tiles = []
    w, h = int(_film.W * scale), int(_film.H * scale)
    for t in times:
        b = render_frame((t, int(t * 60), 1, 60))
        im = Image.frombytes("RGB", (w, h), b)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 92, 18], fill=(0, 0, 0))
        d.text((4, 3), f"{t:5.2f}s f{int(round(t * 60))}", fill=(255, 255, 255))
        tiles.append(im)
    rows = math.ceil(len(tiles) / cols)
    sheet = Image.new("RGB", (cols * w + (cols - 1) * 4, rows * h + (rows - 1) * 4), (40, 40, 40))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % cols) * (w + 4), (i // cols) * (h + 4)))
    os.makedirs(STILLS, exist_ok=True)
    p = os.path.join(STILLS, f"board_{lang}_{fmt_name}.png")
    sheet.save(p)
    print(p)
    return p


def cmd_video(lang, fmt_name, preview=False, t0=0.0, t1=None, workers=10, mblur=3, out=None, audio=None):
    import timeline as TL
    fps = 30 if preview else 60
    scale = 0.5 if preview else 1.0
    t1 = TL.LENGTH if t1 is None else t1
    from film import FMT_169, FMT_916
    f = FMT_169 if fmt_name == "169" else FMT_916
    W, H = int(f.W * scale), int(f.H * scale)
    os.makedirs(OUT, exist_ok=True)
    out = out or os.path.join(OUT, f"{lang}_{fmt_name}{'_preview' if preview else ''}.mp4")
    n0, n1 = int(round(t0 * fps)), int(round(t1 * fps))
    jobs = [(n / fps, int(n / fps * 60), 1 if preview else mblur, fps) for n in range(n0, n1)]
    ff = ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(fps), "-i", "-"]
    if audio and os.path.exists(audio):
        ff += ["-ss", f"{t0}", "-i", audio, "-c:a", "aac", "-b:a", "256k", "-shortest"]
    ff += ["-c:v", "libx264", "-preset", "medium" if preview else "slow", "-crf", "20" if preview else "14",
           "-pix_fmt", "yuv420p", "-movflags", "+faststart", out]
    p = subprocess.Popen(ff, stdin=subprocess.PIPE)
    start = time.time()
    with Pool(workers, initializer=init, initargs=(lang, fmt_name, scale)) as pool:
        for i, b in enumerate(pool.imap(render_frame, jobs, chunksize=2)):
            p.stdin.write(b)
            if i % 30 == 0:
                el = time.time() - start
                print(f"  frame {i + n0}/{n1}  {el:6.1f}s  ({(i + 1) / max(el, 1e-6):.2f} fps)", flush=True)
    p.stdin.close()
    p.wait()
    print(out, f"{time.time() - start:.0f}s")
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd")
    ap.add_argument("lang")
    ap.add_argument("fmt")
    ap.add_argument("times", nargs="*", type=float)
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--from", dest="t0", type=float, default=0.0)
    ap.add_argument("--to", dest="t1", type=float, default=None)
    ap.add_argument("--workers", type=int, default=10)
    ap.add_argument("--mblur", type=int, default=3)
    ap.add_argument("--scale", type=float, default=0.5)
    ap.add_argument("--audio", default=None)
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    if a.cmd == "still":
        cmd_still(a.lang, a.fmt, a.times, a.scale)
    elif a.cmd == "board":
        cmd_board(a.lang, a.fmt, a.times or None, scale=a.scale if a.scale != 0.5 else 0.25)
    elif a.cmd == "video":
        cmd_video(a.lang, a.fmt, a.preview, a.t0, a.t1, a.workers, a.mblur, a.out, a.audio)
