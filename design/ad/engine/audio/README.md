# Sadora ad: sound

Everything in the soundtrack is synthesised in code (numpy + scipy.signal). There are no
samples. ffmpeg is used only to cross-check loudness.

## Rebuild

From `design/ad/engine/`:

    ../../../build/ad/.venv/bin/python audio/build.py uz en

Each run reads `timeline.beats(lang)`, the VO edit and its anchors fresh, then overwrites
everything in `build/ad/audio/`. Builds give the same result every time because each
event uses its own seeded RNG. Each language takes about 10 s. After a retime, run the
same command again.

Outputs (48 kHz, stereo, 24-bit):

| file | what |
|---|---|
| `{lang}_mix.wav` | the final mix: −14 LUFS integrated, true peak ≤ −1.25 dBTP |
| `{lang}_music.wav`, `{lang}_sfx.wav` | stems as they sit in the mix: ducked, master gain and limiter applied. VO + music + SFX = mix |
| `{lang}_cues.json` | every SFX cue: `t` (the picture time it is locked to), `beat`, `beat_time`, `offset`, `gain_db`, `pan`, `hero`, `start`/`end` |
| `{lang}_spectrogram.png` | mix spectrogram (8 kHz line), plus 400 ms loudness of VO / music / SFX |
| `chime.wav`, `sonic_logo.wav` | the brand chime and the sonic logo on their own (−3 dBFS peak) |

The build prints the cue table, the worst voice-to-music gap per VO block, the hero cue
levels and the checks (loudness, true peak, DC, energy above 12 kHz, breath silence, stereo
correlation).

## Code

| module | role |
|---|---|
| `synth.py` | DSP kit: modal partials, swept sines, moving band-pass noise (STFT), filters, pan, synthetic reverb, BS.1770 meters, WAV I/O, `Bus` (every event gets ≥ 2.5 ms fades) |
| `instruments.py` | felt piano, harp, kalimba, pad, doira (dum / tak + jingles), clap, shaker, marimba, glass, bell |
| `music.py` | the score at 92 BPM in F. The bar grid is counted back from `B["drop"]`. Chord plan, arrangement, riser and the breath's reversed swell |
| `chime.py` | the chime (F5–A5–C6, marimba + glass, 65 ms apart) and the sonic logo (the same motif at 0.6× speed with pitch kept, plus a low F3 bell) |
| `sfx.py` | about 130 synthesised cues on the picture's beats (`cue_sheet`) |
| `mix.py` | VO chain, ducking, the loudness guarantee, the master and limiter |
| `build.py` | runs all of the above per language and writes the outputs |

## Mix rules (`mix.py`)

- **VO.** High-pass at 80 Hz, +1.5 dB at 3 kHz, 2:1 soft-knee compressor, look-ahead peak
  limiter at −4 dBTP. Normalised to −16 LUFS as it plays (centred, the same signal on L and R).
- **Music ducking.** The music ducks 8 dB from the VO envelope: 40 ms look-ahead attack,
  300 ms release.
- **Loudness guarantee.** In each 400 ms K-weighted window centred at least 100 ms inside a
  VO block and speech-gated (voice within 10 LU of its integrated level, as in BS.1770),
  the music must sit at least 12 dB under the voice. If a window is closer than that, the
  music is pushed down by the shortfall, spread ±0.4 s so it rides whole phrases. The
  check excludes windows that are mostly the pause before a word or the fade after one.
- **Hero cues.** The chime, sonic logo, slam, t0 landing and heart pop dip the music 5 dB
  (50 ms attack, 400 ms release) and are never ducked themselves. All other SFX duck 6 dB
  under words.
- **Master.** Gain to −14 LUFS, then a 4× oversampled look-ahead true-peak limiter. The
  limiter's gain is applied to every stem, so the stems sum to the mix.

## Tuning

- **Stem balance inside the music:** `music.GAIN`.
- **Music and SFX bus levels:** `mix.MUSIC_GAIN`, `mix.SFX_GAIN`.
- **Duck depths:** `mix.MUSIC_DUCK`, `mix.SFX_DUCK`, `mix.HERO_DUCK`.
- **Each cue's level:** its `add(...)` line in `sfx.cue_sheet`.
