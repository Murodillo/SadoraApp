# The Path — Sadora
*A Sadora launch ad, built entirely in code*
**THE PROMPT · 25.2 s · 16:9 + 9:16 · 60 fps**

---

## How to use it
Open Claude Code (or any coding agent that can run Python) **in the `SadoraApp/` folder** so it can read the real brand files, and paste the prompt below. Fill in the [brackets]. Work in phases and approve each one before the next: brief, assets, voice, engine, first preview, fixes, 9:16, finals. Every frame is rendered by code: no stock footage, no AI video generation.

## What you need
- Python 3 with numpy, Pillow and OpenCV, plus ffmpeg.
- Fonts: **Bricolage Grotesque** (display) and **Figtree** (UI), both free on Google Fonts — the same pair the app and landing use.
- Already in the repo: the logo (`design/logo/`), the companions (`design/pets/`), the clay 3D icons (`design/icons3d/`, `landing/img/*.webp`), the colour tokens (`landing/index.html` `:root`, mirroring `design/Colors.kt`) and the logo motion (`sadora-client/.../ui/components/Brand.kt`).
- An ElevenLabs account (or a native Uzbek voice actor) for the voice-over.
- An SFX library of your own (soft whooshes, ticks, taps, pops, water, paper, gentle impacts).
- Optional: DaVinci Resolve for a layered, editable project at the end.

---

## The prompt

```
GOAL
Make a launch ad for Sadora, a women's health app, built entirely in code (a Python
renderer with numpy / Pillow / OpenCV and a real perspective camera), 23.2 s + a 2 s
end card, 60 fps, 16:9 (1920×1080) and a 9:16 (1080×1920) re-layout. One continuous
camera flight, zero hard cuts. Premium, warm, calm-confident, readable on a phone,
a hook that lands in the first second. Work from the real brand files in this repo;
read them before you design anything.

IDEA
"The Path." Sadora's mark is an S written in ONE continuous stroke, ending in a dot
that pops. Here that stroke never lifts: one glowing line in the brand gradient
(#B06CFF → #8A8CFF → #F68CCB) does every job. It writes the S and reveals the
wordmark, becomes the 28-day cycle ring, fills today's water goal and ticks the
pill, curls into a Gul flower for the streak, types the AI's answer, threads a
verified doctor's prescription into your meds, stretches across to a loved one's
phone (Yaqinim), splits into the four life stages (Sikl, Homiladorlik, Onalik,
Menopauza), underlines the tagline and writes the S again as the companions land.
Every feature is a station along the line; the camera flies from one to the next.
The point the film makes: a woman's body isn't a calendar, it moves through stages,
and Sadora changes with her.

LOOK
A deep violet stage: radial #2A1D5C at centre falling to #0E0B1F at the edges, one
soft orb glow (#C9A1FF → #8E5CFF → #5B3FE0 → #3A2A9A), never neon. Sadora's visual
language made physical: soft clay 3D shapes with a faint white rim light
(the app's "clay" icons), rounded radii 12 / 16 / 22 / 28 px on UI cards.
Brand gradient #B06CFF / #8A8CFF / #F68CCB for the line and display type.
App dark-mode UI rebuilt from the tokens: bg #0F0D24, surface #1B1838,
surface-2 #272348, line #2E2A52, text #F3F0FA, muted #A39DBF, primary #8E7BFF,
secondary #FF7EC4, accent #63D8FF, success #3FCF98, warm #FFC46B.
Cycle phase colours (PhaseColors): period #FF4F9A, follicular #4FB8FF,
fertile #FF63C8, luteal #8E7BFF. Life-stage branches: Sikl #FF4F9A,
Homiladorlik #4FB8FF, Onalik #FFC46B, Menopauza #8E7BFF.
Bricolage Grotesque 700 for display lines (tight, -0.035em), Figtree for all UI
strings. Motion uses the app's own curves: emphasized cubic-bezier(.2,0,0,1),
gentle (.4,0,.2,1), pop (.34,1.56,.64,1) with overshoot, 55 ms stagger.
Depth of field, real motion blur, light grain; text always holds long enough to read.

BEAT SHEET (seconds, 60 fps)
S1  0.00–1.80  Hook. The S's dot rushes from depth through violet light strands and
               SLAMS at 0.17 s, BIG (about half the frame): real squash and stretch
               on the pop curve, a soft shockwave ring, a sub hit. The camera eases
               back while the line writes the S (Brand.kt order: S stroke, dot pop,
               letters rising 80 ms apart). Nilufar, the lotus companion, blooms out
               of the dot and waves (pets/anim wave clip), then floats aside.
S2  1.80–3.40  The cycle. The line dives into the deep world and wraps into a ring of
               28 clay beads. Period days fill #FF4F9A, the fertile window #FF63C8,
               a glowing bead marks "Bugun · 14-kun". Future days stay OUTLINED only
               with a small "Taxminiy" chip — in Sadora an estimate is never drawn
               as a fact.
S3  3.40–6.00  Today. A phone rises out of the ring: "Xayrli tong, Malika", the AI
               summary card, then three ticks landing on the three spoken words:
               the water ring fills with the line's light (+250 ml), the pill row
               "Temir 30 mg" flips to "Qabul qildim" with the Sadora chime, the mood
               face settles on 🙂. Camera pushes in on each.
S4  6.00–8.00  Streak & Gul. Drop (6.00). The light leaps out and curls into a clay
               flower (Gul, the app's reward) and blooms. "12 kunlik seriya" in fixed
               digit cells, the week row fills Du→Ya, +10 +10 +3 Gul coins arc into
               the counter (340 → 363), the gold "Olovli ketma-ketlik" badge flips in
               (7.40). Badges are for habits, never for body numbers.
S5  8.00–10.40 Sadora AI. A floating chat: "Nega hayzdan oldin charchoq sezaman?"
               The line becomes the typing cursor and writes the answer ("Oxirgi 3
               siklda lyuteal fazada uyqu o'rtacha 38 daqiqaga qisqargan…"); the
               footer "Ma'lumotlaringiz asosida · AI · Bu tashxis emas" stays legible.
               Nilufar plays her think clip beside it.
S6  10.40–13.00 Doctor. The line threads to a verified doctor card: fictional
               "Nodira Karimova · Ginekolog ✓ · 4,9". The 24-hour window counts down
               ("23:41 qoldi"), a prescription card arrives ("Temir 30 mg · kuniga
               1 marta"), "Dorilarimga qo'shish" is pressed and the card rides the
               line into the meds list (12.60), chime again.
S7  13.00–15.40 Yaqinim. The line stretches across the frame to a second phone, the
               loved one's view: "Malika · Sikl 24-kun · hayzgacha 5 kun · Energiya
               past". A quick-word chip flies over: "Choy damlab bering ☕". On the
               held pause in the voice, the reply lands: "Hozir!" with a heart pop
               (14.90).
S8  15.40–18.40 Every stage. A fast pull-back: the line splits into four glowing
               branches, each lighting on its spoken word with its clay icon
               (landing/img ic_cycle, ic_pregnancy, ic_postpartum, ic_menopause) and
               a companion: Sikl (Anorxon, 15.70), Homiladorlik (Laylo the stork,
               16.40), Onalik (Ohu the fawn, 17.10), Menopauza (Momiq the kitten,
               17.80). As each lights, the app's bottom tab label morphs to match —
               the whole tab changes per stage, that's the feature.
S9  18.40–20.40 Type. The four branches converge into one: "Har bir ayol." /
               "Har bir lahza." in Bricolage Grotesque with gradient fill, the line as
               the baseline, splitting into the four stage colours and merging back
               into the brand gradient.
S10 20.40–23.20 Hero. A breath (20.40), then all five companions hop in and land ON
               the hit (20.70), Nilufar centre. The line writes the S exactly as
               Brand.kt does (S in 1.5 s, dot pops at 1.25 s with overshoot, letters
               from 1.2 s at 80 ms, tagline at 1.9 s), "SADORA" (21.90), the Sadora
               sonic logo, a happy group bounce.
S11 23.20–25.20 End card: [your signature]. Small line under it:
               "Android va iOS · O'zbek · Русский · English".

VOICE-OVER
Uzbek is the master (the app is Uzbek-first); render an English version from the
same picture. A warm, calm, confident young female voice — a friend who happens to
know medicine, never a commercial announcer. ElevenLabs Eleven v3 for Uzbek:
audition 2–3 voices on the full script; if the Uzbek doesn't sound native, record
a native speaker instead. The whole script as one flowing read, natural speed,
2 takes; keep the word-perfect one and cut it only at its own pauses:

UZ: "Tanangiz jadval emas — u bosqichlardan o'tadi. Har kuni bir kichik qadam:
suv, dori, kayfiyat. Savol tug'ilsa — Sadora tushuntiradi. Jiddiy bo'lsa —
shifokor javob beradi. Yaqiningiz esa taxmin qilmaydi… biladi. Sikl,
homiladorlik, onalik, menopauza. Har bir ayol. Har bir lahza. Sadora."

EN: "Your body isn't a calendar — it moves through stages. One small step a day:
water, meds, mood. Got a question? Sadora explains. Something serious? A doctor
answers. And the people who love you don't guess… they know. Cycle, pregnancy,
motherhood, menopause. Every woman. Every moment. Sadora."

Target anchors (re-lock the picture to the real take): 0.50 start · "bosqichlardan"
2.30 · "Har kuni" 3.50 · "suv / dori / kayfiyat" 3.90 / 4.60 / 5.20 · "Savol" 8.10 ·
"Jiddiy" 10.50 · "Yaqiningiz" 13.10 · "biladi" 14.90 · "Sikl" 15.70 · "Har bir ayol"
18.60 · "Sadora." 21.90.

MUSIC
Original, made in code: warm, modern, premium (never kiddy, never clinical),
92 BPM in F major. Felt piano and harp / kalimba plucks with a round sub from
frame 0; a soft doira (Uzbek frame drum) pattern, claps and shaker from the drop
(6.00); a lift on the four-way split (15.40); a breath (20.40) with only a reversed
swell; a full chord and a low hit on 20.70, ringing under the end card.

SOUND
About 100 cues from [your SFX library], every visible move has a sound: bead
ticks for the cycle ring, a water pour for the ring fill, a soft paper flip for
the pill, coin chimes for Gul, keyboard taps for the AI, a gentle swish for the
prescription, a heart pop for "Hozir!". Hero cues: if the app ships its own
success / notification sounds, use them; otherwise design ONE Sadora chime in
code (three rising notes, F–A–C, marimba + glass) and use it for every "done"
moment (4.60, 12.60) and, slowed with a low bell under it, as the sonic logo
(22.20). The music dips under the hero cues and the voice leaves them room.
Mix: voice about –16 LUFS, music ≥ 12 dB under every word, master –14 LUFS /
–1.25 dBTP.

BRAND
The mark from design/logo/ (sadora-monogram.svg, sadora-app-icon*.svg) — untouched
and pixel-exact at rest, clear space kept empty; the S is animated only by revealing
its own path, exactly as Brand.kt does. The companions are the app's own clay
renders: use design/pets/cut/*.png and the clips in design/pets/anim/ (idle,
happy, think, wave, sleep); if a pose is missing, cut it from
design/pets/<name>_poses.png with tools/cut_pet_sheet.py and tools/pet_matting.py.
Animate them only as whole sprites and their own clips (squash, stretch, hop,
tilt) — never redrawn or recoloured. Icons and badges from landing/img/*.webp and
design/icons3d/. App UI rebuilt from the tokens with fictional names and numbers.

HONESTY RULES (the app's own, keep them on screen)
- Estimates are always outlined and labelled "Taxminiy"; never shown as facts.
- AI output always carries "Ma'lumotlaringiz asosida · AI"; no diagnosis, no
  cause-and-effect claims ("ko'pincha birga kuzatilgan", not "sabab").
- No numbers that judge the body; no badges for weight or health metrics.
- No unverifiable claims ("thousands of women…", "made with gynecologists"),
  no payment brands or store mentions in the film.
- Doctors and users are fictional; the ✓ means "verified" only.

PROCESS
1. Brief with frame-exact timings. 2. Voice first, then lock the picture hits to its
words. 3. One camera path through a 3D world, every station a flat face with a home
pose that maps 1:1 onto the screen. 4. Companions + type, UI, world and sound in
parallel, the line drawn by ONE shared function that also draws the S. 5. Preview,
a frame-by-frame motion check, one fix round. 6. The 9:16 re-layout and the English
VO version, then, on request, a layered Resolve project.
```

---

## What makes the difference
- **The logo is the path.** The S is already one stroke with a popping dot, so the line that carries the whole film *is* the brand — the opening and closing reveals are the app's real motion, to the millisecond.
- **A hook that lands big.** The dot slams at about twice its logo size, then the camera settles onto the lock-up.
- **The stage switch is the hero feature.** Sixteen seconds in, the line splits into four life stages and the tab label changes with each one. That's what sets Sadora apart from period trackers, so give it the biggest camera move.
- **Honest on screen.** The outlined "Taxminiy" days and the "Bu tashxis emas" footer come from the app's own UI. They make the ad more trustworthy, so don't edit them out.
- **One flowing voice-over in Uzbek first.** Record one take that fills the film and cut it only at its pauses. Let the picture follow the words, and fit the English version to the same picture.
- **Real companions from real parts.** Nilufar, Laylo, Ohu, Momiq and Anorxon move only through their own renders and clips. Check every move frame by frame before anyone sees it.
