#!/usr/bin/env python3
"""
Seeds the demo accounts a client walkthrough needs, through the public API.

Run against a dev server started with OTP_FIXED_CODE=123456 (the code the script
types). Every call goes through the same endpoints the app uses, so what the client
sees on the phone is exactly what this produced — no rows are written directly.

    python3 tools/seed_demo.py                # http://localhost:8080
    python3 tools/seed_demo.py http://host:8080

Safe to run twice: signing in an existing phone returns the same account, and the
health writes are per-day upserts.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/") + "/v1"
OTP_CODE = "123456"
ADMIN = ("owner@sadora.uz", "changeme123")
TODAY = date.today()


def d(days_ago: int) -> str:
    return (TODAY - timedelta(days=days_ago)).isoformat()


def call(method, path, body=None, token=None, ok=(200, 201, 204)):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as res:
                raw = res.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            text = e.read().decode()
            if e.code == 429 and attempt < 3:
                time.sleep(5)
                continue
            if e.code in ok:
                return None
            raise SystemExit(f"{method} {path} -> {e.code}: {text}")
    return None


# ---------------------------------------------------------------- sign in

def sign_in(phone: str) -> tuple[str, str] | None:
    """Signs one demo account in, or None when the server refuses it.

    The refusal that actually happens is the blocked account this script creates on
    purpose: on a second run it cannot sign in again, and the seeder used to die there
    — taking the Bilim library with it. Its row is already in place from the first run,
    which is all the demo needs, so it is skipped rather than fatal.
    """
    challenge = call("POST", "/auth/otp/request", {"phone": phone, "language": "uz"})
    session = call("POST", "/auth/otp/verify", ok=(200, 403), body={
        "challengeId": challenge["challengeId"],
        "code": challenge.get("devCode") or OTP_CODE,
        "device": {
            "deviceId": f"demo-{phone[-4:]}",
            "platform": "android",
            "osVersion": "15",
            "appVersion": "1.0",
            "model": "Galaxy S23 Ultra",
            "timezone": "Asia/Tashkent",
        },
    })
    # `call` answers None for a tolerated non-2xx, which here means "blocked".
    if session is None:
        return None
    return session["tokens"]["accessToken"], session["user"]["id"]


# ---------------------------------------------------------------- the four women

USERS = [
    {
        "phone": "+998900000001",
        "name": "Malika Karimova",
        "lifeStage": "cycle",
        "birthDate": "2000-03-14",
        "heightCm": 165, "weightKg": 56,
        "goals": ["understand_cycle", "sleep_better", "drink_water"],
        "cycle": {"lastPeriodStart": d(9), "averageCycleLength": 28, "averagePeriodLength": 5, "cycleIsRegular": True},
        "periods": [(9, 5), (37, 5), (65, 4), (93, 5)],
        "logs": {
            0: {"mood": "good", "energy": 4, "stress": 2, "symptoms": []},
            1: {"mood": "ok", "energy": 3, "stress": 3, "symptoms": [{"key": "fatigue", "severity": "mild"}]},
            2: {"mood": "good", "energy": 4, "stress": 2},
            3: {"mood": "great", "energy": 5, "stress": 1},
            4: {"mood": "ok", "energy": 3, "stress": 3, "symptoms": [{"key": "headache", "severity": "moderate"}]},
            5: {"flow": "light", "mood": "ok", "energy": 3, "stress": 2},
            6: {"flow": "medium", "mood": "low", "energy": 2, "stress": 3, "symptoms": [{"key": "cramps", "severity": "moderate"}]},
            7: {"flow": "heavy", "mood": "low", "energy": 2, "stress": 4, "symptoms": [{"key": "cramps", "severity": "severe"}, {"key": "back_pain", "severity": "moderate"}]},
            8: {"flow": "heavy", "mood": "bad", "energy": 1, "stress": 4, "symptoms": [{"key": "cramps", "severity": "severe"}, {"key": "bloating", "severity": "moderate"}]},
            9: {"flow": "medium", "mood": "low", "energy": 2, "stress": 3, "symptoms": [{"key": "cramps", "severity": "moderate"}]},
            10: {"mood": "ok", "energy": 3, "stress": 3, "symptoms": [{"key": "mood_swings", "severity": "mild"}, {"key": "cravings", "severity": "moderate"}]},
            11: {"mood": "low", "energy": 2, "stress": 4, "symptoms": [{"key": "breast_tender", "severity": "mild"}, {"key": "acne", "severity": "mild"}]},
            12: {"mood": "ok", "energy": 3, "stress": 3},
            14: {"mood": "good", "energy": 4, "stress": 2},
            16: {"mood": "great", "energy": 5, "stress": 1},
            18: {"mood": "good", "energy": 4, "stress": 2},
            20: {"mood": "ok", "energy": 3, "stress": 2},
        },
        "journal": [
            (0, "Bugun ertalab yugurdim, kayfiyat juda yaxshi. Kechqurun choy o'rniga suv ichishga harakat qildim."),
            (2, "Ishda biroz charchadim, lekin nafas mashqi yordam berdi. Ertaga erta yotaman."),
            (7, "Hayz ikkinchi kuni — og'riq kuchli. Issiq grelka va SADORA maslahati bilan yengilroq o'tdi."),
        ],
        "practices": [("breathing", 240), ("meditation", 600)],
        "meals": [
            ("breakfast", "08:10", "Suli bo'tqasi, banan, yong'oq", 390, 12, 11, 62),
            ("lunch", "13:20", "Tovuqli salat, non", 520, 34, 16, 55),
            ("snack", "16:30", "Yog'urt (tabiiy), olma", 160, 8, 3, 26),
        ],
        "water": [250, 500, 250, 250],
        "meds": [
            {"name": "Folat kislotasi", "emoji": "💊", "dosage": "400", "unit": "mkg", "foodRelation": "with",
             "schedule": {"kind": "daily", "times": ["09:00"]}, "stockUnits": 24},
            {"name": "Magniy B6", "emoji": "🌙", "dosage": "1", "unit": "tabletka", "foodRelation": "after",
             "schedule": {"kind": "daily", "times": ["21:00"]}, "stockUnits": 40},
        ],
        "doses_taken": [("Folat kislotasi", "09:00")],
        "posts": [
            ("cycle", "Hayz oldidan 3-4 kun uyqum buziladi. Sizda ham shundaymi? Nima yordam beradi?"),
            ("wellbeing", "Ertalabki 10 daqiqalik nafas mashqi kunimni butunlay o'zgartirdi. Tavsiya qilaman 🌸"),
        ],
        "ai": "Hayz vaqtida og'riqni kamaytirish uchun nima qilsam bo'ladi?",
        "premium_days": 365,
        # What the demo account has in the wallet, so the shop is worth opening.
        "coins": 3200,
    },
    {
        "phone": "+998900000002",
        "name": "Dilnoza Rashidova",
        "lifeStage": "pregnancy",
        "birthDate": "1996-07-22",
        "heightCm": 168, "weightKg": 66,
        "goals": ["eat_balanced", "sleep_better", "remember_meds"],
        "stage": {"dueDate": (TODAY + timedelta(weeks=14)).isoformat(), "lastPeriodStart": (TODAY - timedelta(weeks=26)).isoformat()},
        "logs": {
            0: {"mood": "good", "energy": 3, "stress": 2, "symptoms": [{"key": "back_pain", "severity": "mild"}]},
            1: {"mood": "ok", "energy": 3, "stress": 2, "symptoms": [{"key": "swelling", "severity": "mild"}]},
            2: {"mood": "good", "energy": 4, "stress": 1},
            3: {"mood": "ok", "energy": 2, "stress": 3, "symptoms": [{"key": "insomnia", "severity": "moderate"}, {"key": "back_pain", "severity": "moderate"}]},
            4: {"mood": "great", "energy": 4, "stress": 1},
            5: {"mood": "good", "energy": 3, "stress": 2, "symptoms": [{"key": "nausea", "severity": "mild"}]},
            7: {"mood": "ok", "energy": 3, "stress": 2},
            9: {"mood": "good", "energy": 4, "stress": 2},
            11: {"mood": "low", "energy": 2, "stress": 3, "symptoms": [{"key": "fatigue", "severity": "moderate"}]},
            13: {"mood": "good", "energy": 3, "stress": 2},
        },
        "journal": [
            (0, "26-hafta! Chaqaloq bugun juda faol tepdi. Shifokor hammasi yaxshi dedi."),
            (3, "Kechasi bel og'rig'idan uyg'ondim. Yon tomonda yostiq bilan yotish yordam berdi."),
        ],
        "practices": [("breathing", 300)],
        "meals": [
            ("breakfast", "08:30", "Tvorog, mevalar, asal", 340, 22, 9, 42),
            ("lunch", "13:00", "Shorva, non, sabzavot salati", 480, 26, 14, 58),
            ("snack", "16:00", "Yong'oq va quritilgan o'rik", 210, 5, 12, 22),
            ("dinner", "19:30", "Bug'da pishirilgan baliq, guruch", 520, 38, 15, 54),
        ],
        "water": [300, 300, 300, 250, 250],
        "meds": [
            {"name": "Prenatal vitamin", "emoji": "🤰", "dosage": "1", "unit": "kapsula", "foodRelation": "with",
             "schedule": {"kind": "daily", "times": ["09:00"]}, "stockUnits": 18},
            {"name": "Temir", "emoji": "🩸", "dosage": "60", "unit": "mg", "foodRelation": "before",
             "schedule": {"kind": "daily", "times": ["12:00"]}, "stockUnits": 30},
        ],
        "doses_taken": [("Prenatal vitamin", "09:00"), ("Temir", "12:00")],
        "posts": [
            ("pregnancy", "26-haftada bel og'rig'i uchun qanday mashqlar qilyapsizlar? Yoga yordam beradimi?"),
        ],
        "premium_days": 0,
        # What the demo account has in the wallet, so the shop is worth opening.
        "coins": 640,
    },
    {
        "phone": "+998900000003",
        "name": "Nilufar Tosheva",
        "lifeStage": "postpartum",
        "birthDate": "1998-11-05",
        "heightCm": 162, "weightKg": 63,
        "goals": ["sleep_better", "less_stress", "more_energy"],
        "stage": {"birthDate": (TODAY - timedelta(weeks=6)).isoformat()},
        "logs": {
            0: {"mood": "ok", "energy": 2, "stress": 3, "symptoms": [{"key": "fatigue", "severity": "moderate"}]},
            1: {"mood": "low", "energy": 2, "stress": 4, "symptoms": [{"key": "insomnia", "severity": "severe"}, {"key": "fatigue", "severity": "severe"}]},
            2: {"mood": "ok", "energy": 3, "stress": 3},
            3: {"mood": "good", "energy": 3, "stress": 2},
            4: {"mood": "low", "energy": 2, "stress": 4, "symptoms": [{"key": "anxiety", "severity": "moderate"}]},
            5: {"mood": "ok", "energy": 2, "stress": 3, "symptoms": [{"key": "fatigue", "severity": "moderate"}]},
            6: {"mood": "good", "energy": 3, "stress": 2},
            8: {"mood": "ok", "energy": 2, "stress": 3},
            10: {"mood": "low", "energy": 1, "stress": 4, "symptoms": [{"key": "insomnia", "severity": "moderate"}]},
            12: {"mood": "ok", "energy": 3, "stress": 3},
        },
        "journal": [
            (0, "Chaqaloq kechasi 3 marta uyg'ondi. Charchadim, lekin bugun onam yordamga keldi."),
            (4, "O'zimni biroz xavotirli his qildim. Grounding mashqi tinchlantirdi."),
        ],
        "practices": [("grounding", 180), ("breathing", 120)],
        "meals": [
            ("breakfast", "07:45", "Tuxum, non, pishloq", 420, 24, 20, 36),
            ("lunch", "13:30", "Mastava, non", 380, 18, 12, 48),
        ],
        "water": [250, 250, 500],
        "meds": [
            {"name": "Vitamin D3", "emoji": "☀️", "dosage": "2000", "unit": "XB", "foodRelation": "with",
             "schedule": {"kind": "daily", "times": ["10:00"]}, "stockUnits": 55},
        ],
        "doses_taken": [],
        "posts": [
            ("wellbeing", "Tug'ruqdan keyin 6 hafta o'tdi, uyqusizlik hali ham qiynayapti. Qanday tiklandingiz?"),
        ],
        "premium_days": 0,
        # What the demo account has in the wallet, so the shop is worth opening.
        "coins": 1450,
    },
    {
        "phone": "+998900000004",
        "name": "Gulnora Azimova",
        "lifeStage": "perimenopause",
        "birthDate": "1979-01-30",
        "heightCm": 160, "weightKg": 70,
        "goals": ["sleep_better", "be_active", "eat_balanced"],
        "cycle": {"lastPeriodStart": d(40), "averageCycleLength": 35, "averagePeriodLength": 4, "cycleIsRegular": False},
        "periods": [(40, 3), (86, 4), (118, 3)],
        "logs": {
            0: {"mood": "ok", "energy": 3, "stress": 2, "symptoms": [{"key": "hot_flush", "severity": "mild"}]},
            1: {"mood": "low", "energy": 2, "stress": 3, "symptoms": [{"key": "night_sweats", "severity": "moderate"}, {"key": "insomnia", "severity": "moderate"}]},
            2: {"mood": "ok", "energy": 3, "stress": 3, "symptoms": [{"key": "hot_flush", "severity": "moderate"}]},
            3: {"mood": "good", "energy": 4, "stress": 2},
            4: {"mood": "ok", "energy": 3, "stress": 2, "symptoms": [{"key": "joint_pain", "severity": "mild"}]},
            6: {"mood": "low", "energy": 2, "stress": 4, "symptoms": [{"key": "night_sweats", "severity": "severe"}, {"key": "mood_swings", "severity": "moderate"}]},
            8: {"mood": "ok", "energy": 3, "stress": 3},
            10: {"mood": "good", "energy": 4, "stress": 2},
            13: {"mood": "ok", "energy": 3, "stress": 3, "symptoms": [{"key": "hot_flush", "severity": "moderate"}]},
        },
        "journal": [
            (1, "Kechasi yana terlab uyg'ondim. Xonani salqinroq qilib, paxta choyshab oldim."),
            (6, "Kayfiyat tez o'zgarmoqda. Ertalabki yurish va suv ko'proq ichish yordam beryapti."),
        ],
        "practices": [("meditation", 900)],
        "meals": [
            ("breakfast", "08:00", "Grechka, sabzavot, yashil choy", 310, 11, 6, 52),
            ("lunch", "12:45", "Lag'mon (kichik porsiya)", 400, 18, 12, 52),
            ("dinner", "19:00", "Tovuq go'shti, bug'da sabzavot", 430, 36, 14, 30),
        ],
        "water": [250, 250, 250, 250, 250, 250],
        "meds": [
            {"name": "Kalsiy + D3", "emoji": "🦴", "dosage": "500", "unit": "mg", "foodRelation": "with",
             "schedule": {"kind": "daily", "times": ["08:30", "20:30"]}, "stockUnits": 60},
            {"name": "Omega-3", "emoji": "🐟", "dosage": "1000", "unit": "mg", "foodRelation": "with",
             "schedule": {"kind": "daily", "times": ["13:00"]}, "stockUnits": 12},
        ],
        "doses_taken": [("Kalsiy + D3", "08:30"), ("Omega-3", "13:00")],
        "posts": [
            ("body", "47 yoshdaman, issiq xurujlar boshlandi. Dori-darmonsiz yengillashtirish yo'llari bormi?"),
        ],
        "premium_days": 30,
        # What the demo account has in the wallet, so the shop is worth opening.
        "coins": 820,
    },
]


def seed_user(spec: dict, everyone: list) -> dict:
    token, user_id = sign_in(spec["phone"])
    print(f"  {spec['name']:<20} {spec['phone']}  {user_id}")

    call("POST", "/me/onboarding", {
        "name": spec["name"],
        "language": "uz",
        "timezone": "Asia/Tashkent",
        "lifeStage": spec["lifeStage"],
        "birthDate": spec["birthDate"],
        "heightCm": spec["heightCm"],
        "weightKg": spec["weightKg"],
        "goals": spec["goals"],
        "cycle": spec.get("cycle"),
        "stage": spec.get("stage"),
        "permissions": {"notifications": True, "healthData": True, "camera": True},
        "consents": {"storeHealth": True, "aiInsights": True, "analytics": True, "marketing": False},
        "referredByDoctor": spec["lifeStage"] in ("pregnancy", "perimenopause"),
        "firstCheckIn": {"mood": spec["logs"][0].get("mood"), "symptomKeys": [s["key"] for s in spec["logs"][0].get("symptoms", [])]},
    }, token)

    for ago, length in spec.get("periods", []):
        call("POST", "/cycle/periods", {"startedOn": d(ago), "endedOn": d(ago - length + 1)}, token, ok=(200, 201, 409))

    for ago, log in spec["logs"].items():
        call("PUT", f"/days/{d(ago)}", {"symptoms": [], **log}, token)

    for ago, body in spec["journal"]:
        call("POST", "/mind/journal", {"date": d(ago), "body": body}, token)
    for kind, seconds in spec["practices"]:
        call("POST", "/mind/practices", {"kind": kind, "durationSeconds": seconds}, token)

    for slot, at, desc, kcal, p, f, c in spec["meals"]:
        call("POST", "/nutrition/meals", {
            "date": d(0), "slot": slot, "eatenAt": at, "description": desc,
            "kcal": kcal, "proteinG": p, "fatG": f, "carbsG": c,
        }, token)
    for ml in spec["water"]:
        call("POST", "/nutrition/water", {"ml": ml}, token)

    existing = {m["name"] for m in (call("GET", "/meds", token=token) or [])}
    meds = {}
    for med in spec["meds"]:
        if med["name"] in existing:
            continue
        created = call("POST", "/meds", {**med, "remindersEnabled": True, "startedOn": d(20)}, token)
        meds[med["name"]] = created["id"]
    for name, at in spec["doses_taken"]:
        if name in meds:
            call("POST", f"/meds/{meds[name]}/doses", {"dueOn": d(0), "dueAt": at, "status": "taken"}, token)

    posts = []
    for topic, body in spec["posts"]:
        posts.append(call("POST", "/community/posts", {"topic": topic, "body": body}, token)["id"])

    if spec.get("ai"):
        try:
            call("POST", "/ai/chat", {"question": spec["ai"]}, token)
        except SystemExit as e:
            print("    (AI chat skipped:", str(e)[:80], ")")

    entry = {
        "token": token,
        "id": user_id,
        "posts": posts,
        "name": spec["name"],
        "premium_days": spec["premium_days"],
        "coins": spec["coins"],
    }
    everyone.append(entry)
    return entry


COMMENTS = [
    "Menda ham xuddi shunday. Kechqurun telefonni erta qo'yish yordam berdi.",
    "Iliq sut va magniy — menga juda foyda qildi 💜",
    "Shifokorim yengil cho'zilish mashqlarini tavsiya qilgan edi, haqiqatan yordam beradi.",
    "Siz yolg'iz emassiz. Birinchi oylar eng qiyini, keyin yengillashadi 🌸",
]


def cross_link(everyone: list):
    """Likes, comments and one report, so the feed and the moderation queue are alive."""
    for i, me in enumerate(everyone):
        for j, other in enumerate(everyone):
            if i == j:
                continue
            for post_id in other["posts"]:
                call("PUT", f"/community/posts/{post_id}/like", token=me["token"])
        others = [p for j, o in enumerate(everyone) if j != i for p in o["posts"]]
        if others:
            call("POST", f"/community/posts/{others[i % len(others)]}/comments", {"body": COMMENTS[i % len(COMMENTS)]}, me["token"])
    # One open report for the moderation page — from the last woman on the first post.
    first = everyone[0]["posts"][0]
    call("POST", f"/community/posts/{first}/report", {"reason": "other", "note": "Demo: moderatsiya navbatini ko'rsatish uchun"}, everyone[-1]["token"], ok=(200, 201, 409))


def grant_premium(everyone: list):
    session = call("POST", "/admin/auth/login", {"email": ADMIN[0], "password": ADMIN[1]})
    admin = session["accessToken"]
    for u in everyone:
        if not u["premium_days"]:
            continue
        until = (TODAY + timedelta(days=u["premium_days"])).isoformat() + "T00:00:00Z"
        call("POST", f"/admin/users/{u['id']}/premium", {"expiresAt": until, "reason": "Demo hisob — mijozga ko'rsatish"}, admin)
        print(f"  Premium: {u['name']} until {until[:10]}")


def seed_rewards(everyone: list, admin: str):
    """Streaks, Nur balances and one accepted invite, so the shop is worth opening.

    The streak is whatever one check-in makes it — a day — because the server counts
    consecutive opens and there is no honest way for a seeder to fake a fortnight of
    them. The balances are a manual adjustment, which is exactly how an operator would
    grant them, and they land on the audit log saying so.
    """
    for u in everyone:
        result = call("POST", "/rewards/check-in", None, u["token"])
        call("POST", f"/admin/rewards/users/{u['id']}/adjust",
             {"amount": u["coins"], "note": "Demo hisob — do'konni ko'rsatish uchun"}, admin)
        print(f"  {u['name']:<20} streak {result['streak']['current']}  +{u['coins']} nur")

    # One invite that actually paid, so the referral screen has a number on it.
    inviter, invited = everyone[0], everyone[-1]
    code = call("GET", "/rewards/referral", token=inviter["token"])["code"]
    claim = call("POST", "/rewards/referral/claim", {"code": code}, invited["token"])
    print(f"  Taklif: {inviter['name']} -> {invited['name']} ({code}), qabul {claim['accepted']}")


# ---------------------------------------------------------------- the rest of the room

# Accounts nobody walks through, there so the admin panel has a population rather than a
# handful of rows: the list paginates, the dashboard's life-stage split has shape, and a
# blocked account exists to be looked at.
BACKGROUND = [
    ("+998900000010", "Zilola Umarova", "cycle", "1997-04-12", False),
    ("+998900000011", "Sevara Yo'ldosheva", "trying_to_conceive", "1994-09-03", True),
    ("+998900000012", "Kamola Ergasheva", "pregnancy", "1999-12-19", False),
    ("+998900000013", "Sabina Qodirova", "cycle", "2002-06-25", False),
    ("+998900000014", "Ozoda Nazarova", "postpartum", "1995-02-08", False),
    ("+998900000015", "Feruza Sharipova", "menopause", "1974-11-30", True),
    ("+998900000016", "Aziza Rahimova", "cycle", "2001-08-17", False),
    ("+998900000017", "Lola Mahmudova", "perimenopause", "1980-03-05", False),
    ("+998900000018", "Nodira Sultonova", "cycle", "1998-07-21", False),
    ("+998900000019", "Muslima Halilova", "trying_to_conceive", "1996-01-14", False),
]

BACKGROUND_MOODS = ["good", "ok", "great", "low", "ok", "good"]

# The one account the demo shows as blocked. Fixed, so re-running the seeder does not
# block a second person.
BLOCKED_PHONE = "+998900000019"


def seed_background(admin: str) -> list:
    """Quiet accounts: onboarded, a few days logged, nothing else."""
    seeded = []
    for index, (phone, name, stage, birth, premium) in enumerate(BACKGROUND):
        credentials = sign_in(phone)
        if credentials is None:
            print(f"  {name:<20} {phone}  (bloklangan — o'tkazib yuborildi)")
            continue
        token, user_id = credentials
        call("POST", "/me/onboarding", {
            "name": name,
            "language": "uz" if index % 4 else "ru",
            "timezone": "Asia/Tashkent",
            "lifeStage": stage,
            "birthDate": birth,
            "heightCm": 158 + (index % 12),
            "weightKg": 52 + (index % 18),
            "goals": ["understand_cycle", "sleep_better"][: 1 + index % 2],
            "cycle": {"lastPeriodStart": d(3 + index * 2), "averageCycleLength": 26 + index % 6,
                      "averagePeriodLength": 4 + index % 2, "cycleIsRegular": index % 3 != 0}
                     if stage in ("cycle", "trying_to_conceive") else None,
            "stage": {"dueDate": (TODAY + timedelta(weeks=8 + index)).isoformat()} if stage == "pregnancy"
                     else {"birthDate": (TODAY - timedelta(weeks=10 + index)).isoformat()} if stage == "postpartum"
                     else None,
            "permissions": {"notifications": True, "healthData": True, "camera": False},
            "consents": {"storeHealth": True, "aiInsights": index % 2 == 0, "analytics": True, "marketing": False},
            "referredByDoctor": index % 3 == 0,
        }, token)

        for day in range(0, 6):
            call("PUT", f"/days/{d(day)}", {
                "mood": BACKGROUND_MOODS[(index + day) % len(BACKGROUND_MOODS)],
                "energy": 2 + (index + day) % 4,
                "stress": 1 + (index + day) % 4,
                "symptoms": [],
            }, token)

        if premium:
            until = (TODAY + timedelta(days=90)).isoformat() + "T00:00:00Z"
            call("POST", f"/admin/users/{user_id}/premium",
                 {"expiresAt": until, "reason": "Dev muhiti uchun namuna obuna"}, admin)

        seeded.append({"id": user_id, "name": name, "phone": phone})
        print(f"  {name:<20} {phone}")

    # One blocked account: the users list filter and the card's unblock action need
    # something to act on, and a demo should not have to create it by hand.
    #
    # Named rather than "the last one seeded": a blocked account cannot sign in, so on
    # the next run it is skipped and "the last one" would be somebody else — each run
    # would block one more person until the demo was all blocked accounts.
    victim = next((u for u in seeded if u["phone"] == BLOCKED_PHONE), None)
    if victim is None:
        return seeded
    call("POST", f"/admin/users/{victim['id']}/block",
         {"blocked": True, "reason": "Dev muhiti: bloklangan hisob namunasi"}, admin)
    print(f"  blocked: {victim['name']}")
    return seeded


# ---------------------------------------------------------------- the library

def block(kind: str, **kwargs) -> dict:
    return {"type": kind, **kwargs}


ARTICLES = [
    {
        "slug": "hayz-oldidan-kayfiyat", "categoryKey": "cycle", "premium": False, "published": True,
        "title": "Hayz oldidan kayfiyat nega o'zgaradi",
        "excerpt": "PMS — injiqlik emas, gormonal o'zgarishning tabiiy natijasi. Nima yordam beradi.",
        "blocks": [
            block("paragraph", text="Hayzdan oldingi 5–7 kunda progesteron va estrogen darajasi keskin pasayadi. Bu serotoninga ta'sir qiladi — shuning uchun kayfiyat, uyqu va ishtaha bir vaqtda o'zgaradi."),
            block("heading", text="Nima yordam beradi"),
            block("bullets", items=["Kuniga 20–30 daqiqa yengil harakat", "Uyquni bir xil vaqtda boshlash", "Kofein va shakarni kamaytirish", "Magniy va B6 — shifokor tavsiyasi bilan"]),
            block("note", text="Agar kayfiyat o'zgarishi ishga yoki munosabatlarga xalaqit bersa, bu PMDD bo'lishi mumkin — ginekologga murojaat qiling."),
        ],
        "author": "Dilbar Aliyeva", "authorRole": "Akusher-ginekolog", "readMinutes": 4,
    },
    {
        "slug": "temir-tanqisligi", "categoryKey": "nutrition", "premium": False, "published": True,
        "title": "Temir tanqisligi: belgilari va ovqatlanish",
        "excerpt": "Charchoq, soch to'kilishi va nafas qisishi ko'pincha bitta sababdan.",
        "blocks": [
            block("paragraph", text="Ayollarda temir tanqisligi eng keng tarqalgan mikroelement yetishmovchiligi. Har oylik qon yo'qotish uni doimiy xavf ostida saqlaydi."),
            block("heading", text="Belgilari"),
            block("bullets", items=["Doimiy charchoq va holsizlik", "Terining oqarishi", "Soch to'kilishi va tirnoqlarning sinishi", "Zinapoyada nafas qisishi"]),
            block("heading", text="Nima yeyish kerak"),
            block("bullets", items=["Jigar va qizil go'sht", "Loviya, no'xat, yasmiq", "Ismaloq va boshqa ko'katlar", "C vitamini bilan birga — temir yaxshiroq so'riladi"]),
            block("note", text="Tashxis faqat qon tahlili bilan qo'yiladi. Ferritin ko'rsatkichini so'rang."),
        ],
        "author": "Nigora Tosheva", "authorRole": "Nutritsiolog", "readMinutes": 5,
    },
    {
        "slug": "uyqu-gigienasi", "categoryKey": "sleep", "premium": False, "published": True,
        "title": "Uyqu gigiyenasi: 7 ta oddiy qoida",
        "excerpt": "Uyqu sifati ko'pincha davomiyligidan muhimroq.",
        "blocks": [
            block("paragraph", text="Uyquga ketish qiyinligi odatda kechqurungi odatlardan boshlanadi, to'shakdan emas."),
            block("bullets", items=["Har kuni bir vaqtda yotish va turish", "Yotishdan 1 soat oldin ekranlarni qo'yish", "Xona salqin va qorong'i bo'lsin", "Kunduzi 20 daqiqadan uzun uxlamaslik", "Kechki ovqat yotishdan 3 soat oldin", "Kofein tushdan keyin yo'q", "Uyqu kelmasa — o'rindan turib, xira yorug'da kitob"]),
        ],
        "author": "Kamola Yusupova", "authorRole": "Nevrolog", "readMinutes": 3,
    },
    {
        "slug": "gormonlar-qanday-ishlaydi", "categoryKey": "hormones", "premium": True, "published": True,
        "title": "Sikl gormonlari: kim nima qiladi",
        "excerpt": "Estrogen, progesteron, FSH va LH — to'rt gormon va ularning oydagi vazifasi.",
        "blocks": [
            block("paragraph", text="Sikl — to'rt gormonning navbatma-navbat ishlashi. Har birining o'z vazifasi va o'z cho'qqisi bor."),
            block("heading", text="Follikulyar faza"),
            block("paragraph", text="FSH tuxumdonlardagi follikulalarni o'stiradi, estrogen ko'tariladi. Energiya va kayfiyat odatda shu davrda eng yaxshi."),
            block("heading", text="Ovulyatsiya"),
            block("paragraph", text="LH keskin ko'tariladi va tuxum hujayra chiqadi. Bu 24 soatlik oyna, homiladorlik ehtimoli eng yuqori."),
            block("heading", text="Lyuteal faza"),
            block("paragraph", text="Progesteron ustunlik qiladi. Tana harorati biroz ko'tariladi, ishtaha ortadi, kayfiyat o'zgaruvchan bo'lishi mumkin."),
            block("note", text="Bu umumiy manzara. Sizning siklingiz 21 kundan 35 kungacha bo'lishi va baribir sog'lom hisoblanishi mumkin."),
        ],
        "author": "Dilbar Aliyeva", "authorRole": "Akusher-ginekolog", "readMinutes": 7,
    },
    {
        "slug": "nafas-mashqlari", "categoryKey": "mind", "premium": False, "published": True,
        "title": "4-7-8 nafas mashqi: stressni 5 daqiqada pasaytirish",
        "excerpt": "Parasimpatik nerv tizimini yoqadigan eng oddiy usul.",
        "blocks": [
            block("paragraph", text="Uzun nafas chiqarish yurak urishini sekinlashtiradi va tanaga xavf o'tganini bildiradi."),
            block("heading", text="Qanday bajariladi"),
            block("bullets", items=["Burun orqali 4 sanoq nafas oling", "7 sanoq ushlab turing", "Og'iz orqali 8 sanoq chiqaring", "4 marta takrorlang"]),
            block("note", text="Bosh aylansa to'xtating va oddiy nafasga qayting. Kuniga ikki marta yetarli."),
        ],
        "author": "Kamola Yusupova", "authorRole": "Nevrolog", "readMinutes": 3,
    },
    {
        "slug": "homiladorlik-ovqatlanish", "categoryKey": "nutrition", "premium": True, "published": True,
        "title": "Homiladorlikda ovqatlanish: nima kerak, nimadan saqlanish kerak",
        "excerpt": "Folat, temir, kalsiy va yod — va ro'yxatdan chiqarish kerak bo'lgan mahsulotlar.",
        "blocks": [
            block("paragraph", text="Homiladorlikda kaloriya emas, sifat muhim. Birinchi trimestrda qo'shimcha kaloriya deyarli kerak emas."),
            block("heading", text="Kerakli moddalar"),
            block("bullets", items=["Folat — asab naychasi uchun, homiladorlikdan oldin boshlanadi", "Temir — qon hajmi 40% ga oshadi", "Kalsiy va D vitamini", "Yod — miya rivojlanishi uchun"]),
            block("heading", text="Saqlanish kerak"),
            block("bullets", items=["Xom yoki chala pishgan go'sht va tuxum", "Pasterizatsiya qilinmagan sut mahsulotlari", "Yirik yirtqich baliqlar — simob", "Alkogol — xavfsiz miqdor yo'q"]),
            block("note", text="Har qanday qo'shimcha dori shifokor bilan kelishiladi."),
        ],
        "author": "Nigora Tosheva", "authorRole": "Nutritsiolog", "readMinutes": 6,
    },
    {
        "slug": "menopauza-issiq-xuruj", "categoryKey": "hormones", "premium": False, "published": True,
        "title": "Issiq xurujlar: nega bo'ladi va nima yordam beradi",
        "excerpt": "Perimenopauzaning eng ko'p uchraydigan belgisi va uni yengillashtirish yo'llari.",
        "blocks": [
            block("paragraph", text="Estrogen pasayganda miyaning harorat markazi sezgirroq bo'ladi va tanani kerak bo'lmaganda sovutishga urinadi."),
            block("heading", text="Nima yordam beradi"),
            block("bullets", items=["Qatlamli kiyinish", "Xonani salqin saqlash, paxta choyshab", "Achchiq ovqat, kofein va alkogolni kamaytirish", "Muntazam harakat va vazn nazorati"]),
            block("note", text="Xurujlar kunlik hayotga xalaqit bersa, gormonal terapiya haqida shifokor bilan gaplashing."),
        ],
        "author": "Dilbar Aliyeva", "authorRole": "Akusher-ginekolog", "readMinutes": 4,
    },
    {
        "slug": "sikl-va-sport", "categoryKey": "cycle", "premium": True, "published": False,
        "title": "Siklga moslashtirilgan mashqlar",
        "excerpt": "Qaysi fazada kuch, qaysisida tiklanish — qoralama.",
        "blocks": [
            block("paragraph", text="Follikulyar fazada kuch va chidamlilik yuqori, lyuteal fazada tiklanish sekinroq."),
            block("note", text="Qoralama: mashqlar ro'yxati va manbalar qo'shilishi kerak."),
        ],
        "author": "Nigora Tosheva", "authorRole": "Nutritsiolog", "readMinutes": 5,
    },
]


def seed_articles(admin: str):
    """The Bilim library. The app carries no articles of its own, so this is all of it."""
    existing = {a["slug"] for a in (call("GET", "/admin/content/articles", token=admin) or [])}
    for article in ARTICLES:
        slug = article["slug"]
        if slug in existing:
            continue
        body = {
            "kind": "article",
            "categoryKey": article["categoryKey"],
            "title": article["title"],
            "excerpt": article["excerpt"],
            "blocks": article["blocks"],
            "premium": article["premium"],
            "author": article["author"],
            "authorRole": article["authorRole"],
            "readMinutes": article["readMinutes"],
            "reviewedBy": "SADORA tibbiy kengashi",
            "disclaimer": "Ma'lumot tavsiya xarakterida. Tashxis va davolash uchun shifokorga murojaat qiling.",
        }
        call("POST", "/admin/content/articles", {"slug": slug, "article": body}, admin, ok=(200, 201, 409))
        # Publishing is a separate action from saving, so a draft stays a draft.
        if article["published"]:
            call("PUT", f"/admin/content/articles/{slug}/published", {"published": True}, admin)
        print(f"  {'nashr' if article['published'] else 'qoralama'}: {article['title']}")


if __name__ == "__main__":
    print(f"Seeding against {BASE}")
    session = call("POST", "/admin/auth/login", {"email": ADMIN[0], "password": ADMIN[1]})
    admin = session["accessToken"]

    print("\nDemo accounts")
    everyone = []
    for spec in USERS:
        seed_user(spec, everyone)
    cross_link(everyone)
    grant_premium(everyone)

    print("\nNur va streak")
    seed_rewards(everyone, admin)

    print("\nBackground accounts")
    seed_background(admin)

    print("\nBilim library")
    seed_articles(admin)

    print("\nDone.")
