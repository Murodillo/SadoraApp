# The Path — phase 1 brief

Status: waiting for approval. Nothing is rendered yet. Frames are at 60 fps (frame = seconds × 60).
Total length 25.20 s = 1512 frames (f0–f1511). Prompt: `design/ad/sadora-the-path-PROMPT.md`.

---

## 1. What the repo locks

| Source | What it fixes for the film |
|---|---|
| `sadora-client/.../ui/components/Brand.kt` | S outline, pen centre line `MarkStroke`, dot path, the six SADORA strokes (never set in a typeface), view boxes, gradient stops 0 / .45 / 1, dot `#B06CFF`, wipe stroke width 44. Timing: stroke 1500 ms on (.65,0,.25,1); dot at 1250 ms for 600 ms on pop (.34,1.56,.64,1); letters at 1200 ms + 80 ms each, 500 ms on (0,0,.58,1), 8-unit rise; tagline "EVERY WOMAN. EVERY MOMENT." at 1900 ms for 700 ms, 0.18 em SemiBold. AiOrb and SadoraAppTile gradients. |
| `.../design/Colors.kt` | Dark tokens, same as the prompt. `PhaseColors` and `StagePalettes` (see §2.1). |
| `landing/index.html :root` | Radii 8 / 12 / 16 / 22 / 28, curves emphasized / gentle / pop, Figtree + Bricolage Grotesque. |
| `i18n/StringsUz.kt`, `PartnerUz.kt`, `PrescriptionStrings.kt` | Every on-screen string below is the app's own wording (§2 lists where the prompt differed). |
| `db` migration `coin_rules`, contract `Rewards.kt` | Real Gul amounts and badge tiers. |
| `design/pets/` | Six companions: stills 512² RGBA in `cut/`; clips as 220² webp at 12 fps, cut from 1280×720 24 fps mp4s. |
| `landing/img/`, `design/icons3d/` | Clay icons 112² and badges 144² on the landing; 1024² source sheets. |
| — | The app ships no sound files, so the Sadora chime is designed in code (F–A–C, marimba + glass). |

---

## 2. Where the prompt and the app disagree: my proposed fixes

Each fix keeps the film true to what the app actually shows. **Bold** rows need your decision; the others I'll do unless you object.

| # | Prompt says | App says | Proposal |
|---|---|---|---|
| 2.1 | Branch colours Sikl `#FF4F9A`, Homiladorlik `#4FB8FF`, Onalik `#FFC46B`, Menopauza `#8E7BFF` (reused phase colours) | `StagePalettes`: cycle `#7B61FF→#FF6FB8`, pregnancy `#FFB020→#FF8E92`, postpartum `#FF8AA3→#FFB020`, menopause `#63D8FF→#2FBF8F` | Each branch uses its stage's own gradient, start colour leading: violet, amber, pink, cyan. The four stay easy to tell apart and match the app's re-tinted tab. |
| **2.2** | Tab label morphs Sikl → Homiladorlik → Onalik → Menopauza | The stage tab is **Sikl → Homiladorlik → Tiklanish → Salomatlik**. Stage titles: Sikl kuzatuvi / Homiladorlik / Tug'ruqdan keyin / Menopauza. "Onalik" appears nowhere in the app. | **Recommended:** the big branch captions follow the voice (Sikl · Homiladorlik · Onalik · Menopauza), and the tab inside the phone shows the real labels. Captions are ad copy; the tab is the app, pixel-true. Alternative: captions use the app titles and the VO says "tug'ruqdan keyin". |
| 2.3 | Today is "14-kun" (S2), yet Yaqinim shows "Sikl 24-kun · hayzgacha 5 kun" (S7) | — | The film uses **cycle day 24** throughout. It also fits the AI question about tiredness before a period (S5). Ring: days 1–23 filled in their phase colours, day 24 glows, days 25–28 outlined with the "Taxminiy" chip. |
| 2.4 | "Bugun · 14-kun" | `cycleDayOrdinal` = "Sikl 24-kuni" | Label "Bugun · Sikl 24-kuni". |
| 2.5 | "12 kunlik seriya" + a **gold** "Olovli ketma-ketlik" badge | Streak badge tiers 7 / 30 / 100 (bronze / silver / gold). Copy is "N kun ketma-ket". | The streak ticks 6 → **"7 kun ketma-ket"**, the week row Du–Ya fills completely (seven days, one full row), and the **bronze** badge flips in, earned on screen. |
| 2.6 | Gul +10 +10 +3, 340 → 363 | Rules: water goal +10, dose taken +3, mood check-in +5 | The three S3 ticks pay what the app pays: +10 +3 +5, **340 → 358**. |
| 2.7 | Pill in S3 "Temir 30 mg", and the doctor prescribes "Temir 30 mg" again in S6 | — | The S3 pill becomes **"Vitamin D3 · 1 kapsula"**. Iron arrives new from the doctor in S6, so the story moves forward. |
| 2.8 | AI footer "Ma'lumotlaringiz asosida · AI · Bu tashxis emas" | `aiFootnote` = "Ma'lumotlaringiz asosida · AI tomonidan yaratilgan"; answers end "Bu umumiy ma'lumot — tashxis emas." | Use both strings exactly. |
| 2.9 | Doctor window "23:41 qoldi" | `hoursLeft` = "23 soat qoldi" (whole hours while ≥ 1 h) | "23 soat qoldi" with the arc draining slightly. |
| 2.10 | Prescription card | Title "Retsept", line "Maslahat retsepti, rasmiy retsept emas", button "Dorilarimga qo'shish", toast "Dorilar «Dorilarim»ga qo'shildi" | Use these strings. The small disclaimer stays on the card (the honesty rules). |
| 2.11 | Quick word "Choy damlab bering ☕" | Message kinds "☕ Issiq choy", "🏃 Hozir!", "❤️ Yurak" | Her chip "☕ Issiq choy" → reply "🏃 Hozir!" + heart pop. |
| 2.12 | Partner view "Malika · Sikl 24-kun · hayzgacha 5 kun · Energiya past" | "Siklning 24-kuni", "Hayzgacha 5 kun", "Hayzdan oldingi kunlar", "Energiyasi", and the note "Sanalar taxminiy: ular uning yozuvlariga asoslangan." | Use these strings, including the estimate note (honesty rule 1). |
| 2.13 | Mood face 🙂 | The app has clay mood icons (`ic_mood_good.webp`) | Use the app's clay mood icon, not a system emoji. |
| 2.14 | S1 writes the S "in Brand.kt order" within 1.8 s | Brand.kt's reveal takes 2.6 s | S1 is a **compressed** reveal in Brand.kt's order and curves (stroke 0.8 s, letters 0.4 s each). S10 is the exact, millisecond reveal, as the prompt asks. |
| 2.15 | Hero tagline | Brand.kt's tagline is English in every app language | The lock-up stays Brand.kt verbatim ("EVERY WOMAN. EVERY MOMENT."). S9 has just set the Uzbek line in large type. |
| 2.16 | English version "from the same picture" | The app has full EN strings | The EN cut swaps the VO **and** the on-screen copy (StringsEn.kt, EN captions in S8/S9). The picture timing is re-locked to the EN take. |

---

## 3. Beat sheet (frame-exact)

VO anchors are targets. In phase 3 every hit moves to the real take's word onsets.

### S1 Hook · 0.00–1.80 · f0–f107
| f | t | What happens |
|---|---|---|
| 0 | 0.00 | Deep violet stage, light strands streaming toward camera; the dot is a speck far down the z-axis, rushing in (ease-in). Round sub from frame 0. |
| **10** | **0.17** | **SLAM.** The dot fills about half the frame height (~540 px). Squash 1.25×0.80 (f10–14), stretch 0.92×1.08 (f18), settle on the pop curve (f26). A soft shockwave ring expands f10–f40. Sub hit. |
| 14–60 | 0.23–1.00 | Camera eases back (emphasized) from the dot to the lock-up framing; the dot settles into its true place in the mark. |
| 18–66 | 0.30–1.10 | The line writes the S along `MarkStroke` (Brand.kt wipe, DrawEasing, 0.8 s). |
| 30 | 0.50 | VO starts: "Tanangiz jadval emas —" |
| 36–66 | 0.60–1.10 | Nilufar blooms out of the dot: whole-sprite scale 0→1 on the pop curve from the dot's centre, slight tilt. |
| 57–105 | 0.95–1.75 | S·A·D·O·R·A rise 8 units, 80 ms apart (f57/62/67/71/76/81), 0.4 s each. |
| 60 | 1.00 | The pen reaches the tail; the dot re-pops 1.0 → 1.08 → 1.0. |
| 66–96 | 1.10–1.60 | Nilufar wave clip. |
| 96–107 | 1.60–1.80 | Nilufar floats to frame right (gentle); the line leaves the S's tail and heads into depth. |

### S2 Cycle · 1.80–3.40 · f108–f203
| f | t | What happens |
|---|---|---|
| 108–132 | 1.80–2.20 | Camera dives through the lock-up (it falls out of focus) following the line's head. |
| 120–154 | 2.00–2.57 | The line wraps into a ring; 28 clay beads pop on one frame apart (a tick on every other bead). |
| 138 | 2.30 | VO "bosqichlardan" as the ring closes. |
| 150–175 | 2.50–2.92 | Days 1–23 fill in sweep order: period `#FF4F9A` 1–5, follicular `#4FB8FF`, fertile `#FF63C8`, luteal `#8E7BFF`. |
| 168 | 2.80 | Day 24 glows (scale 1.25, pop). Label "Bugun · Sikl 24-kuni". |
| 176 | 2.93 | Days 25–28 stay outlined; the "Taxminiy" chip pops beside them. |

### S3 Today · 3.40–6.00 · f204–f359
| f | t | What happens |
|---|---|---|
| 204–234 | 3.40–3.90 | A phone (dark UI from the tokens) rises out of the ring; the ring stays behind it as a halo. "Xayrli tong, Malika" (f216). AI summary card, lines 55 ms apart (f222), with the footnote "Ma'lumotlaringiz asosida · AI tomonidan yaratilgan". |
| 210 | 3.50 | VO "Har kuni…" |
| **234** | **3.90** | **"suv"**: push to the water ring; the line's light pours in (f234–262), "+250 ml" chip (f240), water pour. |
| **276** | **4.60** | **"dori"**: push to the pill row "Vitamin D3"; the button flips to "Qabul qildim" (paper flip f276–286), check pop, **Sadora chime**. |
| **312** | **5.20** | **"kayfiyat"**: mood row settles on the clay "good" icon (pop f318). |
| 330–359 | 5.50–6.00 | Slight pull-back; the line gathers light from the three ticks. |

### S4 Streak & Gul · 6.00–8.00 · f360–f479
| f | t | What happens |
|---|---|---|
| **360** | **6.00** | **DROP**: doira, claps and shaker enter. The light leaps out of the phone and curls; the clay Gul flower blooms from the curl (f360–390). |
| 384–420 | 6.40–7.00 | Streak card: digit cells roll 6 → **7** (f396), "7 kun ketma-ket"; the week row Du Se Ch Pa Ju Sh Ya fills 55 ms apart. |
| 414–444 | 6.90–7.40 | Coins arc from the flower: +10, +3, +5 land at f420 / f428 / f436; counter 340 → 358 Gul; coin chimes. |
| **444** | **7.40** | The bronze "Olovli ketma-ketlik" badge flips in (Y-rotation, pop). |

### S5 Sadora AI · 8.00–10.40 · f480–f623
| f | t | What happens |
|---|---|---|
| 480–504 | 8.00–8.40 | Camera glides to a floating chat (header "SADORA AI" with the AiOrb from Brand.kt). Nilufar think clip beside it. |
| 486 | 8.10 | VO "Savol…" — the question bubble lands: "Nega hayzdan oldin charchoq sezaman?" |
| 510–582 | 8.50–9.70 | The line becomes the typing cursor: "Oxirgi 3 siklda lyuteal fazada uyqungiz o'rtacha **38 daqiqaga** qisqargan. Kam uyqu va charchoq ko'pincha birga kuzatiladi." ("38 daqiqaga" in accent so the eye lands on it.) Keyboard taps. |
| 582–623+ | 9.70–10.40+ | "Bu umumiy ma'lumot — tashxis emas." + the footnote fade in and stay readable into the move. |

### S6 Doctor · 10.40–13.00 · f624–f779
| f | t | What happens |
|---|---|---|
| 624–648 | 10.40–10.80 | The line threads to a verified doctor card: initials avatar (no face), "Nodira Karimova", "Ginekolog ✓", "★ 4,9". VO "Jiddiy" at f630. |
| 648 | 10.80 | The 24-hour arc "23 soat qoldi". |
| 672 | 11.20 | Prescription card arrives (swish): "Retsept · Temir 30 mg · kuniga 1 marta", plus "Maslahat retsepti, rasmiy retsept emas". |
| 714 | 11.90 | "Dorilarimga qo'shish" pressed (0.96 → 1, tap). |
| 726–756 | 12.10–12.60 | The card rides the line into the meds list under "Vitamin D3". |
| **756** | **12.60** | Lands, toast "Dorilar «Dorilarim»ga qo'shildi", **Sadora chime**. |

### S7 Yaqinim · 13.00–15.40 · f780–f923
| f | t | What happens |
|---|---|---|
| 780–810 | 13.00–13.50 | The line stretches across frame to the loved one's phone. VO "Yaqiningiz" at f786. |
| 800 | 13.33 | Partner view: "Malika · Siklning 24-kuni · Hayzgacha 5 kun · Hayzdan oldingi kunlar · Energiyasi: past", note "Sanalar taxminiy…". |
| 834–852 | 13.90–14.20 | The "☕ Issiq choy" chip flies along the line from her phone to theirs (whoosh). |
| **894** | **14.90** | On "biladi": the reply "🏃 Hozir!" lands, heart pop. |

### S8 Every stage · 15.40–18.40 · f924–f1103 (the biggest camera move)
| f | t | What happens |
|---|---|---|
| 924–960 | 15.40–16.00 | Fast pull-back and crane up, FOV widens; the line splits into four branches. Music lift. |
| **942** | **15.70** | **Sikl**: violet→pink branch lights, `ic_cycle` pops, Anorxon hops on; tab = "Sikl". |
| **984** | **16.40** | **Homiladorlik**: amber branch, `ic_pregnancy`, Laylo; tab = "Homiladorlik". |
| **1026** | **17.10** | **Onalik**: pink branch, `ic_postpartum`, Ohu; tab = "Tiklanish". |
| **1068** | **17.80** | **Menopauza**: cyan branch, `ic_menopause`, Momiq; tab = "Salomatlik". |
| 1068–1103 | 17.80–18.40 | All four held lit. |

The tab bar sits in a phone at the hub of the four branches. Each label cross-morphs on its hit, and the tab icon and tint change with it.

### S9 Type · 18.40–20.40 · f1104–f1223
| f | t | What happens |
|---|---|---|
| 1104–1130 | 18.40–18.83 | The four branches converge into one line, which becomes the baseline. |
| **1116** | **18.60** | "Har bir ayol." in Bricolage 700, gradient fill, −0.035 em, rising 55 ms apart. |
| ~1164 | ~19.40 | "Har bir lahza." (on its VO word). |
| 1150–1215 | 19.17–20.25 | The baseline splits into the four stage colours, then merges back into the brand gradient. |

### S10 Hero · 20.40–23.20 · f1224–f1391 (Brand.kt exact, t0 = f1242)
| f | t | What happens |
|---|---|---|
| 1224–1242 | 20.40–20.70 | Breath: the type dissolves into depth; music drops to a reversed swell. |
| **1242** | **20.70** | **HIT**: five companions hop in and land (squash), Nilufar centre; full chord + low hit. The S stroke starts (1.5 s → f1332). |
| 1314–1368 | 21.90–22.80 | Letters at f1314 / 1319 / 1324 / 1328 / 1333 / 1338, 30 f each. VO "Sadora." at f1314. |
| 1317–1353 | 21.95–22.55 | Dot pops (peak overshoot around f1337). |
| **1332** | **22.20** | Sonic logo: the chime slowed, with a low bell under it. |
| 1340–1391 | 22.33–23.20 | Group happy bounce (happy clips). |
| 1356–1398 | 22.60–23.30 | Tagline fades in. It finishes 6 frames into the end card, which keeps the lock-up. |

Companions sit below and beside the lock-up; the mark's clear space stays empty.

### S11 End card · 23.20–25.20 · f1392–f1511
Lock-up holds. **[signature]** fades in at f1404, then "Android va iOS · O'zbek · Русский · English" at f1416. Companions idle. The chord rings out; audio fades over the last 0.3 s.

---

## 4. Assets plan (phase 2)

- **Fonts**: Bricolage Grotesque (variable, opsz/wght) and Figtree, OFL, from github.com/google/fonts, into `build/ad/fonts/`. Not installed on this Mac yet.
- **Companions**: re-cut the clips from the 1280×720 mp4s at 640 px with `tools/cut_pet_video.py` into `build/ad/pets/`. The 220 px app frames are too small for the hero. Needed: Nilufar wave, think and happy; happy and idle for Anorxon, Laylo, Ohu and Momiq. All of these have source mp4s. Nilufar has no idle mp4, so her idle is the 512² still with whole-sprite breathing. Clips play at 12 fps (each frame held 5 film frames); squash, hop and tilt run at 60.
- **Icons and badges**: the landing webps (112–144 px) go soft above ~120 px on screen. Re-cut `ic_cycle`, `ic_pregnancy`, `ic_postpartum`, `ic_menopause`, `ic_flower_coin`, `ic_water`, `ic_meds`, `ic_mood_good` at SIZE=480 and the streak badge with GRID=2, from their `design/icons3d/` sheets, using `tools/cut_icon_sheet.py`.
- A contact sheet of every asset on the violet stage, for your OK.

## 5. Engine plan (phases 4–5)

Code in `design/ad/engine/`, renders in `build/ad/` (already gitignored).

- `timeline.py`: beats and VO anchors as one table; curves emphasized, gentle, pop and Brand.kt draw/out.
- `camera.py`: one eased spline path for position, target and FOV; a real perspective projection.
- `world.py`: each station is a flat 2D face rendered at native pixel size. Its home pose is computed so that at the camera's home key it maps 1:1 onto the screen; elsewhere it's warped with `cv2.warpPerspective`.
- `line.py`: **one function** draws the glowing gradient stroke from a 3D polyline (head position, width, glow, colour stops). It draws the S, the ring, the curl, the cursor, the threads, the branches and the baseline.
- `pets.py`, `ui.py` (cards from the tokens, radii 12/16/22/28), `type.py` (Bricolage with tracking and gradient fill), `post.py` (depth-of-field by depth slices, sub-frame motion blur only on fast moves, grain, vignette).
- `audio/`: music synth (92 BPM, F major: felt piano, harp/kalimba, round sub, doira from the drop), the SFX cues, the Sadora chime, and a mix with sidechain ducking under every word. Loudness is measured with ffmpeg `ebur128`/`loudnorm`: voice −16 LUFS, master −14 LUFS / −1.25 dBTP.
- Render budget: previews at 960×540 / 30 fps take minutes. Finals at 1080p60 should take roughly 1–2 h per format on the Mac's 12 cores (1512 frames).

## 6. What I need from you

1. **Approve or change** the fixes in §2. Decision 2.2 is the only open one.
2. **Signature** for the end card (S11).
3. **Voice**: an ElevenLabs API key in an env var, so I can audition 2–3 v3 voices, or WAV takes from a native speaker. Until then I can build against the target anchors with a silent guide.
4. **SFX**: a path to your library, or I synthesize all ~100 cues in code.
5. **OK to download the two fonts** (about 1–2 MB, OFL, from github.com/google/fonts).
