# The Path — engine

The Sadora launch ad, rendered entirely in code. Brief: `../the-path-brief.md`; the original
prompt: `../sadora-the-path-PROMPT.md`.

Every command runs from the repo root with the project venv
(`build/ad/.venv/bin/python`: skia-python + uharfbuzz over the system numpy/OpenCV/Pillow).

```bash
# 1. Voice: takes from Gemini TTS (repo .env key), the edit, the anchors the picture locks to
python3 design/ad/engine/vo_gemini.py uz Achernar        # a take → build/ad/vo/
python3 design/ad/engine/vo_check.py build/ad/vo/<take>.wav uz
python3 design/ad/engine/vo_edit.py uz                    # → uz_edit.wav + uz_anchors.json
python3 design/ad/engine/timeline.py uz                   # every beat, in seconds and frames

# 2. Companions at film resolution (once)
design/ad/engine/cut_pets.sh                              # → build/ad/pets/<pet>_<pose>/

# 3. Sound (music, SFX, mix) — reads the same timeline
build/ad/.venv/bin/python design/ad/engine/audio/build.py uz en

# 4. Picture
build/ad/.venv/bin/python design/ad/engine/render.py board uz 169       # contact sheet of every beat
build/ad/.venv/bin/python design/ad/engine/render.py still uz 916 5.2   # one frame
build/ad/.venv/bin/python design/ad/engine/render.py video uz 169 --preview --audio build/ad/vo/uz_edit.wav
build/ad/.venv/bin/python design/ad/engine/finals.py                    # all four deliverables
```

## How it fits together

| File | What it owns |
|---|---|
| `timeline.py` | The clock. Every beat is derived from the voice-over's word anchors; picture and sound both read `beats(lang)`. |
| `film.py` | The world: stations (flat faces in 3D, each with a home pose that maps 1:1 to the screen), the camera keys (smoothed at 240 Hz), every scene, the app copy in UZ/EN. |
| `line.py` | Brand.kt's vectors verbatim, the shared `Pen`, the glow, the mark/wordmark/lockup and the reveal clock. |
| `ui.py` | The app's dark tokens and Clay.kt (surfaces, beads, arcs), the tab bar pieces, the orb. |
| `text.py` | HarfBuzz shaping + Skia glyphs: kerning, tracking, per-letter animation, wrapping, emoji/Cyrillic fallback. |
| `pets.py` | Companion sprites and clips (whole sprites only: scale, squash, hop, tilt, fade). |
| `render.py` | Stills, boards, video (worker pool → ffmpeg), motion blur on fast moves, vignette, grain. |
| `finals.py` | Picture × language × format, muxed with the mix. |

Changing a voice block in `vo_edit.py` moves every hit that depends on it — re-run the sound
build afterwards so the cues follow.
