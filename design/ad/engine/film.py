"""
"The Path": the world, the stations, the camera flight and the light.

Every station is a flat face in one 3D world, with a home pose that maps it 1:1 onto the
screen. The camera flies from home to home on the app's curves; the line runs between
them as light. `Film(lang, fmt).frame(t)` returns one RGBA frame as a numpy array.
"""
import functools, math
import numpy as np
import skia

from core import (BRAND, C, DIST, EMPH, GENTLE, IN, LINEAR, OUT, POP, STAGES, Camera, Plane, at,
                  bell, brand_shader, clamp, drawable, hexc, home, lerp, lerpv, mix, paint, ph,
                  ramp, smooth, tw)
import line as L
import pets as PET
import text as T
import timeline as TL
import ui as U


class Fmt:
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.portrait = H > W


FMT_169 = Fmt(1920, 1080)
FMT_916 = Fmt(1080, 1920)

# Copy, from the app's own strings (StringsUz, PartnerUz, PrescriptionStrings, ...).
COPY = {
    "uz": dict(
        today="Bugun", cycle_day="Sikl 24-kuni", estimate="Taxminiy",
        greet="Xayrli tong, Malika", greet_sub="Sikl 24-kuni · Hayzdan oldingi kunlar",
        ai_title="SADORA AI", ai_summary="Oxirgi sikllarda shu kunlarda energiyangiz pastroq bo'lgan.",
        ai_foot="Ma'lumotlaringiz asosida · AI tomonidan yaratilgan",
        water="Suv", water_of="{} / 2 000 ml", add_water="+250 ml",
        pill="Vitamin D3", pill_sub="1 kapsula · 09:00", taken="Qabul qildim",
        mood="Kayfiyat", streak="{} kun ketma-ket", week=["Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya"],
        gul="{} Gul", gained="+{} Gul", badge="Olovli ketma-ketlik", tier="Bronza",
        chat_sub="SADORA AI · shaxsiy yordamchingiz",
        question="Nega hayzdan oldin charchoq sezaman?",
        answer=("Oxirgi 3 siklda lyuteal fazada uyqungiz o'rtacha 38 daqiqaga qisqargan. "
                "Kam uyqu va charchoq ko'pincha birga kuzatiladi."),
        answer_hi="38 daqiqaga",
        not_dx="Bu umumiy ma'lumot — tashxis emas.",
        doctor="Nodira Karimova", specialty="Ginekolog", rating="4,9", hours="23 soat qoldi",
        rx_title="Retsept", rx_drug="Temir 30 mg", rx_dose="kuniga 1 marta · 30 kun",
        rx_note="Maslahat retsepti, rasmiy retsept emas", rx_add="Dorilarimga qo'shish",
        meds_title="Dorilarim", meds_toast="Dorilar «Dorilarim»ga qo'shildi", from_doctor="Shifokor retseptlari",
        partner_title="Yaqinim", her="Malika", p_day="Siklning 24-kuni", p_period="Hayzgacha 5 kun",
        p_phase="Hayzdan oldingi kunlar", p_energy="Energiyasi", p_low="past",
        p_note="Sanalar taxminiy: ular uning yozuvlariga asoslangan.",
        tea="☕ Issiq choy", on_it="🏃 Hozir!", asked="Malika so'radi",
        stages=["Sikl", "Homiladorlik", "Onalik", "Menopauza"],
        tabs=["Bugun", "Ruhiyat", "Chat", None, "Premium"],
        journey=["Sikl", "Homiladorlik", "Tiklanish", "Salomatlik"],
        line1="Har bir ayol.", line2="Har bir lahza.",
        signature="sadora.app", platforms="Android va iOS · O'zbek · Русский · English",
    ),
    "en": dict(
        today="Today", cycle_day="Cycle day 24", estimate="Predicted",
        greet="Good morning, Malika", greet_sub="Cycle day 24 · The days before your period",
        ai_title="SADORA AI", ai_summary="In your last cycles your energy was lower on these days.",
        ai_foot="Based on your data · written by AI",
        water="Water", water_of="{} / 2,000 ml", add_water="+250 ml",
        pill="Vitamin D3", pill_sub="1 capsule · 09:00", taken="Taken",
        mood="Mood", streak="{} days running", week=["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"],
        gul="{} Gul", gained="+{} Gul", badge="On fire", tier="Bronze",
        chat_sub="SADORA AI · your personal assistant",
        question="Why do I feel tired before my period?",
        answer=("Over your last 3 cycles, your sleep in the luteal phase was 38 minutes shorter on "
                "average. Short sleep and tiredness are often seen together."),
        answer_hi="38 minutes",
        not_dx="This is general information — not a diagnosis.",
        doctor="Nodira Karimova", specialty="Gynecologist", rating="4.9", hours="23 hours left",
        rx_title="Prescription", rx_drug="Iron 30 mg", rx_dose="once a day · 30 days",
        rx_note="Doctor's advice, not an official prescription", rx_add="Add to my medications",
        meds_title="My medications", meds_toast="Added to your medications", from_doctor="Doctors' prescriptions",
        partner_title="Yaqinim", her="Malika", p_day="Cycle day 24", p_period="Period in 5 days",
        p_phase="The days before her period", p_energy="Energy", p_low="low",
        p_note="Dates are estimates based on her records.",
        tea="☕ A hot tea", on_it="🏃 On my way!", asked="Malika asked",
        stages=["Cycle", "Pregnancy", "Motherhood", "Menopause"],
        tabs=["Today", "Mind", "Chat", None, "Premium"],
        journey=["Cycle", "Pregnant", "Recovery", "Health"],
        line1="Every woman.", line2="Every moment.",
        signature="sadora.app", platforms="Android & iOS · O'zbek · Русский · English",
    ),
}
STAGE_ICONS = ["ic3d_cycle", "ic3d_pregnancy", "ic3d_postpartum", "ic3d_menopause"]
STAGE_PETS = ["anorxon", "laylo", "ohu", "momiq"]
PHASE_OF_DAY = lambda d: "period" if d <= 5 else "follicular" if d <= 9 else "fertile" if d <= 15 else "luteal"
TODAY = 24


def _cubic(p0, p1, p2, p3, n=64):
    k = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = map(lambda v: np.asarray(v, np.float64), (p0, p1, p2, p3))
    return (1 - k) ** 3 * p0 + 3 * (1 - k) ** 2 * k * p1 + 3 * (1 - k) * k ** 2 * p2 + k ** 3 * p3


class Station:
    def __init__(self, name, plane, span, draw, light=None, fade=0.25, close_until=-1.0):
        self.name, self.plane, self.span, self.draw_fn, self.light_fn, self.fade = name, plane, span, draw, light, fade
        # Until this time the camera may sit right on the plane (the slam) without it fading.
        self.close_until = close_until

    def alpha(self, t):
        a, b = self.span
        return min(ph(t, a - self.fade, a) if self.fade > 0 else float(t >= a),
                   1 - ph(t, b, b + self.fade) if self.fade > 0 else float(t <= b))


class Film:
    def __init__(self, lang="uz", fmt=FMT_169):
        self.lang, self.fmt = lang, fmt
        self.B = TL.beats(lang)
        self.S = COPY[lang]
        self.W, self.H = fmt.W, fmt.H
        self._build_world()
        self._build_camera()

    # ------------------------------------------------------------ world layout

    def _build_world(self):
        W, H, B, P = self.W, self.H, self.B, self.fmt.portrait
        # Station planes: the flight runs right and forward (down and forward in 9:16).
        lat = (lambda x, y: (x, y)) if not P else (lambda x, y: (y * 0.35, x * 0.9))
        self.p_lock = Plane((0, 0, 0), W, H)
        self.p_cycle = Plane((0, 0, 2400), W, H)
        x4, y4 = lat(1500, -80)
        self.p_streak = Plane((x4, y4, 3500), W, H, yaw=-0.16 if not P else 0.0, pitch=0.0 if not P else 0.12)
        x5, y5 = lat(3000, 60)
        self.p_ai = Plane((x5, y5, 4500), W, H, yaw=-0.10 if not P else 0.0, pitch=0.0 if not P else 0.08)
        x6, y6 = lat(4500, -60)
        self.p_doc = Plane((x6, y6, 5500), W, H, yaw=-0.10 if not P else 0.0, pitch=0.0 if not P else 0.08)
        x7, y7 = lat(6100, 0)
        self.p_love = Plane((x7, y7, 6500), W, H)
        # Her phone sits at this point of the Yaqinim station; the stages fan out from it.
        hx, hy = self._her_phone_xy()
        hub = self.p_love.world(hx, hy)
        self.p_stages = Plane(hub + np.array([0, -H * 0.30 * 1.8 if not P else -H * 0.22 * 1.8, 8.0]), W, H, scale=1.8)
        x9, y9 = lat(8300, 0)
        self.p_type = Plane((x9 + (0 if not P else 0), y9, 8200), W, H)
        x10, y10 = lat(9900, 0)
        self.p_hero = Plane((x10, y10, 9400), W, H)

        B = self.B
        sc = B["scenes"]
        self.stations = [
            Station("lockup", self.p_lock, (0.0, sc["S2"][0] + 0.25), self.draw_lockup, self.light_lockup, fade=0.0,
                    close_until=1.1),
            Station("cycle", self.p_cycle, (sc["S2"][0] - 0.1, sc["S3"][1] + 0.45), self.draw_cycle, self.light_cycle),
            Station("streak", self.p_streak, (sc["S4"][0] - 0.15, sc["S5"][0] + 0.35), self.draw_streak, self.light_streak),
            Station("ai", self.p_ai, (sc["S5"][0] - 0.25, sc["S6"][0] + 0.35), self.draw_ai, self.light_ai),
            Station("doctor", self.p_doc, (sc["S6"][0] - 0.25, sc["S7"][0] + 0.35), self.draw_doctor, self.light_doctor),
            Station("love", self.p_love, (sc["S7"][0] - 0.25, sc["S8"][0] + 0.45), self.draw_love, self.light_love),
            Station("stages", self.p_stages, (sc["S8"][0] - 0.05, sc["S9"][0] + 0.35), self.draw_stages, self.light_stages),
            Station("type", self.p_type, (sc["S9"][0] - 0.25, B["breath"][1] + 0.05), self.draw_type, self.light_type),
            Station("hero", self.p_hero, (B["breath"][0] - 0.1, TL.LENGTH + 1), self.draw_hero, self.light_hero, fade=0.2),
        ]
        rng = np.random.default_rng(7)
        n = 520
        span_x = (-1500, 11500) if not P else (-1600, 1800)
        span_y = (-1400, 1400) if not P else (-1200, 9000)
        self.motes = np.stack([rng.uniform(*span_x, n), rng.uniform(*span_y, n), rng.uniform(-1200, 11000, n)], 1)
        self.mote_hue = rng.uniform(0, 1, n)
        self.mote_size = rng.uniform(0.6, 1.8, n)
        m = 90
        ang = rng.uniform(0, 2 * np.pi, m)
        rad = rng.uniform(220, 1400, m)
        self.strands = np.stack([np.cos(ang) * rad, np.sin(ang) * rad, rng.uniform(0, 1, m)], 1)

    def _her_phone_xy(self):
        W, H = self.W, self.H
        return (W * 0.27, H * 0.52) if not self.fmt.portrait else (W * 0.30, H * 0.26)

    # ------------------------------------------------------------ camera

    def _pose(self, plane, u=None, v=None, dist=1.0, du=0.0, dv=0.0):
        u = plane.w / 2 if u is None else u
        v = plane.h / 2 if v is None else v
        n = np.cross(plane.uy, plane.ux)
        n /= np.linalg.norm(n)
        s = np.linalg.norm(plane.ux)
        tgt = plane.world(u, v)
        pos = tgt + n * DIST * s * dist + plane.ux * du + plane.uy * dv
        return pos, tgt

    def _build_camera(self):
        B, W, H, P = self.B, self.W, self.H, self.fmt.portrait
        lay = self.lockup_layout()
        dot_u, dot_v = lay["dot"]
        keys = []

        def key(t, pose, ease=EMPH):
            keys.append((t, np.asarray(pose[0], np.float64), np.asarray(pose[1], np.float64), ease))

        # S1: tight on the dot as it slams, then ease back onto the lock-up.
        near = self._pose(self.p_lock, dot_u, dot_v, dist=0.040)
        key(0.0, near, LINEAR)
        key(B["slam"] + 0.06, near, LINEAR)
        key(1.02, self._pose(self.p_lock, dist=1.0), EMPH)
        key(B["dive"][0], self._pose(self.p_lock, dist=0.95), GENTLE)
        # S2: dive through the lock-up to the ring.
        key(B["dive"][1], self._pose(self.p_cycle, dist=1.0), EMPH)
        key(B["phone_rise"][0], self._pose(self.p_cycle, dist=0.97), GENTLE)
        # S3: the phone; a push on each of the three ticks.
        ph_lay = self.phone_layout()
        key(B["phone_rise"][1], self._pose(self.p_cycle, W / 2, ph_lay["frame_v"], dist=1.0), EMPH)
        key(B["push_water"] + 0.22, self._pose(self.p_cycle, *ph_lay["water_c"], dist=0.66), EMPH)
        key(B["push_meds"] + 0.22, self._pose(self.p_cycle, *ph_lay["pill_c"], dist=0.66), EMPH)
        key(B["push_mood"] + 0.22, self._pose(self.p_cycle, *ph_lay["mood_c"], dist=0.66), EMPH)
        key(B["leap"], self._pose(self.p_cycle, *ph_lay["mood_c"], dist=0.72), GENTLE)
        # S4 → S7: station to station.
        key(B["bloom_gul"][0] + 0.22, self._pose(self.p_streak, dist=1.0), EMPH)
        key(B["glide_ai"][0], self._pose(self.p_streak, dist=0.95), GENTLE)
        key(B["glide_ai"][1], self._pose(self.p_ai, dist=1.0), EMPH)
        key(B["thread"][0], self._pose(self.p_ai, dist=0.94), GENTLE)
        dl = self.doctor_layout()
        dx_, dy_, dw_, dh_ = dl["card"]
        card_c = (dx_ + dw_ / 2, dy_ + dh_ / 2)
        cd = 0.62 if not P else 0.95
        key(B["thread"][1], self._pose(self.p_doc, *card_c, dist=cd), EMPH)
        key(B["rx"] - 0.12, self._pose(self.p_doc, *card_c, dist=cd - 0.04), GENTLE)
        key(B["rx"] + 0.32, self._pose(self.p_doc, dist=1.0), EMPH)
        key(B["ride"][0], self._pose(self.p_doc, dist=0.95), GENTLE)
        key(B["stretch"][0] + 0.10, self._pose(self.p_doc, dist=0.93), GENTLE)
        hx, hy = self._her_phone_xy()
        key(B["stretch"][0] + 0.45, self._pose(self.p_love, hx + (W * 0.10 if not P else 0), hy + (0 if not P else H * 0.08), dist=0.92), EMPH)
        key(B["stretch"][1] + 0.2, self._pose(self.p_love, dist=1.0), EMPH)
        pl = self.partner_layout()
        key(B["chip_fly"][0] + 0.10, self._pose(self.p_love, *pl["focus"], dist=0.70), EMPH)
        key(B["pullback"][0], self._pose(self.p_love, *pl["focus"], dist=0.64), GENTLE)
        # S8: the pull-back — out, up and tilting down onto the four branches.
        key(B["pullback"][1], self._pose(self.p_stages, dist=1.06, dv=-150 if not P else -200), EMPH)
        key(B["stages_hold"], self._pose(self.p_stages, dist=1.02, dv=-90 if not P else -140), GENTLE)
        # S9: along the converged line to the type.
        key(B["converge"][1] + 0.10, self._pose(self.p_type, dist=1.0), EMPH)
        key(B["breath"][0], self._pose(self.p_type, dist=0.94), GENTLE)
        # S10–S11: the hero, with a slow settle.
        key(B["breath"][1], self._pose(self.p_hero, dist=1.04), EMPH)
        key(B["signature"], self._pose(self.p_hero, dist=1.0), GENTLE)
        key(TL.LENGTH, self._pose(self.p_hero, dist=0.985), LINEAR)
        self.cam_keys = keys

        # Sample at 240 Hz, then smooth the path so no move starts or stops with a kink.
        ts = np.arange(0, TL.LENGTH + 1e-9, 1 / 240)
        pos = np.zeros((len(ts), 3)); tgt = np.zeros((len(ts), 3))
        for i, t in enumerate(ts):
            pos[i], tgt[i] = self._raw_cam(t)
        sig = 0.05 * 240
        r = int(sig * 3)
        kern = np.exp(-0.5 * (np.arange(-r, r + 1) / sig) ** 2)
        kern /= kern.sum()
        guard = int((B["slam"] + 0.12) * 240)

        def sm(a):
            pad = np.pad(a, ((r, r), (0, 0)), mode="edge")
            out = np.stack([np.convolve(pad[:, k], kern, mode="valid") for k in range(3)], 1)
            out[:guard] = a[:guard]
            return out
        self.cam_ts, self.cam_pos, self.cam_tgt = ts, sm(pos), sm(tgt)

    def _raw_cam(self, t):
        ks = self.cam_keys
        if t <= ks[0][0]:
            return ks[0][1], ks[0][2]
        for (t0, p0, g0, _), (t1, p1, g1, e) in zip(ks, ks[1:]):
            if t <= t1:
                k = e(ph(t, t0, t1))
                return p0 + (p1 - p0) * k, g0 + (g1 - g0) * k
        return ks[-1][1], ks[-1][2]

    def camera(self, t):
        i = np.searchsorted(self.cam_ts, t)
        i = min(max(i, 1), len(self.cam_ts) - 1)
        k = (t - self.cam_ts[i - 1]) / (self.cam_ts[i] - self.cam_ts[i - 1])
        pos = self.cam_pos[i - 1] * (1 - k) + self.cam_pos[i] * k
        tgt = self.cam_tgt[i - 1] * (1 - k) + self.cam_tgt[i] * k
        # The slam: a short, decaying shake.
        s = t - self.B["slam"]
        if 0 < s < 0.35:
            amp = 3.0 * math.exp(-s * 14)
            pos = pos + np.array([math.sin(s * 90) * amp, math.cos(s * 70) * amp, 0])
        cam = Camera(pos, tgt, self.W, self.H)
        cam.focus = float(np.linalg.norm(tgt - pos))
        return cam

    # ------------------------------------------------------------ frame

    def frame(self, t, surface=None):
        W, H = self.W, self.H
        surf = surface or skia.Surface(W, H)
        c = surf.getCanvas()
        cam = self.camera(t)
        self.draw_background(c, cam, t)
        if t < self.B["slam"] + 0.35:
            self._strands(c, cam, t)
        live = [s for s in self.stations
                if s.alpha(t) > 0.003 and s.plane.visible(cam, near=2.0 if t < s.close_until else 60.0)]
        live.sort(key=lambda s: -s.plane.depth(cam))
        for s in live:
            self._draw_station(c, cam, s, t)
        self.draw_world_light(c, cam, t)
        for s in live:
            if s.light_fn:
                self._draw_station(c, cam, s, t, light=True)
        img = surf.makeImageSnapshot()
        return img.toarray(colorType=skia.kRGBA_8888_ColorType)

    def coc(self, cam, depth):
        """Depth of field: blur radius in pixels for a station at camera depth `depth`."""
        f = cam.focus
        return clamp(abs(depth - f) / max(depth, 1.0) * 9.0, 0.0, 14.0)

    def _draw_station(self, c, cam, s, t, light=False):
        a = s.alpha(t)
        M, depth = s.plane.matrix(cam)
        # Fade a plane as the camera closes on it from the front — it must never fill the
        # frame as a smear on the way through.
        if t >= s.close_until:
            a *= clamp((depth - 140) / 500)
        if a <= 0.003:
            return
        blur = self.coc(cam, depth)
        c.save()
        layer = None
        if blur > 0.7 or a < 0.999:
            q = skia.Paint()
            if blur > 0.7:
                q.setImageFilter(skia.ImageFilters.Blur(blur, blur))
            q.setAlphaf(a)
            if light:
                q.setBlendMode(skia.BlendMode.kPlus if False else skia.BlendMode.kSrcOver)
            c.saveLayer(None, q)
            layer = True
        c.concat(M)
        c.clipRect(skia.Rect.MakeWH(s.plane.w, s.plane.h).makeOutset(s.plane.w * 0.6, s.plane.h * 0.6))
        self._t = t
        (s.light_fn if light else s.draw_fn)(c, t)
        if layer:
            c.restore()
        c.restore()

    # ------------------------------------------------------------ background

    def draw_background(self, c, cam, t):
        W, H = self.W, self.H
        c.drawPaint(skia.Paint(Shader=skia.GradientShader.MakeRadial(
            skia.Point(W / 2, H * 0.42), max(W, H) * 0.78, [hexc(C["deep1"]), hexc(C["deep2"])])))
        # One soft orb glow, far behind everything; it drifts with the flight's parallax.
        orb_w = cam.pos + np.array([0, 0, 0]) * 0
        o, z = cam.project(np.array([cam.pos[0] * 0.92 + 500, -350.0, cam.pos[2] + 9000]))
        r = max(W, H) * 0.42
        c.drawCircle(o[0], o[1], r, skia.Paint(AntiAlias=True, Shader=skia.GradientShader.MakeRadial(
            skia.Point(o[0], o[1]), r,
            [hexc(C["orb1"], 0.16), hexc(C["orb2"], 0.13), hexc(C["orb3"], 0.08), hexc(C["orb4"], 0.0)],
            [0.0, 0.35, 0.7, 1.0])))
        # Dust in the light, for depth: soft discs that blur away from the focus plane.
        pts, zs = cam.project(self.motes)
        ok = (zs > 80) & (zs < 9000)
        ok &= (pts[:, 0] > -60) & (pts[:, 0] < W + 60) & (pts[:, 1] > -60) & (pts[:, 1] < H + 60)
        for i in np.nonzero(ok)[0]:
            z = zs[i]
            r = self.mote_size[i] * 1500 / z * 2.2
            cc = self.coc(cam, z)
            a = 0.30 * clamp(1 - z / 9000) * clamp(z / 400)
            col = ramp(BRAND, self.mote_hue[i], a / (1 + cc * 0.25))
            q = skia.Paint(AntiAlias=True, Color=col)
            if cc > 1.5:
                q.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, cc * 0.6))
            c.drawCircle(pts[i, 0], pts[i, 1], r + cc * 0.5, q)

    # ------------------------------------------------------------ the light between stations

    def draw_world_light(self, c, cam, t):
        B = self.B
        # S1 → S2: the line leaves the S's tail and dives to the ring's top.
        a, b = B["s1_exit"] - 0.06, B["ring_wrap"][0]
        if a - 0.05 < t < B["ring_wrap"][1] + 0.3:
            self._travel(c, cam, self._dive_curve(), t, a, b, trail=0.55)
        # S3 → S4: from the mood row up and over into the flower.
        a, b = B["leap"] - 0.02, B["bloom_gul"][0] + 0.02
        if a - 0.05 < t < b + 0.45:
            self._travel(c, cam, self._arc(self.p_cycle, self.phone_layout()["mood_c"], self.p_streak, self.streak_layout()["flower"], lift=-380), t, a, b)
        # S4 → S5: from the badge to the chat's caret.
        a, b = B["glide_ai"][0] - 0.05, B["typing"][0] - 0.02
        if a - 0.05 < t < b + 0.45:
            al = self.ai_layout()
            self._travel(c, cam, self._arc(self.p_streak, self.streak_layout()["badge"], self.p_ai, al["caret0"], lift=-260), t, a, b)
        # S5 → S6: from the end of the answer to the doctor's card.
        a, b = B["thread"][0], B["doctor_card"] + 0.02
        if a - 0.05 < t < b + 0.45:
            self._travel(c, cam, self._arc(self.p_ai, self.ai_layout()["caret1"], self.p_doc, self.doctor_layout()["card_in"], lift=-220), t, a, b)
        # S6 → S7: from the meds list to her phone.
        a, b = B["stretch"][0] - 0.05, B["stretch"][0] + 0.32
        if a - 0.05 < t < b + 0.45:
            hx, hy = self._her_phone_xy()
            self._travel(c, cam, self._arc(self.p_doc, self.doctor_layout()["meds_out"], self.p_love, (hx, hy - self.H * 0.18), lift=-200), t, a, b)
        # S8 → S9: the four branches gather into one line and run to the type's baseline.
        a, b = B["converge"][0] + 0.05, B["converge"][1] + 0.05
        if a - 0.05 < t < b + 0.45:
            sl = self.stages_layout()
            tl = self.type_layout()
            self._travel(c, cam, self._arc(self.p_stages, sl["hub_top"], self.p_type, tl["base0"], lift=-300), t, a, b)
        # S9 → S10: the baseline becomes the pen.
        a, b = B["breath"][0] - 0.05, B["breath"][1]
        if a - 0.05 < t < b + 0.3:
            tl = self.type_layout()
            hl = self.hero_layout()
            self._travel(c, cam, self._arc(self.p_type, tl["base1"], self.p_hero, hl["pen0"], lift=-160), t, a, b, trail=0.6)

    def _strands(self, c, cam, t):
        B = self.B
        lay = self.lockup_layout()
        dot = self.p_lock.world(*lay["dot"])
        fade = 1 - ph(t, B["slam"], B["slam"] + 0.32)
        for x, y, ph0 in self.strands:
            speed = 26000
            z = dot[2] + 9000 - ((t * speed + ph0 * 9000) % 9000)
            z0, z1 = z, z + 900
            P0 = dot + np.array([x, y, z0 - dot[2]])
            P1 = dot + np.array([x * 1.02, y * 1.02, z1 - dot[2]])
            (s0, s1), (d0, d1) = cam.project(np.stack([P0, P1]))
            if min(d0, d1) < 30:
                continue
            p = skia.Path()
            p.moveTo(*s0); p.lineTo(*s1)
            w = clamp(1500 / d0 * 3.0, 0.6, 7)
            col = ramp(BRAND, ph0)
            L.glow_line(c, p, w, color=col, alpha=0.75 * fade * clamp((9000 - (z0 - dot[2])) / 2500), glow=0.8)

    def _dive_curve(self):
        lay = self.lockup_layout()
        tail = self.p_lock.world(*lay["tail"])
        rl = self.ring_layout()
        top = self.p_cycle.world(rl["cx"], rl["cy"] - rl["R"])
        return _cubic(tail, tail + np.array([-260, 420, 700]), top + np.array([-520, -260, -500]), top, 96)

    def _arc(self, pa, uva, pb, uvb, lift=-300.0):
        A = pa.world(*uva)
        Bp = pb.world(*uvb)
        mid_up = np.array([0, lift, 0])
        return _cubic(A, A + (Bp - A) * 0.25 + mid_up + np.array([0, 0, -400]),
                      A + (Bp - A) * 0.75 + mid_up * 0.6 + np.array([0, 0, -300]), Bp, 96)

    def _travel(self, c, cam, pts, t, a, b, trail=0.45, width=4.2):
        """Light running along a 3D curve from t=a to t=b, its tail fading behind it."""
        k = EMPH(ph(t, a, b))
        if k <= 0:
            return
        fade_after = 1 - ph(t, b + 0.05, b + 0.40)
        if fade_after <= 0:
            return
        n = len(pts)
        head = k
        tail = max(0.0, head - trail) if t < b else head - trail * (1 - fade_after) - (1 - fade_after)
        tail = max(0.0, tail)
        i0, i1 = int(tail * (n - 1)), max(int(head * (n - 1)), 1)
        if i1 <= i0:
            return
        scr, zs = cam.project(pts[i0:i1 + 1])
        if zs.min() < 40:
            good = zs >= 40
            scr, zs = scr[good], zs[good]
            if len(scr) < 2:
                return
        # Draw in chunks so the width follows depth and the colour runs along the line.
        m = len(scr)
        chunk = 8
        for j in range(0, m - 1, chunk):
            seg = scr[j:min(m, j + chunk + 1)]
            if len(seg) < 2:
                continue
            p = skia.Path()
            p.moveTo(*seg[0])
            for q in seg[1:]:
                p.lineTo(*q)
            z = float(zs[min(m - 1, j + chunk // 2)])
            w = clamp(width * 1500 / z, 0.8, 14)
            u = (i0 + j) / (n - 1)
            fall = clamp((j / max(1, m - 1)) * 2.2)  # the tail fades in
            L.glow_line(c, p, w, color=ramp(BRAND, u), alpha=fade_after * (0.25 + 0.75 * fall))
        hx, hy = scr[-1]
        hz = float(zs[-1])
        if t < b + 0.05:
            L.pen_head(c, hx, hy, clamp(width * 1500 / hz * 1.2, 2, 18), alpha=fade_after)

    # ------------------------------------------------------------ S1 · lock-up (and S10's twin)

    def lockup_layout(self, size=None):
        W, H = self.W, self.H
        size = size or (300 if not self.fmt.portrait else 330)
        m = L.lockup_metrics(size)
        top = (H - m["total"]) / 2 - (10 if not self.fmt.portrait else 120)
        cx = W / 2
        x0 = cx - m["mw"] / 2
        (dx, dy), dw = L.dot_center(m["mh"])
        (tx, ty), _ = L.mark_point(m["mh"], 1.0)
        return dict(size=size, m=m, top=top, cx=cx, x0=x0, dot=(x0 + dx, top + dy), dot_w=dw, tail=(x0 + tx, top + ty))

    def _slam_squash(self, t):
        s = t - self.B["slam"]
        if s < 0:
            return (1.0, 1.0)
        f = s * 60
        if f < 4:
            k = f / 4
            return (lerp(1, 1.25, k), lerp(1, 0.80, k))
        if f < 8:
            k = (f - 4) / 4
            return (lerp(1.25, 0.92, k), lerp(0.80, 1.08, k))
        if f < 16:
            k = POP((f - 8) / 8)
            return (lerp(0.92, 1.0, k), lerp(1.08, 1.0, k))
        return (1.0, 1.0)

    def draw_lockup(self, c, t):
        B, lay = self.B, self.lockup_layout()
        clock = (t - B["s1_write"][0]) * 1000
        stroke, _, letters, _ = L.reveal_state(clock, compressed=True)
        # The dot: in flight before the slam (drawn by light_lockup), then here.
        dot = 1.0 if t >= B["slam"] else 0.0
        repop = 1 + 0.10 * bell(t, B["s1_write"][1] - 0.06, B["s1_write"][1] + 0.26)
        c.save()
        c.translate(lay["x0"], lay["top"])
        L.draw_mark(c, lay["m"]["mh"], stroke if t > B["s1_write"][0] else 0.0, dot,
                    dot_squash=self._slam_squash(t), dot_extra=repop)
        c.restore()
        m = lay["m"]
        wy = lay["top"] + m["mh"] + m["gap1"]
        c.save()
        c.translate(lay["cx"] - m["ww"] / 2, wy)
        L.draw_wordmark(c, m["ww"], letters)
        c.restore()
        # Nilufar blooms out of the dot, waves, and floats aside.
        bloom = B["bloom"]
        if t > bloom[0]:
            k = POP(ph(t, *bloom))
            dx0, dy0 = lay["dot"]
            side = -1 if not self.fmt.portrait else 1
            hx = lay["cx"] + side * (lay["m"]["width"] / 2 + 230) if not self.fmt.portrait else lay["cx"] + 300
            hy = lay["top"] + m["mh"] * 0.92 if not self.fmt.portrait else lay["top"] + m["total"] + 420
            mk = EMPH(ph(t, bloom[0], bloom[1] + 0.15))
            x = lerp(dx0, hx, mk)
            y = lerp(dy0 + 40, hy, mk)
            away = EMPH(ph(t, B["wave"][1] - 0.35, B["wave"][1] + 0.3))
            x += side * 260 * away
            y -= 120 * away
            size = 330 if not self.fmt.portrait else 360
            pose_t = t - B["wave"][0]
            pose = "idle"
            PET.draw(c, "nilufar", pose, max(0.0, pose_t), x, y, size * max(0.0, k),
                     rot=-8 * (1 - mk) + 4 * away * side, alpha=clamp(k * 3) * (1 - 0.6 * away))

    def light_lockup(self, c, t):
        B, lay = self.B, self.lockup_layout()
        dx, dy = lay["dot"]
        r0 = lay["dot_w"] / 2
        # Before the slam: the dot rushing in from depth (drawn as light, it has no plane yet).
        if t < B["slam"]:
            k = IN(ph(t, 0.0, B["slam"]))
            s = lerp(0.02, 1.0, k ** 2.2)
            m = lay["m"]
            c.save()
            c.translate(dx, dy)
            c.scale(s, s)
            c.translate(-dx, -dy)
            c.translate(lay["x0"], lay["top"])
            L.draw_mark(c, m["mh"], 0.0, 1.0)
            c.restore()
            L.pen_head(c, dx, dy, r0 * s * 1.2, alpha=0.8)
        # The shockwave ring.
        s = t - B["slam"]
        if 0 <= s < 0.55:
            k = OUT(s / 0.55)
            R = lerp(r0 * 1.1, r0 * 60, k)
            q = paint(color=hexc(C["b1"]), stroke=lerp(r0 * 0.5, r0 * 0.05, k), blur=r0 * 0.25,
                      blend=skia.BlendMode.kPlus)
            q.setAlphaf(0.7 * (1 - k))
            c.drawCircle(dx, dy, R, q)
            q2 = paint(color=hexc(C["b3"]), stroke=lerp(r0 * 0.25, r0 * 0.02, k), blend=skia.BlendMode.kPlus)
            q2.setAlphaf(0.45 * (1 - k))
            c.drawCircle(dx, dy, R * 0.82, q2)
            # A soft flash on impact.
            fl = 1 - ph(s, 0, 0.18)
            c.drawCircle(dx, dy, r0 * 6, paint(shader=skia.GradientShader.MakeRadial(
                skia.Point(dx, dy), r0 * 6, [skia.Color(255, 255, 255, int(150 * fl)), hexc(C["b1"], 0.0)]),
                blend=skia.BlendMode.kPlus))
        # The pen's head while the S is written.
        w0, w1 = B["s1_write"]
        if w0 < t < w1 + 0.05:
            stroke = L.DRAW(ph(t, w0, w0 + 0.8)) if hasattr(L, "DRAW") else ph(t, w0, w1)
            (x, y), _ = L.mark_point(lay["m"]["mh"], stroke)
            L.pen_head(c, lay["x0"] + x, lay["top"] + y, 6, alpha=1 - ph(t, w1 - 0.05, w1 + 0.05))
        # The tail glows before the line leaves it.
        if B["s1_exit"] - 0.3 < t < B["s1_exit"] + 0.1:
            tx, ty = lay["tail"]
            L.pen_head(c, tx, ty, 6, alpha=bell(t, B["s1_exit"] - 0.3, B["s1_exit"] + 0.1))

    # ------------------------------------------------------------ S2 · the cycle ring  + S3 · today

    def ring_layout(self):
        W, H = self.W, self.H
        return dict(cx=W / 2, cy=H / 2, R=300 if not self.fmt.portrait else 330, bead=15)

    def _bead_angle(self, d):
        return -90 + (d - 1) * 360 / 28

    def draw_cycle(self, c, t):
        B, S = self.B, self.S
        rl = self.ring_layout()
        cx, cy, R, br = rl["cx"], rl["cy"], rl["R"], rl["bead"]
        # As the phone rises the ring grows into a halo behind it and dims.
        rise = EMPH(ph(t, *B["phone_rise"]))
        halo = 1 + 0.75 * rise
        dim = 1 - 0.55 * rise
        Rr = R * halo
        wrap = EMPH(ph(t, *B["ring_wrap"]))
        # A faint dashed ring waits in the depth while the line dives toward it.
        ghost = ph(t, B["dive"][0], B["ring_wrap"][0]) * (1 - ph(t, B["ring_wrap"][0], B["ring_wrap"][1]))
        if ghost > 0:
            q = paint(color=hexc(C["textAccent"], 0.6 * ghost), stroke=2.6)
            q.setPathEffect(skia.DashPathEffect.Make([3.0, 9.0], 0))
            c.drawCircle(cx, cy, Rr, q)
        if wrap > 0:
            # The groove the beads sit in, drawn as far as the line has wrapped.
            c.drawArc(skia.Rect.MakeXYWH(cx - Rr, cy - Rr, Rr * 2, Rr * 2), -90, 360 * wrap, False,
                      paint(color=hexc(C["line"], 0.9 * dim), stroke=3))
        b0, b1 = B["beads"]
        f0, f1 = B["fill"]
        for d in range(1, 29):
            a = math.radians(self._bead_angle(d))
            x, y = cx + Rr * math.cos(a), cy + Rr * math.sin(a)
            appear = ph(t, b0 + (d - 1) / 60, b0 + (d - 1) / 60 + 0.12)
            if appear <= 0:
                continue
            s = POP(appear)
            ph_name = PHASE_OF_DAY(d)
            col = C[ph_name]
            fill_k = ph(t, f0 + (d - 1) / 23 * (f1 - f0 - 0.08), f0 + (d - 1) / 23 * (f1 - f0 - 0.08) + 0.08)
            r = br * s * (1 + 0.15 * rise)
            if d < TODAY:
                # Recorded days: an outline that fills with its phase's clay.
                c.drawCircle(x, y, r, paint(color=hexc(C["lineStrong"], 0.9 * dim), stroke=2.2))
                if fill_k > 0:
                    U.clay_bead(c, x, y, r * POP(fill_k), col, alpha=dim)
            elif d == TODAY:
                tk = ph(t, B["today"], B["today"] + 0.35)
                c.drawCircle(x, y, r, paint(color=hexc(C["lineStrong"], 0.9 * dim), stroke=2.2))
                if tk > 0:
                    pulse = 1 + 0.08 * math.sin((t - B["today"]) * 6)
                    U.clay_bead(c, x, y, r * 1.35 * POP(tk) * pulse, col, alpha=dim)
                    c.drawCircle(x, y, r * 1.9 * POP(tk), paint(color=skia.Color(255, 255, 255, int(200 * dim)), stroke=2.4))
            else:
                # Predicted days are never drawn as facts: outline only, in the phase colour.
                q = paint(color=hexc(col, 0.95 * dim), stroke=2.4)
                q.setPathEffect(skia.DashPathEffect.Make([5.0, 4.0], 0))
                c.drawCircle(x, y, r, q)
        # Centre label and the estimate chip.
        tk = EMPH(ph(t, B["today"], B["today"] + 0.4)) * (1 - ph(t, B["phone_rise"][0], B["phone_rise"][0] + 0.25))
        if tk > 0:
            T.draw(c, "ui6", S["today"].upper(), 22, cx, cy - 28 + 10 * (1 - tk), color=C["muted"], alpha=tk, track=0.14, align="center")
            T.draw(c, "display", S["cycle_day"], 64, cx, cy + 44 + 10 * (1 - tk), color=C["text"], alpha=tk, track=-0.02, align="center")
        ek = POP(ph(t, B["estimate"], B["estimate"] + 0.3)) * dim * (1 - ph(t, B["phone_rise"][0], B["phone_rise"][0] + 0.25))
        if ek > 0:
            a = math.radians(self._bead_angle(26.5))
            ex, ey = cx + (Rr + 92) * math.cos(a), cy + (Rr + 92) * math.sin(a)
            c.save()
            c.translate(ex, ey)
            c.scale(ek, ek)
            U.chip(c, 0, -30, "◷ " + S["estimate"], 2.2, outline=C["luteal"], color=C["textAccent"], size=15, align="center")
            c.restore()
        # S3: the phone rises out of the ring.
        if t > B["phone_rise"][0] - 0.05:
            self.draw_phone(c, t)

    def light_cycle(self, c, t):
        B = self.B
        rl = self.ring_layout()
        cx, cy, R = rl["cx"], rl["cy"], rl["R"]
        wa, wb = B["ring_wrap"]
        k = EMPH(ph(t, wa, wb))
        if 0 < k and t < wb + 0.5:
            fade = 1 - ph(t, wb + 0.05, wb + 0.5)
            p = skia.Path()
            p.addArc(skia.Rect.MakeXYWH(cx - R, cy - R, R * 2, R * 2), -90 + 360 * max(0, k - 0.55), 360 * min(k, 0.55))
            L.glow_line(c, p, 4.5, shader=brand_shader(cx - R, cy - R, cx + R, cy + R), alpha=fade)
            if t < wb:
                a = math.radians(-90 + 360 * k)
                L.pen_head(c, cx + R * math.cos(a), cy + R * math.sin(a), 7)
        # S3: the line's light pours into the water ring.
        self.light_phone(c, t)

    # -- the phone (S3)

    def phone_layout(self):
        W, H = self.W, self.H
        u = 1.55
        pw, ph_ = 400 * u, 860 * u
        x0 = W / 2 - pw / 2
        rest_top = 60 if not self.fmt.portrait else (H - ph_) / 2
        d = dict(u=u, pw=pw, ph=ph_, x0=x0, rest_top=rest_top)
        top = rest_top
        pad = 20 * u
        d["water"] = (x0 + pad, top + 300 * u, pw - 2 * pad, 150 * u)
        d["pill"] = (x0 + pad, top + 470 * u, pw - 2 * pad, 84 * u)
        d["mood"] = (x0 + pad, top + 574 * u, pw - 2 * pad, 120 * u)
        cxw = lambda r: (r[0] + r[2] / 2, r[1] + r[3] / 2)
        d["water_c"], d["pill_c"], d["mood_c"] = cxw(d["water"]), cxw(d["pill"]), cxw(d["mood"])
        d["frame_v"] = top - 46 + H / 2 if not self.fmt.portrait else top + ph_ / 2
        d["ring_c"] = (d["water"][0] + 70 * u, d["water"][1] + d["water"][3] / 2)
        return d

    def draw_phone(self, c, t):
        B, S = self.B, self.S
        pl = self.phone_layout()
        u, pw, phh, x0 = pl["u"], pl["pw"], pl["ph"], pl["x0"]
        rise = EMPH(ph(t, *B["phone_rise"]))
        dy = (1 - rise) * self.H * 0.9
        c.save()
        c.translate(0, dy)
        top = pl["rest_top"]
        # Device: a dark bezel, the app's bg inside, 44dp corners.
        U.shadow(c, x0, top, pw, phh, 52 * u, u, alpha=0.6, lift=24)
        c.drawRRect(U.rrect(x0 - 9 * u, top - 9 * u, pw + 18 * u, phh + 18 * u, 56 * u), paint(color=hexc("#07060F")))
        c.drawRRect(U.rrect(x0 - 9 * u, top - 9 * u, pw + 18 * u, phh + 18 * u, 56 * u),
                    paint(color=skia.Color(255, 255, 255, 30), stroke=1.5))
        c.save()
        c.clipRRect(U.rrect(x0, top, pw, phh, 46 * u), True)
        c.drawRect(skia.Rect.MakeXYWH(x0, top, pw, phh), paint(color=hexc(C["bg"])))
        c.drawCircle(x0 + pw * 0.8, top + 40 * u, 260 * u, paint(shader=skia.GradientShader.MakeRadial(
            skia.Point(x0 + pw * 0.8, top + 40 * u), 260 * u, [hexc(C["primary"], 0.22), hexc(C["primary"], 0.0)])))
        pad = 20 * u
        T.draw(c, "ui6", "9:41", 15 * u, x0 + 30 * u, top + 30 * u, color=C["text"])
        c.drawRRect(U.rrect(x0 + pw / 2 - 55 * u, top + 12 * u, 110 * u, 30 * u, 15 * u), paint(color=skia.ColorBLACK))
        g = at(t, B["greeting"], 0.45)
        T.draw(c, "ui7", S["greet"], 27 * u, x0 + pad, top + 108 * u + 14 * (1 - g), color=C["text"], alpha=g, track=-0.02)
        T.draw(c, "ui", S["greet_sub"], 14 * u, x0 + pad, top + 134 * u + 14 * (1 - g), color=C["muted"], alpha=g)
        # The AI summary card.
        ak = at(t, B["ai_card"], 0.5)
        if ak > 0:
            cy0 = top + 156 * u + 24 * (1 - ak)
            cw, chh = pw - 2 * pad, 124 * u
            U.clay_surface(c, x0 + pad, cy0, cw, chh, 24 * u, u, fill=C["surface"], alpha=ak)
            U.orb(c, x0 + pad + 30 * u, cy0 + 34 * u, 14 * u, t=t, alpha=ak)
            T.draw(c, "ui7", S["ai_title"], 12 * u, x0 + pad + 54 * u, cy0 + 39 * u, color=C["textAccent"], alpha=ak, track=0.14)
            T.draw_block(c, "ui5", S["ai_summary"], 15 * u, x0 + pad + 18 * u, cy0 + 72 * u, cw - 36 * u,
                         color=C["text"], alpha=at(t, B["ai_card"] + 0.055, 0.5), leading=1.3)
            T.draw(c, "ui", S["ai_foot"], 11 * u, x0 + pad + 18 * u, cy0 + chh - 14 * u, color=C["muted2"],
                   alpha=at(t, B["ai_card"] + 0.11, 0.5))
        # Water.
        wx, wy, ww, wh = pl["water"]
        wk = at(t, B["ai_card"] + 0.12, 0.5)
        if wk > 0:
            U.clay_surface(c, wx, wy, ww, wh, 24 * u, u, alpha=wk)
            rcx, rcy = pl["ring_c"]
            rr = 46 * u
            U.clay_ring_track(c, rcx, rcy, rr, 12 * u)
            fill = 0.75 + 0.25 * EMPH(ph(t, B["water"], B["water"] + 0.5))
            U.clay_arc(c, rcx, rcy, rr, 12 * u, -90, 360 * fill, C["accent"], bead=True, alpha=wk)
            U.icon(c, "ic3d_water", rcx, rcy, 40 * u, alpha=wk)
            T.draw(c, "ui7", S["water"], 20 * u, wx + 140 * u, wy + 52 * u, color=C["text"], alpha=wk, track=-0.01)
            ml = int(round((1500 + 500 * EMPH(ph(t, B["water"], B["water"] + 0.5))) / 10) * 10)
            ml_s = f"{ml:,}".replace(",", " " if self.lang == "uz" else ",")
            T.draw(c, "ui", S["water_of"].format(ml_s), 15 * u, wx + 140 * u, wy + 80 * u, color=C["muted"], alpha=wk)
            pk = POP(ph(t, B["water"] + 0.06, B["water"] + 0.34))
            c.save()
            bx, by = wx + 140 * u, wy + 96 * u
            c.translate(bx, by + 18 * u)
            c.scale(1 + 0.12 * bell(t, B["water"], B["water"] + 0.3), 1 + 0.12 * bell(t, B["water"], B["water"] + 0.3))
            U.chip(c, 0, -18 * u, S["add_water"], u, fill=C["surface2"], color=C["accent"], size=13, alpha=wk)
            c.restore()
        # Pill row.
        px_, py_, pw_, ph2 = pl["pill"]
        pk = at(t, B["ai_card"] + 0.18, 0.5)
        if pk > 0:
            U.clay_surface(c, px_, py_, pw_, ph2, 20 * u, u, alpha=pk)
            U.icon(c, "ic3d_meds", px_ + 40 * u, py_ + ph2 / 2, 46 * u, alpha=pk)
            T.draw(c, "ui6", S["pill"], 17 * u, px_ + 76 * u, py_ + 36 * u, color=C["text"], alpha=pk)
            T.draw(c, "ui", S["pill_sub"], 13 * u, px_ + 76 * u, py_ + 58 * u, color=C["muted"], alpha=pk)
            # The action flips over like a card: the outlined pill, then the done one.
            fk = ph(t, B["meds"] - 0.04, B["meds"] + 0.16)
            bw, bh = 128 * u, 40 * u
            bx, by = px_ + pw_ - bw - 16 * u, py_ + (ph2 - bh) / 2
            sy = abs(math.cos(math.pi * fk))
            c.save()
            c.translate(bx + bw / 2, by + bh / 2)
            c.scale(1, max(0.02, sy))
            c.translate(-(bx + bw / 2), -(by + bh / 2))
            if fk < 0.5:
                c.drawRRect(U.rrect(bx, by, bw, bh, bh / 2), paint(color=hexc(C["primary"]), stroke=1.6 * u))
                T.draw(c, "ui6", S["taken"], 14 * u, bx + bw / 2, by + bh / 2 + 5 * u, color=C["textAccent"], align="center", alpha=pk)
            else:
                U.clay_surface(c, bx, by, bw, bh, bh / 2, u, fill=C["success"], drop=False)
                U.check(c, bx + 20 * u, by + bh / 2, 11 * u, color="#2BA57A", progress=ph(t, B["meds"] + 0.1, B["meds"] + 0.3))
                T.draw(c, "ui7", S["taken"], 14 * u, bx + bw / 2 + 12 * u, by + bh / 2 + 5 * u, color="#0B2A1E", align="center")
            c.restore()
        # Mood.
        mx, my, mw, mh = pl["mood"]
        mk = at(t, B["ai_card"] + 0.24, 0.5)
        if mk > 0:
            U.clay_surface(c, mx, my, mw, mh, 24 * u, u, alpha=mk)
            T.draw(c, "ui7", S["mood"], 18 * u, mx + 20 * u, my + 34 * u, color=C["text"], alpha=mk)
            faces = ["ic3d_mood_bad", "ic3d_mood_low", "ic3d_mood_ok", "ic3d_mood_good", "ic3d_mood_great"]
            sel = POP(ph(t, B["mood"], B["mood"] + 0.32))
            for i, f in enumerate(faces):
                fx = mx + 44 * u + i * (mw - 88 * u) / 4
                fy = my + 80 * u
                chosen = i == 3
                s = 1 + (0.28 * sel if chosen else -0.12 * clamp(sel))
                a = mk * (1 if chosen or sel <= 0 else 1 - 0.45 * clamp(sel))
                if chosen and sel > 0:
                    c.drawCircle(fx, fy, 30 * u * s, paint(color=hexc(C["primary"], 0.28 * clamp(sel))))
                U.icon(c, f, fx, fy, 40 * u, alpha=a, scale=s)
        # The tab bar.
        self._tabbar(c, x0 + 14 * u, top + phh - 92 * u, pw - 28 * u, u, journey=0, alpha=at(t, B["ai_card"] + 0.3, 0.5))
        c.restore()
        c.restore()

    def light_phone(self, c, t):
        B = self.B
        pl = self.phone_layout()
        rise = EMPH(ph(t, *B["phone_rise"]))
        dy = (1 - rise) * self.H * 0.9
        u = pl["u"]
        c.save()
        c.translate(0, dy)
        # The ring's light streams up into the water ring as "suv" is said.
        a, b = B["water"] - 0.30, B["water"] + 0.45
        if a < t < b + 0.3:
            rcx, rcy = pl["ring_c"]
            rr = 46 * u
            k = EMPH(ph(t, B["water"], b))
            fade = 1 - ph(t, b, b + 0.3)
            p = skia.Path()
            p.addArc(skia.Rect.MakeXYWH(rcx - rr, rcy - rr, rr * 2, rr * 2), -90 + 270, 90 * k + 0.1)
            L.glow_line(c, p, 6, color=hexc(C["accent"]), alpha=fade, glow=1.4)
            # The stream arriving from below the phone.
            sk = EMPH(ph(t, a, B["water"] + 0.05))
            if sk < 1:
                rl = self.ring_layout()
                src = (rl["cx"], rl["cy"] + rl["R"] * 1.75 - dy)
                pts = _cubic((src[0], src[1], 0), (src[0] - 300, src[1] - 200, 0), (rcx - 200, rcy + 160, 0), (rcx, rcy + rr, 0), 40)[:, :2]
                n = len(pts)
                i1 = max(2, int(n * sk))
                pen = L.Pen(points=pts[max(0, i1 - 18):i1])
                L.glow_line(c, pen.path, 4, shader=brand_shader(src[0], src[1], rcx, rcy), alpha=1.0)
                L.pen_head(c, *pts[i1 - 1], 6)
        # The pill's tick and the mood's choice catch the light.
        for tt, (xx, yy) in ((B["meds"], pl["pill_c"]), (B["mood"], pl["mood_c"])):
            k = bell(t, tt - 0.02, tt + 0.45)
            if k > 0:
                xx2 = xx + (pl["pill"][2] / 2 - 80 * u if tt == B["meds"] else 0.12 * pl["mood"][2])
                c.drawCircle(xx2, yy, 90 * u, paint(shader=skia.GradientShader.MakeRadial(
                    skia.Point(xx2, yy), 90 * u, [hexc(C["b2"], 0.35 * k), hexc(C["b2"], 0.0)]), blend=skia.BlendMode.kPlus))
        c.restore()

    def _tabbar(self, c, x, y, w, u, journey=0, alpha=1.0, morph=None, big=False):
        """BottomNav: a floating clay pill, five slots, the journey tab showing her stage."""
        if alpha <= 0:
            return
        S = self.S
        h = 72 * u
        U.clay_surface(c, x, y, w, h, h / 2, u, fill=C["surface"], alpha=alpha)
        c.drawRRect(U.rrect(x, y, w, h, h / 2), paint(color=hexc(C["primary"], 0.35 * alpha), stroke=1.5 * u))
        slot = w / 5
        icons = ["ic3d_home", "ic3d_calm", "ic3d_chats", STAGE_ICONS[journey], "ic3d_crown"]
        for i in range(5):
            sx = x + slot * i + slot / 2
            sel = i == 3
            if sel:
                c.drawRRect(U.rrect(x + slot * i + 6 * u, y + 8 * u, slot - 12 * u, h - 16 * u, (h - 16 * u) / 2),
                            paint(color=hexc(C["primary"], 0.20 * alpha)))
            label = S["tabs"][i] if i != 3 else S["journey"][journey]
            ic = icons[i]
            if sel and morph is not None:
                # The tab morphs: the old icon and label leave upward, the new ones arrive.
                k, prev = morph
                if k < 1:
                    U.icon(c, STAGE_ICONS[prev], sx, y + 28 * u - 12 * u * k, 30 * u, alpha=alpha * (1 - k))
                    T.draw(c, "ui6", S["journey"][prev], 11 * u, sx, y + 58 * u - 8 * u * k, color=C["textAccent"],
                           alpha=alpha * (1 - k), align="center")
                ka = POP(k)
                U.icon(c, ic, sx, y + 28 * u + 12 * u * (1 - k), 30 * u * (0.6 + 0.4 * ka), alpha=alpha * k)
                T.draw(c, "ui6", label, 11 * u, sx, y + 58 * u + 8 * u * (1 - k), color=C["textAccent"], alpha=alpha * k, align="center")
            else:
                U.icon(c, ic, sx, y + 28 * u, 30 * u, alpha=alpha * (1 if sel else 0.9))
                T.draw(c, "ui6" if sel else "ui5", label, 11 * u, sx, y + 58 * u, color=C["textAccent"] if sel else C["muted2"],
                       alpha=alpha, align="center")

    # ------------------------------------------------------------ S4 · streak & Gul

    def streak_layout(self):
        W, H = self.W, self.H
        if not self.fmt.portrait:
            return dict(k=1.25, flower=(W * 0.19, H * 0.53), card=(W * 0.34, H * 0.32), counter=(W * 0.83, H * 0.22),
                        badge=(W * 0.83, H * 0.58), flower_size=360)
        return dict(k=1.25, flower=(W * 0.50, H * 0.17), card=(W * 0.5 - 380, H * 0.30), counter=(W * 0.50, H * 0.55),
                    badge=(W * 0.50, H * 0.71), flower_size=360)

    def draw_streak(self, c, t):
        B, S = self.B, self.S
        sl = self.streak_layout()
        k_ = sl["k"]
        u = 2.0 * k_
        # The Gul blooms out of the curl.
        fx, fy = sl["flower"]
        fs = sl["flower_size"]
        bk = ph(t, *B["bloom_gul"])
        if bk > 0:
            s = POP(bk)
            spin = (1 - EMPH(bk)) * -120
            c.drawCircle(fx, fy, fs * 0.8 * s, paint(shader=skia.GradientShader.MakeRadial(
                skia.Point(fx, fy), fs * 0.8 * s, [hexc(C["b3"], 0.30), hexc(C["b1"], 0.0)])))
            U.icon(c, "ic3d_flower_coin", fx, fy, fs, scale=s, rot=spin + 6 * math.sin(t * 2.2))
        # The streak card: digit cells, the label, the week row.
        cx0, cy0 = sl["card"]
        cw, chh = 610 * k_, 236 * k_
        ck = at(t, B["streak_card"], 0.4)
        if ck > 0:
            c.save()
            c.translate(0, 30 * (1 - ck))
            U.clay_surface(c, cx0, cy0, cw, chh, 28 * k_, u, alpha=ck)
            roll = EMPH(ph(t, B["streak_roll"], B["streak_roll"] + 0.28))
            dx0 = cx0 + 30 * k_
            cell_w, cell_h = 92 * k_, 116 * k_
            dy0 = cy0 + 26 * k_
            c.drawRRect(U.rrect(dx0, dy0, cell_w, cell_h, 16 * k_), paint(color=hexc(C["surface2"], ck)))
            c.save()
            c.clipRRect(U.rrect(dx0, dy0, cell_w, cell_h, 16 * k_), True)
            T.draw(c, "display", "6", 96 * k_, dx0 + cell_w / 2, dy0 + 94 * k_ - roll * cell_h, color=C["text"], alpha=ck, align="center")
            T.draw(c, "display", "7", 96 * k_, dx0 + cell_w / 2, dy0 + 94 * k_ + (1 - roll) * cell_h, color=C["warm"], alpha=ck, align="center")
            c.restore()
            label = S["streak"].format(7).split(" ", 1)[1]
            T.draw(c, "ui7", label, 40 * k_, dx0 + cell_w + 22 * k_, dy0 + 74 * k_, color=C["text"], alpha=ck, track=-0.02)
            # Week row: seven clay beads filling Du → Ya.
            wy = cy0 + chh - 54 * k_
            for i, d in enumerate(S["week"]):
                bx = cx0 + 56 * k_ + i * (cw - 112 * k_) / 6
                kk = ph(t, B["week_fill"][0] + i * TL.STAGGER, B["week_fill"][0] + i * TL.STAGGER + 0.18)
                c.drawCircle(bx, wy, 16 * k_, paint(color=hexc(C["lineStrong"], 0.8 * ck), stroke=2))
                if kk > 0:
                    U.clay_bead(c, bx, wy, 16 * k_ * POP(kk), C["warm"], alpha=ck)
                T.draw(c, "ui6", d, 16 * k_, bx, wy + 40 * k_, color=C["muted"], alpha=ck, align="center")
            c.restore()
        # The Gul counter.
        gx, gy = sl["counter"]
        gk = at(t, B["streak_card"] + 0.08, 0.4)
        if gk > 0:
            landed = sum(1 for x in B["coins"] if t >= x + 0.30)
            val = 340 + [0, 10, 13, 18][landed]
            w_, h_ = 330, 100
            U.clay_surface(c, gx - w_ / 2, gy - h_ / 2, w_, h_, h_ / 2, 2.0, alpha=gk)
            U.icon(c, "ic3d_flower_coin", gx - w_ / 2 + 54, gy, 70, alpha=gk)
            bump = 1 + 0.14 * max([bell(t, x + 0.28, x + 0.50) for x in B["coins"]] + [0])
            c.save(); c.translate(gx + 36, gy); c.scale(bump, bump); c.translate(-(gx + 36), -gy)
            T.draw(c, "display", S["gul"].format(val), 54, gx + 36, gy + 19, color=C["text"], alpha=gk, align="center", track=-0.02)
            c.restore()
        # The bronze badge flips in.
        bx, by = sl["badge"]
        fk = ph(t, B["badge"], B["badge"] + 0.5)
        if fk > 0:
            self._medal(c, bx, by, 132, fk, t)
            lk = at(t, B["badge"] + 0.2, 0.4)
            T.draw(c, "ui7", S["badge"], 36, bx, by + 196, color=C["text"], alpha=lk, align="center", track=-0.01)
            T.draw(c, "ui6", S["tier"].upper(), 22, bx, by + 234, color="#E9A47C", alpha=lk, align="center", track=0.14)

    def _medal(self, c, x, y, r, k, t):
        """BadgeMedal.kt in bronze: halo, a turning metal rim, the face and the clay art."""
        flip = math.cos(math.pi * (1 - EMPH(k)) * 1.0)
        sx = max(0.04, abs(flip))
        a = clamp(k * 3)
        light, mid, dark, glow = "#FFDCC4", "#E9A47C", "#B86A44", "#FFB48C"
        c.drawCircle(x, y, r * 1.35, paint(shader=skia.GradientShader.MakeRadial(
            skia.Point(x, y), r * 1.35, [hexc(glow, 0.45 * a), hexc(glow, 0.10 * a), hexc(glow, 0)], [0, 0.7, 1])))
        c.save()
        c.translate(x, y)
        c.scale(sx * POP(min(1, k * 1.3)), POP(min(1, k * 1.3)))
        c.translate(-x, -y)
        c.save()
        c.rotate((t * 50) % 360, x, y)
        c.drawCircle(x, y, r, paint(shader=skia.GradientShader.MakeSweep(
            x, y, [hexc(light), hexc(dark), hexc(mid), hexc(light), hexc(dark), hexc(mid), hexc(light)])))
        c.restore()
        c.drawCircle(x, y, r * 0.975, paint(shader=skia.GradientShader.MakeLinear(
            [skia.Point(x, y - r), skia.Point(x, y)], [skia.Color(255, 255, 255, 140), skia.Color(255, 255, 255, 0)]), stroke=r * 0.05))
        fr = r * 0.84
        c.drawCircle(x, y, fr, paint(color=hexc(C["surface"])))
        c.drawCircle(x, y, fr, paint(shader=skia.GradientShader.MakeRadial(
            skia.Point(x - r * 0.3, y - r * 0.35), fr * 1.6, [mix(light, "#FFFFFF", 0.1), hexc("#FBE3D3", 0.22)])))
        c.drawCircle(x, y, fr, paint(color=hexc(dark, 0.35), stroke=r * 0.035))
        if flip > 0:
            U.icon(c, "badge_streak", x, y, r * 2 * 0.6 / 0.92, alpha=1.0)
        c.restore()

    def light_streak(self, c, t):
        B = self.B
        sl = self.streak_layout()
        fx, fy = sl["flower"]
        # The light curls into the flower: a tightening spiral.
        a, b = B["leap"] + 0.10, B["bloom_gul"][0] + 0.12
        if a < t < b + 0.35:
            k = EMPH(ph(t, a, b))
            fade = 1 - ph(t, b, b + 0.35)
            n = 90
            th = np.linspace(0, 4.4 * math.pi, n)
            rr = np.linspace(230, 8, n)
            pts = np.stack([fx + rr * np.cos(th - math.pi / 2), fy + rr * np.sin(th - math.pi / 2)], 1)
            i1 = max(2, int(n * k))
            pen = L.Pen(points=pts[:i1])
            L.glow_line(c, pen.path, 5, shader=brand_shader(fx - 230, fy - 230, fx + 230, fy + 230), alpha=fade)
            L.pen_head(c, *pts[i1 - 1], 7, alpha=fade)
        # Coins arc from the flower into the counter.
        gx, gy = sl["counter"]
        for i, (x0, amt) in enumerate(zip(B["coins"], (10, 3, 5))):
            k = ph(t, x0, x0 + 0.32)
            if 0 < k < 1:
                e = GENTLE(k)
                px_ = lerp(fx, gx - 110, e)
                py_ = lerp(fy, gy, e) - 220 * 4 * e * (1 - e)
                U.icon(c, "ic3d_flower_coin", px_, py_, 64, scale=1 - 0.3 * e)
                T.draw(c, "ui7", self.S["gained"].format(amt), 30, px_, py_ - 50, color=C["warm"], alpha=1 - ph(k, 0.7, 1), align="center")
        # The badge's shine.
        if B["badge"] < t < B["badge"] + 0.9:
            bx, by = sl["badge"]
            k = bell(t, B["badge"] + 0.15, B["badge"] + 0.9)
            c.drawCircle(bx, by, 220, paint(shader=skia.GradientShader.MakeRadial(
                skia.Point(bx, by), 220, [hexc("#FFB48C", 0.35 * k), hexc("#FFB48C", 0)]), blend=skia.BlendMode.kPlus))

    # ------------------------------------------------------------ S5 · Sadora AI

    def ai_layout(self):
        W, H = self.W, self.H
        u = 2.0
        if not self.fmt.portrait:
            x0, w = W * 0.09, W * 0.60
        else:
            x0, w = W * 0.05, W * 0.90
        pad = 22 * u
        ans_w = w - 2 * pad - 60 * u
        n_lines = len(T.wrap("ui5", self.S["answer"], 17 * u, ans_w))
        bubble_h = n_lines * 17 * u * 1.4 + 30 * u
        # header 62u · gap · question 56u · gap · answer bubble · footnote two lines · pad
        h = pad + 62 * u + 22 * u + 56 * u + 26 * u + bubble_h + 20 * u + 52 * u + pad
        y0 = (H - h) / 2 if not self.fmt.portrait else H * 0.25
        ans_x = x0 + pad + 18 * u
        ans_y = y0 + pad + 62 * u + 22 * u + 56 * u + 26 * u + 34 * u
        return dict(u=u, x0=x0, y0=y0, w=w, h=h, pad=pad, ans=(ans_x, ans_y), ans_w=ans_w, bubble_h=bubble_h,
                    caret0=(ans_x, ans_y - 12), caret1=(x0 + w * 0.7, ans_y + bubble_h - 40),
                    pet=(W * 0.85, H * 0.78) if not self.fmt.portrait else (W * 0.74, y0 + h + 420))

    def _answer_state(self, t):
        B = self.B
        txt = self.S["answer"]
        k = ph(t, *B["typing"])
        return txt, int(len(txt) * k)

    def draw_ai(self, c, t):
        B, S = self.B, self.S
        al = self.ai_layout()
        u, x0, y0, w, h, pad = al["u"], al["x0"], al["y0"], al["w"], al["h"], al["pad"]
        k = at(t, B["glide_ai"][0] + 0.1, 0.5)
        U.clay_surface(c, x0, y0, w, h, 28 * u, u, alpha=k)
        U.orb(c, x0 + pad + 22 * u, y0 + pad + 22 * u, 20 * u, t=t, alpha=k)
        T.draw(c, "ui7", S["ai_title"], 22 * u, x0 + pad + 56 * u, y0 + pad + 18 * u, color=C["text"], alpha=k, track=0.02)
        T.draw(c, "ui", S["chat_sub"], 13 * u, x0 + pad + 56 * u, y0 + pad + 38 * u, color=C["muted"], alpha=k)
        c.drawLine(x0 + pad, y0 + pad + 62 * u, x0 + w - pad, y0 + pad + 62 * u, paint(color=hexc(C["line"], k), stroke=1.2))
        # Her question: a hero-gradient bubble on the right.
        qk = POP(ph(t, B["question_lands"] - 0.12, B["question_lands"] + 0.25))
        if qk > 0:
            qs = 17 * u
            qw = T.width("ui6", S["question"], qs) + 36 * u
            qx, qy = x0 + w - pad - qw, y0 + pad + 84 * u
            c.save()
            c.translate(qx + qw, qy + 28 * u)
            c.scale(qk, qk)
            c.translate(-(qx + qw), -(qy + 28 * u))
            U.clay_surface(c, qx, qy, qw, 56 * u, 22 * u, u, shader=U.hero_shader(qx, qy, qx + qw, qy + 56 * u), drop=False)
            T.draw(c, "ui6", S["question"], qs, qx + 18 * u, qy + 35 * u, color="#FFFFFF")
            c.restore()
        # The answer types on.
        txt, n = self._answer_state(t)
        if t > B["typing"][0] - 0.25:
            ax, ay = al["ans"]
            bub_k = at(t, B["typing"][0] - 0.25, 0.3)
            bh = al["bubble_h"]
            U.clay_surface(c, ax - 18 * u, ay - 34 * u, al["ans_w"] + 36 * u, bh, 22 * u, u, fill=C["surface2"], alpha=bub_k, drop=False)
            hi = txt.find(S["answer_hi"])
            spans = [(hi, hi + len(S["answer_hi"]), C["accent"], "ui7")] if hi >= 0 else None
            _, caret = T.draw_block(c, "ui5", txt, 17 * u, ax, ay, al["ans_w"], leading=1.4, color=C["text"], upto=n, spans=spans)
            self._caret = caret
            fk = at(t, B["footnote"], 0.4) * bub_k
            fy = ay - 34 * u + bh + 30 * u
            T.draw(c, "ui6", S["not_dx"], 14 * u, ax - 18 * u, fy, color=C["text"], alpha=fk)
            T.draw(c, "ui", S["ai_foot"], 13 * u, ax - 18 * u, fy + 24 * u, color=C["muted"], alpha=fk)
        # Nilufar thinks beside it.
        px_, py_ = al["pet"]
        pk = POP(ph(t, B["glide_ai"][0] + 0.2, B["glide_ai"][0] + 0.6))
        PET.draw(c, "nilufar", "think", t - B["glide_ai"][0], px_, py_, 320 * pk, alpha=clamp(pk * 2))

    def light_ai(self, c, t):
        B = self.B
        if not (B["typing"][0] - 0.05 < t < B["typing"][1] + 0.45):
            return
        al = self.ai_layout()
        u = al["u"]
        txt, n = self._answer_state(t)
        if not hasattr(self, "_caret"):
            return
        x, y = self._caret
        blink = 1.0 if t < B["typing"][1] else (0.5 + 0.5 * math.cos((t - B["typing"][1]) * 14)) * (1 - ph(t, B["typing"][1], B["typing"][1] + 0.45))
        p = skia.Path()
        p.moveTo(x + 4, y - 17 * u)
        p.lineTo(x + 4, y + 4 * u)
        L.glow_line(c, p, 4, shader=brand_shader(x, y - 40, x, y + 10), alpha=blink, glow=1.2)

    # ------------------------------------------------------------ S6 · doctor

    def doctor_layout(self):
        W, H = self.W, self.H
        if not self.fmt.portrait:
            d = dict(card=(W * 0.07, H * 0.13, 760, 330), rx=(W * 0.53, H * 0.15, 740, 470), meds=(W * 0.07, H * 0.58, 760, 330))
        else:
            d = dict(card=(W * 0.06, H * 0.10, W * 0.88, 330), rx=(W * 0.06, H * 0.34, W * 0.88, 470), meds=(W * 0.06, H * 0.64, W * 0.88, 330))
        x, y, w, h = d["card"]
        d["card_in"] = (x + w * 0.12, y + 40)
        mx, my, mw, mh = d["meds"]
        d["meds_slot"] = (mx + 28, my + 200, mw - 56, 96)
        d["meds_out"] = (mx + mw - 30, my + mh - 40)
        return d

    def draw_doctor(self, c, t):
        B, S = self.B, self.S
        dl = self.doctor_layout()
        u = 2.0
        x, y, w, h = dl["card"]
        k = POP(ph(t, B["doctor_card"] - 0.05, B["doctor_card"] + 0.35))
        if k > 0:
            c.save(); c.translate(x + w / 2, y + h / 2); c.scale(k, k); c.translate(-(x + w / 2), -(y + h / 2))
            U.clay_surface(c, x, y, w, h, 28 * u, u)
            U.avatar(c, x + 92, y + 100, 56, "NK", u)
            T.draw(c, "ui7", S["doctor"], 40, x + 176, y + 92, color=C["text"], track=-0.02)
            T.draw(c, "ui5", S["specialty"], 28, x + 176, y + 132, color=C["muted"])
            vk = POP(ph(t, B["verified"] - 0.05, B["verified"] + 0.3))
            sw = T.width("ui5", S["specialty"], 28)
            if vk > 0:
                U.check(c, x + 176 + sw + 26, y + 122, 15 * vk, color=C["accent"], progress=ph(t, B["verified"], B["verified"] + 0.25))
            T.draw(c, "ui6", "★ " + S["rating"], 28, x + w - 40, y + 92, color=C["warm"], align="right")
            # The 24-hour answer window.
            wk = at(t, B["window"], 0.4)
            ry = y + h - 82
            c.drawRRect(U.rrect(x + 36, ry - 30, w - 72, 72, 36), paint(color=hexc(C["surface2"], wk)))
            rcx, rcy = x + 84, ry + 6
            U.clay_ring_track(c, rcx, rcy, 20, 7)
            U.clay_arc(c, rcx, rcy, 20, 7, -90, 360 * (23 / 24 - 0.004 * (t - B["window"])), C["primary"], bead=False, alpha=wk)
            T.draw(c, "ui6", S["hours"], 28, x + 124, ry + 16, color=C["text"], alpha=wk)
            c.restore()
        # The prescription arrives, and rides the line into the meds list.
        rx, ry_, rw, rh = dl["rx"]
        rk = ph(t, B["rx"] - 0.05, B["rx"] + 0.35)
        ride = EMPH(ph(t, *B["ride"]))
        if rk > 0 and t < B["ride"][1] + 0.02:
            mx, my, mw, mh = dl["meds_slot"]
            sx = lerp(1.0, mw / rw, ride)
            cxp = lerp(rx + rw / 2, mx + mw / 2, ride)
            cyp = lerp(ry_ + rh / 2, my + mh / 2, ride) - 160 * 4 * ride * (1 - ride)
            c.save()
            c.translate(cxp, cyp)
            c.scale(sx * (0.85 + 0.15 * POP(rk)), lerp(1.0, mh / rh, ride) * (0.85 + 0.15 * POP(rk)))
            c.rotate(-4 * (1 - EMPH(rk)) + 3 * math.sin(math.pi * ride))
            c.translate(-rw / 2, -rh / 2)
            a = clamp(rk * 3)
            U.clay_surface(c, 0, 0, rw, rh, 28 * u, u, alpha=a)
            U.icon(c, "ic3d_meds", 70, 70, 76, alpha=a)
            T.draw(c, "ui7", S["rx_title"], 30, 124, 64, color=C["textAccent"], alpha=a, track=0.02)
            T.draw(c, "ui5", S["doctor"], 24, 124, 96, color=C["muted"], alpha=a)
            T.draw(c, "display", S["rx_drug"], 54, 40, 196, color=C["text"], alpha=a, track=-0.02)
            T.draw(c, "ui5", S["rx_dose"], 30, 40, 240, color=C["text"], alpha=a)
            T.draw(c, "ui", S["rx_note"], 22, 40, 282, color=C["muted"], alpha=a)
            press = bell(t, B["press"] - 0.05, B["press"] + 0.2)
            U.button(c, 40, rh - 118, rw - 80, 84, S["rx_add"], u, alpha=a * (1 - ph(ride, 0.0, 0.3)), press=press, size=15)
            c.restore()
        # The meds list.
        mx, my, mw, mh = dl["meds"]
        mk = at(t, B["rx"] + 0.1, 0.45)
        if mk > 0:
            U.clay_surface(c, mx, my, mw, mh, 28 * u, u, alpha=mk)
            T.draw(c, "ui7", S["meds_title"], 34, mx + 30, my + 58, color=C["text"], alpha=mk, track=-0.01)
            U.icon(c, "ic3d_vitamins", mx + 64, my + 124, 54, alpha=mk)
            T.draw(c, "ui6", "Vitamin D3", 28, mx + 110, my + 120, color=C["text"], alpha=mk)
            T.draw(c, "ui", self.S["pill_sub"], 22, mx + 110, my + 148, color=C["muted"], alpha=mk)
            sx, sy, sw, sh = dl["meds_slot"]
            if t >= B["ride"][1]:
                lk = POP(ph(t, B["rx_lands"], B["rx_lands"] + 0.3))
                c.save(); c.translate(sx + sw / 2, sy + sh / 2); c.scale(1 + 0.05 * (1 - lk), 1 + 0.05 * (1 - lk)); c.translate(-(sx + sw / 2), -(sy + sh / 2))
                c.drawRRect(U.rrect(sx, sy, sw, sh, 20), paint(color=hexc(C["surface2"], mk)))
                U.icon(c, "ic3d_meds", sx + 46, sy + sh / 2, 54, alpha=mk)
                T.draw(c, "ui6", S["rx_drug"], 28, sx + 92, sy + 42, color=C["text"], alpha=mk)
                T.draw(c, "ui", S["rx_dose"], 22, sx + 92, sy + 72, color=C["muted"], alpha=mk)
                U.check(c, sx + sw - 40, sy + sh / 2, 18, color=C["success"], progress=ph(t, B["rx_lands"], B["rx_lands"] + 0.3))
                c.restore()
            # The toast.
            tk = bell(t, B["rx_lands"] + 0.05, B["rx_lands"] + 1.4) ** 0.4
            if tk > 0:
                T.draw(c, "ui6", "✓ " + S["meds_toast"], 24, mx + mw / 2, my + mh + 48, color=C["success"], alpha=tk, align="center")

    def light_doctor(self, c, t):
        B = self.B
        dl = self.doctor_layout()
        # The line threads under the prescription as it rides.
        a, b = B["ride"]
        if a - 0.1 < t < b + 0.4:
            rx, ry_, rw, rh = dl["rx"]
            mx, my, mw, mh = dl["meds_slot"]
            p0 = (rx + rw / 2, ry_ + rh / 2)
            p1 = (mx + mw / 2, my + mh / 2)
            pts = np.array([(lerp(p0[0], p1[0], e), lerp(p0[1], p1[1], e) - 160 * 4 * e * (1 - e)) for e in np.linspace(0, 1, 48)])
            k = EMPH(ph(t, a, b))
            fade = 1 - ph(t, b, b + 0.4)
            i1 = max(2, int(48 * k))
            L.glow_line(c, L.Pen(points=pts[:i1]).path, 4.5, shader=brand_shader(*p0, *p1), alpha=fade)
        if B["rx_lands"] - 0.02 < t < B["rx_lands"] + 0.6:
            sx, sy, sw, sh = dl["meds_slot"]
            k = bell(t, B["rx_lands"] - 0.02, B["rx_lands"] + 0.6)
            c.drawRRect(U.rrect(sx - 6, sy - 6, sw + 12, sh + 12, 26), paint(color=hexc(C["b2"], 0.8 * k), stroke=4, blur=6, blend=skia.BlendMode.kPlus))

    # ------------------------------------------------------------ S7 · Yaqinim

    def partner_layout(self):
        W, H = self.W, self.H
        if not self.fmt.portrait:
            u = 1.30
            pw, phh = 400 * u, 780 * u
            x0, y0 = W * 0.66 - pw / 2, H * 0.5 - phh / 2 + 10
        else:
            u = 1.45
            pw, phh = 400 * u, 780 * u
            x0, y0 = W * 0.58 - pw / 2, H * 0.60 - phh / 2
        return dict(u=u, pw=pw, ph=phh, x0=x0, y0=y0, focus=(x0 + pw / 2, y0 + phh * 0.41),
                    chip_to=(x0 + pw * 0.30, y0 + 560 * u), reply=(x0 + pw * 0.62, y0 + 640 * u))

    def _mini_phone(self, c, x0, y0, pw, phh, u, alpha=1.0):
        U.shadow(c, x0, y0, pw, phh, 46 * u, u, alpha=0.55 * alpha, lift=20)
        c.drawRRect(U.rrect(x0 - 8 * u, y0 - 8 * u, pw + 16 * u, phh + 16 * u, 52 * u), paint(color=hexc("#07060F", alpha)))
        c.drawRRect(U.rrect(x0 - 8 * u, y0 - 8 * u, pw + 16 * u, phh + 16 * u, 52 * u), paint(color=skia.Color(255, 255, 255, int(30 * alpha)), stroke=1.5))
        c.drawRRect(U.rrect(x0, y0, pw, phh, 44 * u), paint(color=hexc(C["bg"], alpha)))
        c.drawRRect(U.rrect(x0 + pw / 2 - 50 * u, y0 + 12 * u, 100 * u, 28 * u, 14 * u), paint(color=hexc("#000000", alpha)))

    def draw_love(self, c, t):
        B, S = self.B, self.S
        # Her phone, smaller, on the left (above in 9:16): the app as she sees it.
        hx, hy = self._her_phone_xy()
        u2 = 0.9
        pw2, ph2 = 400 * u2, 820 * u2
        hk = at(t, B["stretch"][0] - 0.2, 0.5) * (1 - ph(t, B["pullback"][0] + 0.15, B["pullback"][0] + 0.45))
        if hk > 0:
            x0, y0 = hx - pw2 / 2, hy - ph2 / 2
            self._mini_phone(c, x0, y0, pw2, ph2, u2, alpha=hk)
            T.draw(c, "ui7", S["greet"], 22 * u2, x0 + 20 * u2, y0 + 96 * u2, color=C["text"], alpha=hk)
            U.clay_surface(c, x0 + 20 * u2, y0 + 130 * u2, pw2 - 40 * u2, 110 * u2, 24 * u2, u2, alpha=hk)
            U.icon(c, "ic3d_cycle", x0 + 70 * u2, y0 + 185 * u2, 60 * u2, alpha=hk)
            T.draw(c, "ui7", S["cycle_day"], 18 * u2, x0 + 116 * u2, y0 + 180 * u2, color=C["text"], alpha=hk)
            T.draw(c, "ui", S["p_phase"], 13 * u2, x0 + 116 * u2, y0 + 204 * u2, color=C["muted"], alpha=hk)
            # The tea request leaving her phone.
            ck = at(t, B["chip_fly"][0] - 0.35, 0.3)
            U.chip(c, x0 + 20 * u2, y0 + 270 * u2, S["tea"], u2 * 1.2, fill=C["surface2"], size=15, alpha=hk * ck)
            # The reply landing on her phone.
            rk = POP(ph(t, B["reply"] + 0.32, B["reply"] + 0.62))
            if rk > 0:
                c.save(); c.translate(x0 + pw2 - 40 * u2, y0 + 352 * u2); c.scale(rk, rk)
                U.chip(c, -150 * u2 * 1.2, -20 * u2, S["on_it"], u2 * 1.2, fill=C["primary"], color="#FFFFFF", size=15, alpha=hk)
                c.restore()
            self._tabbar(c, x0 + 12 * u2, y0 + ph2 - 86 * u2, pw2 - 24 * u2, u2, journey=0, alpha=hk)
        # The loved one's phone: what Yaqinim shows them.
        pl = self.partner_layout()
        u, pw, phh, x0, y0 = pl["u"], pl["pw"], pl["ph"], pl["x0"], pl["y0"]
        pk = at(t, B["stretch"][0] + 0.1, 0.5) * (1 - ph(t, B["pullback"][0], B["pullback"][0] + 0.35))
        if pk <= 0:
            return
        self._mini_phone(c, x0, y0, pw, phh, u, alpha=pk)
        c.save()
        c.clipRRect(U.rrect(x0, y0, pw, phh, 44 * u), True)
        pad = 22 * u
        T.draw(c, "ui7", S["partner_title"], 15 * u, x0 + pad, y0 + 74 * u, color=C["textAccent"], alpha=pk, track=0.14)
        vk = at(t, B["partner_view"], 0.5)
        U.avatar(c, x0 + pad + 30 * u, y0 + 124 * u, 30 * u, "M", u, alpha=pk * vk)
        T.draw(c, "ui7", S["her"], 26 * u, x0 + pad + 74 * u, y0 + 120 * u, color=C["text"], alpha=pk * vk, track=-0.02)
        T.draw(c, "ui", S["p_day"], 15 * u, x0 + pad + 74 * u, y0 + 144 * u, color=C["muted"], alpha=pk * vk)
        cy0 = y0 + 176 * u
        U.clay_surface(c, x0 + pad, cy0, pw - 2 * pad, 200 * u, 24 * u, u, alpha=pk * at(t, B["partner_view"] + 0.06, 0.5))
        rk = at(t, B["partner_view"] + 0.10, 0.5)
        T.draw(c, "display", S["p_period"], 30 * u, x0 + pad + 20 * u, cy0 + 50 * u, color=C["text"], alpha=pk * rk, track=-0.02)
        T.draw(c, "ui6", S["p_phase"], 15 * u, x0 + pad + 20 * u, cy0 + 80 * u, color=C["luteal"], alpha=pk * rk)
        ek = at(t, B["partner_view"] + 0.16, 0.5)
        T.draw(c, "ui5", S["p_energy"], 15 * u, x0 + pad + 20 * u, cy0 + 120 * u, color=C["muted"], alpha=pk * ek)
        bw = pw - 2 * pad - 40 * u
        c.drawRRect(U.rrect(x0 + pad + 20 * u, cy0 + 132 * u, bw, 12 * u, 6 * u), paint(color=hexc(C["surface2"], pk * ek)))
        c.drawRRect(U.rrect(x0 + pad + 20 * u, cy0 + 132 * u, bw * 0.28, 12 * u, 6 * u), paint(color=hexc(C["warm"], pk * ek)))
        T.draw(c, "ui6", S["p_low"], 14 * u, x0 + pad + 20 * u + bw, cy0 + 120 * u, color=C["warm"], alpha=pk * ek, align="right")
        T.draw_block(c, "ui", S["p_note"], 12 * u, x0 + pad + 20 * u, cy0 + 172 * u, pw - 2 * pad - 40 * u, color=C["muted2"], alpha=pk * ek, leading=1.3)
        # Messages: her request lands, their reply goes back with a heart.
        my0 = cy0 + 236 * u
        T.draw(c, "ui6", S["asked"], 13 * u, x0 + pad, my0, color=C["muted"], alpha=pk * at(t, B["chip_fly"][1], 0.3))
        lk = POP(ph(t, B["chip_fly"][1] - 0.04, B["chip_fly"][1] + 0.26))
        if lk > 0:
            c.save(); c.translate(x0 + pad, my0 + 40 * u); c.scale(lk, lk)
            U.chip(c, 0, -10 * u, S["tea"], u * 1.25, fill=C["surface2"], size=15, alpha=pk)
            c.restore()
        rk = POP(ph(t, B["reply"] - 0.10, B["reply"] + 0.22))
        if rk > 0:
            c.save(); c.translate(x0 + pw - pad, my0 + 120 * u); c.scale(rk, rk)
            w_ = T.width("ui6", S["on_it"], 15 * u * 1.25) + 2 * 12 * u * 1.25
            U.chip(c, -w_, -10 * u, S["on_it"], u * 1.25, fill=C["primary"], color="#FFFFFF", size=15, alpha=pk)
            c.restore()
            hk2 = POP(ph(t, B["reply"] + 0.08, B["reply"] + 0.40))
            U.icon(c, "ic3d_heart", x0 + pw - pad - 18 * u, my0 + 96 * u, 54 * u, scale=hk2 * (1 + 0.15 * bell(t, B["reply"] + 0.3, B["reply"] + 0.7)), alpha=pk)
        c.restore()

    def light_love(self, c, t):
        B = self.B
        hx, hy = self._her_phone_xy()
        pl = self.partner_layout()
        # The line stretches across the frame from her phone to theirs.
        a, b = B["stretch"][0] + 0.25, B["stretch"][1] + 0.05
        p0 = (hx + 190, hy - 120) if not self.fmt.portrait else (hx + 120, hy + 290)
        p1 = (pl["x0"] - 14, pl["y0"] + 300) if not self.fmt.portrait else (pl["x0"] + 80, pl["y0"] - 14)
        mid = ((p0[0] + p1[0]) / 2, min(p0[1], p1[1]) - 140)
        pts = _cubic((*p0, 0), (mid[0] - 120, mid[1], 0), (mid[0] + 120, mid[1], 0), (*p1, 0), 64)[:, :2]
        if t > a:
            k = EMPH(ph(t, a, b))
            out = 1 - ph(t, B["pullback"][0], B["pullback"][0] + 0.3)
            i1 = max(2, int(64 * k))
            pen = L.Pen(points=pts[:i1])
            L.glow_line(c, pen.path, 4, shader=brand_shader(*p0, *p1), alpha=0.9 * out)
            if t < b:
                L.pen_head(c, *pts[i1 - 1], 7)
            # The chip rides the line; the reply runs back along it.
            for (s0, s1, label, fwd) in ((B["chip_fly"][0], B["chip_fly"][1], self.S["tea"], True),
                                         (B["reply"] + 0.02, B["reply"] + 0.36, self.S["on_it"], False)):
                kk = ph(t, s0, s1)
                if 0 < kk < 1:
                    e = GENTLE(kk)
                    idx = int((e if fwd else 1 - e) * 63)
                    x, y = pts[idx]
                    L.pen_head(c, x, y, 9)
                    c.save(); c.translate(x, y); c.scale(1.0, 1.0)
                    U.chip(c, 0, -26, label, 1.5, fill=C["primary"] if not fwd else C["surface2"],
                           color="#FFFFFF" if not fwd else C["text"], size=15, align="center")
                    c.restore()

    # ------------------------------------------------------------ S8 · every stage

    def stages_layout(self):
        W, H = self.W, self.H
        if not self.fmt.portrait:
            ends = [(W * 0.14, H * 0.38), (W * 0.38, H * 0.30), (W * 0.62, H * 0.30), (W * 0.86, H * 0.38)]
            hub = (W * 0.5, H * 0.82)
        else:
            ends = [(W * 0.27, H * 0.24), (W * 0.73, H * 0.24), (W * 0.27, H * 0.50), (W * 0.73, H * 0.50)]
            hub = (W * 0.5, H * 0.80)
        return dict(ends=ends, hub=hub, hub_top=(hub[0], hub[1] - 60), tab=(hub[0], hub[1] + 40))

    def _branch(self, i):
        sl = self.stages_layout()
        hx, hy = sl["hub_top"]
        ex, ey = sl["ends"][i]
        return _cubic((hx, hy, 0), (hx, hy - 200, 0), (ex, ey + 240, 0), (ex, ey + 82, 0), 60)[:, :2]

    def draw_stages(self, c, t):
        B, S = self.B, self.S
        sl = self.stages_layout()
        hits = B["stage_hits"]
        k0 = at(t, B["pullback"][0] + 0.15, 0.5)
        # The hub: the floating tab bar, morphing with each stage.
        tx, ty = sl["tab"]
        cur = max([i for i, h in enumerate(hits) if t >= h - 0.06] + [0])
        prev = max(0, cur - 1)
        mk = ph(t, hits[cur] - 0.06, hits[cur] + 0.28) if t >= hits[0] - 0.06 else 1.0
        u = 2.2 if not self.fmt.portrait else 2.3
        tw_ = 420 * u
        tint = STAGES[cur]
        glow = skia.GradientShader.MakeRadial(skia.Point(tx, ty), tw_ * 0.7, [hexc(tint[0], 0.25 * k0), hexc(tint[0], 0)])
        c.drawCircle(tx, ty, tw_ * 0.7, paint(shader=glow))
        self._tabbar(c, tx - tw_ / 2, ty - 36 * u, tw_, u, journey=cur, alpha=k0, morph=(mk, prev) if cur > 0 else (1.0, 0))
        for i in range(4):
            hk = ph(t, hits[i] - 0.08, hits[i] + 0.35)
            if hk <= 0:
                continue
            ex, ey = sl["ends"][i]
            s = POP(hk)
            # Clay icon, the stage's colour glowing under it.
            c.drawCircle(ex, ey, 140 * s, paint(shader=skia.GradientShader.MakeRadial(
                skia.Point(ex, ey), 140 * s, [hexc(STAGES[i][0], 0.32), hexc(STAGES[i][0], 0)])))
            U.icon(c, STAGE_ICONS[i], ex, ey, 160, scale=s)
            # Caption in the stage colours.
            ck = at(t, hits[i] + 0.04, 0.4)
            sh = skia.GradientShader.MakeLinear([skia.Point(ex - 160, 0), skia.Point(ex + 160, 0)], [hexc(STAGES[i][0]), hexc(STAGES[i][1])])
            T.draw(c, "display", S["stages"][i], 58 if not self.fmt.portrait else 64, ex, ey - 104 + 16 * (1 - ck),
                   shader=sh, alpha=ck, align="center", track=-0.03)
            # The companion hops on beside it.
            land = hits[i] + 0.30
            dy, sx, sy = PET.hop(t, land, height=120, air=0.36)
            side = -1 if i < 2 else 1
            px_ = ex + side * 150
            pa = clamp((t - (land - 0.36)) * 6)
            # One clip per appearance: each clip is cut in its own box, so swapping mid-shot would jump in size.
            PET.draw(c, STAGE_PETS[i], "happy", t - land + 0.4, px_, ey + 70 + dy, 190, sx=sx, sy=sy, alpha=pa,
                     flip=side > 0)

    def light_stages(self, c, t):
        B = self.B
        hits = B["stage_hits"]
        conv = EMPH(ph(t, *B["converge"]))
        for i in range(4):
            pts = self._branch(i)
            a = B["split"] + i * 0.04
            k = EMPH(ph(t, a, hits[i]))
            if k <= 0:
                continue
            lit = ph(t, hits[i] - 0.05, hits[i] + 0.2)
            n = len(pts)
            i1 = max(2, int(n * k))
            i0 = int(n * conv * 0.98)
            if i1 - i0 < 2:
                continue
            pen = L.Pen(points=pts[i0:i1])
            col0, col1 = STAGES[i]
            sh = skia.GradientShader.MakeLinear([skia.Point(*pts[0]), skia.Point(*pts[-1])],
                                                [ramp(BRAND, 0.3), hexc(col0)] if lit < 1 else [hexc(col0), hexc(col1)])
            L.glow_line(c, pen.path, 4.5 + 1.5 * lit, shader=sh, alpha=0.55 + 0.45 * lit, glow=0.8 + 0.6 * lit)
            if k < 1:
                L.pen_head(c, *pts[i1 - 1], 7, color=hexc(col0))
            if 0 < lit < 1:
                ex, ey = self.stages_layout()["ends"][i]
                c.drawCircle(ex, ey, 220, paint(shader=skia.GradientShader.MakeRadial(
                    skia.Point(ex, ey), 220, [hexc(col0, 0.4 * bell(t, hits[i] - 0.05, hits[i] + 0.5)), hexc(col0, 0)]),
                    blend=skia.BlendMode.kPlus))

    # ------------------------------------------------------------ S9 · type

    def type_layout(self):
        W, H = self.W, self.H
        if not self.fmt.portrait:
            size = 170
            y1, y2 = H * 0.44, H * 0.70
        else:
            size = 146
            y1, y2 = H * 0.44, H * 0.565
        w1 = T.width("display", self.S["line1"], size, -0.035)
        w2 = T.width("display", self.S["line2"], size, -0.035)
        x1, x2 = W / 2 - w1 / 2, W / 2 - w2 / 2
        base_y = y1 + size * 0.16
        return dict(size=size, y1=y1, y2=y2, x1=x1, x2=x2, w1=w1, w2=w2, base0=(x1 - 40, base_y),
                    base1=(x1 + w1 + 40, base_y), base_y=base_y)

    def draw_type(self, c, t):
        B, S = self.B, self.S
        tl = self.type_layout()
        out = 1 - EMPH(ph(t, B["breath"][0] - 0.05, B["breath"][1]))
        for line, x, y, w, t0 in ((S["line1"], tl["x1"], tl["y1"], tl["w1"], B["type1"]), (S["line2"], tl["x2"], tl["y2"], tl["w2"], B["type2"])):
            if t < t0 - 0.1:
                continue
            sh = brand_shader(x, y - tl["size"], x + w, y)

            def letter(i, g, t0=t0):
                k = OUT(ph(t, t0 - 0.06 + i * 0.022, t0 - 0.06 + i * 0.022 + 0.38))
                return 0.0, 40 * (1 - k) - 30 * (1 - out), k * out, 1.0
            T.draw(c, "display", line, tl["size"], x, y, shader=sh, track=-0.035, letter=letter)

    def light_type(self, c, t):
        B = self.B
        tl = self.type_layout()
        x0, y = tl["base0"]
        x1, _ = tl["base1"]
        k = EMPH(ph(t, B["converge"][1] - 0.1, B["type1"] + 0.45))
        if k <= 0:
            return
        out = 1 - ph(t, B["breath"][0], B["breath"][0] + 0.2)
        xe = lerp(x0, x1, k)
        split = bell(t, B["colour_split"][0], B["colour_merge"][1]) ** 0.6
        if split > 0.02:
            for i, (c0, c1) in enumerate(STAGES):
                off = (i - 1.5) * 9 * split
                p = skia.Path(); p.moveTo(x0, y + off); p.lineTo(xe, y + off)
                L.glow_line(c, p, 3.2, shader=skia.GradientShader.MakeLinear([skia.Point(x0, 0), skia.Point(x1, 0)], [hexc(c0), hexc(c1)]),
                            alpha=out * split, glow=0.7)
        p = skia.Path(); p.moveTo(x0, y); p.lineTo(xe, y)
        L.glow_line(c, p, 4.5, shader=brand_shader(x0, y, x1, y), alpha=out * (1 - split * 0.85))
        if k < 1:
            L.pen_head(c, xe, y, 7)

    # ------------------------------------------------------------ S10–S11 · hero and end card

    def hero_layout(self):
        W, H = self.W, self.H
        size = 300 if not self.fmt.portrait else 330
        m = L.lockup_metrics(size)
        top = H * 0.07 if not self.fmt.portrait else H * 0.18
        cx = W / 2
        x0 = cx - m["mw"] / 2
        (sx, sy), _ = L.mark_point(m["mh"], 0.0)
        ground = top + m["total"] + (300 if not self.fmt.portrait else 360)
        return dict(size=size, m=m, top=top, cx=cx, x0=x0, pen0=(x0 + sx, top + sy), ground=ground)

    def draw_hero(self, c, t):
        B, S = self.B, self.S
        hl = self.hero_layout()
        t0 = B["t0"]
        clock = (t - t0) * 1000
        stroke, dot, letters, tag = L.reveal_state(clock)
        if t < t0:
            stroke, dot, letters, tag = 0.0, 0.0, [0.0] * 6, 0.0
        L.draw_lockup(c, hl["cx"], hl["top"], hl["size"], stroke, dot, letters, tag)
        # The five companions land on the hit, Nilufar in the centre, then bounce together.
        order = ["anorxon", "laylo", "nilufar", "ohu", "momiq"]
        W = self.W
        n = len(order)
        gap = (W * 0.155) if not self.fmt.portrait else W * 0.185
        settle = EMPH(ph(t, B["signature"] - 0.3, B["signature"] + 0.3))
        size0 = 250 if not self.fmt.portrait else 230
        size = lerp(size0, size0 * 0.80, settle)
        ground = hl["ground"]
        for i, pet in enumerate(order):
            land = t0 + (i - 2) * 0.012
            dy, sx, sy = PET.hop(t, land, height=260, air=0.42, squash=0.2)
            # From off-frame below, a hop up and in.
            enter = clamp((t - (land - 0.42)) / 0.42)
            if enter <= 0:
                continue
            bx = hl["cx"] + (i - 2) * gap
            b1 = bell(t, B["bounce"][0], B["bounce"][0] + 0.45)
            b2 = bell(t, B["bounce"][0] + 0.45, B["bounce"][1])
            dy -= 40 * (b1 + b2) * (1 if i % 2 == 0 else 0.8)
            pose = "happy"
            sz = size * (1.12 if pet == "nilufar" else 1.0)
            PET.draw(c, pet, pose, t - land, bx, ground + dy + (1 - enter) * 220, sz, sx=sx, sy=sy, alpha=clamp(enter * 2.5),
                     flip=i > 2)
        # The end card lines.
        sk = at(t, B["signature"], 0.5)
        if sk > 0:
            y_sig = ground + (62 if not self.fmt.portrait else 90)
            T.draw(c, "ui7", S["signature"], 40 if not self.fmt.portrait else 46, hl["cx"], y_sig + 10 * (1 - sk), color=C["text"], alpha=sk, align="center", track=0.01)
        pk = at(t, B["platforms"], 0.5)
        if pk > 0:
            y_pl = ground + (102 if not self.fmt.portrait else 142)
            T.draw(c, "ui5", S["platforms"], 24 if not self.fmt.portrait else 28, hl["cx"], y_pl + 10 * (1 - pk), color=C["muted"], alpha=pk, align="center")

    def light_hero(self, c, t):
        B = self.B
        hl = self.hero_layout()
        t0 = B["t0"]
        # The pen's head writes the S again, exactly on Brand.kt's clock.
        if t0 <= t < B["mark_draw"][1] + 0.05:
            stroke = L.reveal_state((t - t0) * 1000)[0]
            (x, y), _ = L.mark_point(hl["m"]["mh"], stroke)
            L.pen_head(c, hl["x0"] + x, hl["top"] + y, 6, alpha=1 - ph(t, B["mark_draw"][1] - 0.05, B["mark_draw"][1] + 0.05))
        # The landing: a soft light ring on the ground.
        if t0 - 0.02 < t < t0 + 0.6:
            k = OUT(ph(t, t0, t0 + 0.6))
            gx, gy = hl["cx"], hl["ground"]
            c.save(); c.translate(gx, gy); c.scale(1, 0.18)
            q = paint(color=hexc(C["b2"]), stroke=10 * (1 - k) + 1, blur=8, blend=skia.BlendMode.kPlus)
            q.setAlphaf(0.6 * (1 - k))
            c.drawCircle(0, 0, lerp(200, self.W * 0.55, k), q)
            c.restore()
        # The dot's pop catches the light.
        d0, d1 = B["dot_pop"]
        if d0 < t < d1 + 0.3:
            (dx, dy), dw = L.dot_center(hl["m"]["mh"])
            x, y = hl["x0"] + dx, hl["top"] + dy
            k = bell(t, d0 + 0.1, d1 + 0.3)
            c.drawCircle(x, y, dw * 4, paint(shader=skia.GradientShader.MakeRadial(
                skia.Point(x, y), dw * 4, [hexc(C["b1"], 0.6 * k), hexc(C["b1"], 0)]), blend=skia.BlendMode.kPlus))
