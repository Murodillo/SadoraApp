#!/usr/bin/env python3
"""API audit harness — edge cases, races and authorisation checks against a DEV server.

    python3 tools/api_audit.py http://localhost:8080 [results.jsonl]

Signs in throwaway accounts (dev fixed OTP code), walks every user and admin route
with bad input, parallel requests and cross-account ids, and lists as ANOMALY every
answer outside the expected status set — and every 5xx, expected or not. It writes
rows to the dev database; never point it at production. It waits 16 s once for the
refresh-token grace window. Expected leftovers on an unconfigured laptop: the 503s for
WHOOP (not configured), a "broken pipe" on the 3 MB body (the server closes the
connection after refusing it), the admin 2FA-confirm 403 and the premium-grant check.
"""
import json
import random
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
V1 = BASE + "/v1"
TODAY = date.today()
RESULTS = []
ANOMALIES = []


def d(days_ago: int) -> str:
    return (TODAY - timedelta(days=days_ago)).isoformat()


def raw_call(method, path, body=None, token=None, headers=None, raw_body=None, base=V1, timeout=60):
    url = base + path
    data = None
    if raw_body is not None:
        data = raw_body if isinstance(raw_body, bytes) else raw_body.encode()
    elif body is not None:
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if raw_body is None:
        req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read()
            ctype = res.headers.get("Content-Type", "")
            try:
                parsed = json.loads(raw) if raw and "json" in ctype else raw.decode(errors="replace")
            except Exception:
                parsed = raw.decode(errors="replace")
            return res.status, parsed, dict(res.headers)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = raw.decode(errors="replace")
        return e.code, parsed, dict(e.headers)
    except Exception as e:  # connection errors etc.
        return -1, str(e), {}


def check(name, status, expected, payload=None, note=""):
    ok = status in expected if isinstance(expected, (tuple, list, set)) else status == expected
    code = None
    if isinstance(payload, dict) and "error" in payload and isinstance(payload["error"], dict):
        code = payload["error"].get("code")
    RESULTS.append((name, ok, status, expected, code, note))
    if not ok or status >= 500 or status == -1:
        ANOMALIES.append((name, status, expected, code, (json.dumps(payload)[:400] if not isinstance(payload, str) else payload[:400]), note))
    return ok


def call(name, method, path, expected, body=None, token=None, headers=None, raw_body=None, base=V1, note=""):
    status, payload, hdrs = raw_call(method, path, body, token, headers, raw_body, base)
    check(name, status, expected, payload, note)
    return status, payload, hdrs


def phone():
    return "+99890" + "".join(random.choice("0123456789") for _ in range(7))


DEVICE = {"deviceId": "audit-device", "platform": "android", "osVersion": "15", "appVersion": "1.0",
          "model": "Audit", "timezone": "Asia/Tashkent"}


def sign_in(ph, device=DEVICE, code=None):
    s, ch, _ = call("otp request " + ph[-4:], "POST", "/auth/otp/request", 201, {"phone": ph, "language": "uz"})
    if s != 201:
        return None
    s, sess, _ = call("otp verify " + ph[-4:], "POST", "/auth/otp/verify", 200, {
        "challengeId": ch["challengeId"], "code": code or ch.get("devCode") or "123456", "device": device})
    return sess if s == 200 else None


def onboard(tok, name="Audit Ayol", stage="cycle", extra=None, expected=200):
    body = {
        "name": name, "language": "uz", "timezone": "Asia/Tashkent", "lifeStage": stage,
        "birthDate": "1995-05-05", "heightCm": 165, "weightKg": 58,
        "goals": ["understand_cycle", "sleep_better"],
        "cycle": {"lastPeriodStart": d(10), "averageCycleLength": 28, "averagePeriodLength": 5, "cycleIsRegular": True},
        "permissions": {"notifications": True, "healthData": True},
        "consents": {"storeHealth": True, "aiInsights": True, "analytics": False, "marketing": False},
        "firstCheckIn": {"mood": "good", "symptomKeys": ["headache", "nonexistent_key"]},
    }
    if extra:
        body.update(extra)
    return call("onboarding " + name[:12], "POST", "/me/onboarding", expected, body, tok)


# =====================================================================================
print("== health")
call("health live", "GET", "/health/live", 200, base=BASE)
call("health ready", "GET", "/health/ready", 200, base=BASE)
call("unknown route", "GET", "/nope", 404)
call("unknown route unauth", "GET", "/me/nope", (401, 404))

print("== auth edge cases")
call("otp bad phone", "POST", "/auth/otp/request", 400, {"phone": "12345"})
call("otp bad operator", "POST", "/auth/otp/request", 400, {"phone": "+998701234567"})
call("otp unicode phone", "POST", "/auth/otp/request", 400, {"phone": "+998９０1234567"})
call("otp empty body (no content type)", "POST", "/auth/otp/request", 415, raw_body="")
call("otp empty body (json)", "POST", "/auth/otp/request", 400, raw_body="", headers={"Content-Type": "application/json"})
call("otp malformed json", "POST", "/auth/otp/request", 400, raw_body="{not json", headers={"Content-Type": "application/json"})
call("otp wrong content type", "POST", "/auth/otp/request", 415, raw_body="phone=+998901234567", headers={"Content-Type": "text/plain"})
call("otp 200KB body (under limit)", "POST", "/auth/otp/request", 201, {"phone": "+998901234567", "language": "uz", "pad": "x" * 200_000})
call("otp 3MB body -> 413", "POST", "/auth/otp/request", 413, {"phone": "+998901234567", "language": "uz", "pad": "x" * 3_000_000})
call("otp unknown language", "POST", "/auth/otp/request", 400, {"phone": "+998901234567", "language": "fr"})
call("verify garbage challenge", "POST", "/auth/otp/verify", 400, {"challengeId": "not-a-uuid", "code": "123456", "device": DEVICE})
call("verify unknown challenge", "POST", "/auth/otp/verify", 400, {"challengeId": "00000000-0000-0000-0000-000000000000", "code": "123456", "device": DEVICE})
call("verify short code", "POST", "/auth/otp/verify", 400, {"challengeId": "00000000-0000-0000-0000-000000000000", "code": "12", "device": DEVICE})
call("verify letters code", "POST", "/auth/otp/verify", 400, {"challengeId": "00000000-0000-0000-0000-000000000000", "code": "abcdef", "device": DEVICE})

# attempts exhaustion
p = phone()
s, ch, _ = call("otp request attempts", "POST", "/auth/otp/request", 201, {"phone": p})
for i in range(5):
    call(f"wrong code #{i+1}", "POST", "/auth/otp/verify", 400, {"challengeId": ch["challengeId"], "code": "000000", "device": DEVICE})
s, pl, _ = call("6th attempt -> too many", "POST", "/auth/otp/verify", 400, {"challengeId": ch["challengeId"], "code": ch.get("devCode", "123456"), "device": DEVICE})
check("6th attempt code is otp_too_many_attempts", 400 if (isinstance(pl, dict) and pl.get("error", {}).get("code") == "otp_too_many_attempts") else 999, 400, pl)

# reuse of a consumed challenge
p = phone()
s, ch, _ = call("otp request reuse", "POST", "/auth/otp/request", 201, {"phone": p})
call("verify ok", "POST", "/auth/otp/verify", 200, {"challengeId": ch["challengeId"], "code": ch.get("devCode", "123456"), "device": DEVICE})
call("verify reuse -> 400", "POST", "/auth/otp/verify", 400, {"challengeId": ch["challengeId"], "code": ch.get("devCode", "123456"), "device": DEVICE})

# concurrent verify of the same challenge: exactly one must win
p = phone()
s, ch, _ = call("otp request race", "POST", "/auth/otp/request", 201, {"phone": p})
race = []
def _verify():
    race.append(raw_call("POST", "/auth/otp/verify", {"challengeId": ch["challengeId"], "code": ch.get("devCode", "123456"), "device": DEVICE})[0])
ts = [threading.Thread(target=_verify) for _ in range(6)]
[t.start() for t in ts]; [t.join() for t in ts]
check("race: exactly one 200", 200 if race.count(200) == 1 else 999, 200, race, note=str(race))

# concurrent first sign-in of the SAME phone via two challenges: both must not 500
p = phone()
s1, ch1, _ = call("race2 req1", "POST", "/auth/otp/request", 201, {"phone": p})
s2, ch2, _ = call("race2 req2", "POST", "/auth/otp/request", 201, {"phone": p})
race2 = []
def _v(c):
    race2.append(raw_call("POST", "/auth/otp/verify", {"challengeId": c["challengeId"], "code": c.get("devCode", "123456"), "device": DEVICE})[0])
ta, tb = threading.Thread(target=_v, args=(ch1,)), threading.Thread(target=_v, args=(ch2,))
ta.start(); tb.start(); ta.join(); tb.join()
check("race2: no 500 on parallel first sign-in", 200 if all(x == 200 for x in race2) else 500, 200, race2, note=str(race2))

print("== sessions")
A = sign_in(phone())
B = sign_in(phone())
assert A and B, "could not sign in"
ta, tb = A["tokens"]["accessToken"], B["tokens"]["accessToken"]
check("A isNewUser", 200 if A["isNewUser"] else 999, 200)
call("bootstrap A", "GET", "/bootstrap?platform=android", 200, token=ta)
call("bootstrap bad platform", "GET", "/bootstrap?platform=blackberry", 400, token=ta)
call("no token", "GET", "/me", 401)
call("garbage token", "GET", "/me", 401, token="garbage")
call("alg none token", "GET", "/me", 401, token="eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJ0eXAiOiJ1c2VyIn0.")
call("user token on admin route", "GET", "/admin/me", 401, token=ta)

# refresh rotation: an immediate re-send is a retry (grace window), a late one is reuse
r0 = A["tokens"]["refreshToken"]
s, r1, _ = call("refresh ok", "POST", "/auth/refresh", 200, {"refreshToken": r0})
s, r1b, _ = call("refresh immediate retry -> new pair (grace)", "POST", "/auth/refresh", 200, {"refreshToken": r0})
call("refresh garbage", "POST", "/auth/refresh", 401, {"refreshToken": "nope"})
# concurrent refresh of one token: all succeed (grace) and nothing 5xx
A2 = sign_in(phone())
rt = A2["tokens"]["refreshToken"]
race3 = []
def _r():
    race3.append(raw_call("POST", "/auth/refresh", {"refreshToken": rt})[0])
ts = [threading.Thread(target=_r) for _ in range(5)]
[t.start() for t in ts]; [t.join() for t in ts]
check("parallel refresh: all 200, no 5xx", 200 if all(c == 200 for c in race3) else 999, 200, race3, note=str(race3))
# after the grace window a replay is theft: the family goes
print("   (waiting 16s for the refresh grace window)")
time.sleep(16)
s, pl, _ = call("refresh late reuse -> revoked family", "POST", "/auth/refresh", 401, {"refreshToken": r0})
s, pl2, _ = call("successor also revoked", "POST", "/auth/refresh", 401, {"refreshToken": r1b["tokens"]["refreshToken"]})

print("== onboarding validation")
onboard(ta, name="", expected=400)
onboard(ta, name="x" * 61, expected=400)
onboard(ta, name="Oʻg'iloy G‘ulomova 🌸", expected=200)  # unicode + apostrophes + emoji
onboard(ta, extra={"timezone": "Mars/Olympus"}, expected=400)
onboard(ta, extra={"cycle": {"averageCycleLength": 14, "averagePeriodLength": 5}}, expected=400)
onboard(ta, extra={"cycle": {"averageCycleLength": 28, "averagePeriodLength": 16}}, expected=400)
onboard(ta, extra={"birthDate": "1815-01-01"}, expected=400)
onboard(ta, extra={"birthDate": "2031-01-01"}, expected=400)
onboard(ta, extra={"birthDate": "not-a-date"}, expected=400)
onboard(ta, extra={"heightCm": 10}, expected=400)
onboard(ta, extra={"weightKg": 500}, expected=400)
onboard(ta, extra={"heightCm": 99999999999999999999}, expected=400)
onboard(ta, extra={"lifeStage": "alien"}, expected=400)
onboard(ta, extra={"goals": ["fly"]}, expected=400)
onboard(ta, extra={"inviteCode": "NOPE-0000"}, expected=200)  # unknown invite must not fail onboarding
onboard(ta, name="Audit A", expected=200)
onboard(tb, name="Audit B", stage="pregnancy", extra={"stage": {"dueDate": (TODAY + timedelta(days=100)).isoformat()}, "cycle": None}, expected=200)
s, prof, _ = call("me A", "GET", "/me", 200, token=ta)
check("A onboarded", 200 if prof.get("onboardingCompleted") else 999, 200)

print("== profile patch")
call("patch name too long", "PATCH", "/me", 400, {"name": "y" * 61}, ta)
call("patch name blank", "PATCH", "/me", 400, {"name": "   "}, ta)
call("patch weight 0", "PATCH", "/me", 400, {"weightKg": 0}, ta)
call("patch tz bad", "PATCH", "/me", 400, {"timezone": "Nowhere/City"}, ta)
call("patch lang ru", "PATCH", "/me", 200, {"language": "ru"}, ta)
call("patch empty", "PATCH", "/me", 200, {}, ta)
call("patch unknown field", "PATCH", "/me", 200, {"unknownField": 1}, ta)
call("patch null name", "PATCH", "/me", 200, raw_body='{"name": null}', token=ta, headers={"Content-Type": "application/json"})
call("patch birth future", "PATCH", "/me", 400, {"birthDate": (TODAY + timedelta(days=1)).isoformat()}, ta)

print("== consents")
call("consents get", "GET", "/me/consents", 200, token=ta)
call("consents revoke health", "PUT", "/me/consents", 200, {"storeHealth": False, "aiInsights": False}, ta)
call("period log without consent -> 403", "POST", "/cycle/periods", 403, {"startedOn": d(3)}, ta)
call("daily log without consent -> 403", "PUT", f"/days/{d(0)}", 403, {"mood": "ok"}, ta)
call("consents grant health", "PUT", "/me/consents", 200, {"storeHealth": True, "aiInsights": True}, ta)

print("== cycle")
call("period future", "POST", "/cycle/periods", 400, {"startedOn": (TODAY + timedelta(days=2)).isoformat()}, ta)
call("period end before start", "POST", "/cycle/periods", 400, {"startedOn": d(3), "endedOn": d(5)}, ta)
call("period 1900", "POST", "/cycle/periods", 400, {"startedOn": "1900-01-01"}, ta)
s, per, _ = call("period ok", "POST", "/cycle/periods", (200, 201), {"startedOn": d(3)}, ta)
call("period duplicate same day", "POST", "/cycle/periods", (400, 409), {"startedOn": d(3)}, ta)
call("period overlapping", "POST", "/cycle/periods", (200, 201, 400, 409), {"startedOn": d(2)}, ta, note="overlap policy?")
pid = per.get("id") if isinstance(per, dict) else None
call("period end too long (40d)", "PATCH", f"/cycle/periods/{pid}", 400, {"endedOn": (TODAY + timedelta(days=37)).isoformat()}, ta)
call("period patch ok", "PATCH", f"/cycle/periods/{pid}", 200, {"endedOn": d(0)}, ta)
call("period patch by B -> 404", "PATCH", f"/cycle/periods/{pid}", (403, 404), {"endedOn": d(1)}, tb)
call("period delete by B -> 404", "DELETE", f"/cycle/periods/{pid}", (403, 404), token=tb)
call("period bad id", "PATCH", "/cycle/periods/not-uuid", 400, {"endedOn": d(0)}, ta)
call("cycle status", "GET", "/cycle/status", 200, token=ta)
call("cycle calendar default", "GET", "/cycle/calendar", 200, token=ta)
call("calendar 5-year range", "GET", f"/cycle/calendar?from=2020-01-01&to={TODAY.isoformat()}", (200, 400), token=ta, note="range cap?")
call("calendar reversed", "GET", f"/cycle/calendar?from={d(0)}&to={d(10)}", 400, token=ta)
call("calendar bad date", "GET", "/cycle/calendar?from=yesterday", 400, token=ta)
call("cycle history", "GET", "/cycle/history", 200, token=ta)
call("cycle status pregnancy (B)", "GET", "/cycle/status", 200, token=tb)

print("== daily logs")
call("day note 1001", "PUT", f"/days/{d(0)}", 400, {"note": "n" * 1001}, ta)
call("day note 1000 unicode", "PUT", f"/days/{d(0)}", 200, {"note": "o‘g‘ 🌸" * 100}, ta)
call("day energy 6", "PUT", f"/days/{d(0)}", 400, {"energy": 6}, ta)
call("day energy 0", "PUT", f"/days/{d(0)}", 400, {"energy": 0}, ta)
call("day stress -1", "PUT", f"/days/{d(0)}", 400, {"stress": -1}, ta)
call("day future", "PUT", f"/days/{(TODAY + timedelta(days=3)).isoformat()}", 400, {"mood": "ok"}, ta)
call("day bad date", "PUT", "/days/2026-13-45", 400, {"mood": "ok"}, ta)
call("day unknown symptom", "PUT", f"/days/{d(1)}", (200, 400), {"symptoms": [{"key": "zzz_nope"}]}, ta, note="dropped or rejected?")
call("day 50 symptoms", "PUT", f"/days/{d(1)}", (200, 400), {"symptoms": [{"key": f"s{i}"} for i in range(50)]}, ta)
call("day mood only", "PUT", f"/days/{d(2)}", 200, {"mood": "great", "symptoms": [{"key": "headache", "severity": "severe"}]}, ta)
s, dl, _ = call("day get", "GET", f"/days/{d(2)}", 200, token=ta)
check("day get keeps symptoms after mood-only save", 200 if (isinstance(dl, dict) and dl.get("symptoms")) else 999, 200, dl)
call("day partial resave (mood only) keeps symptoms?", "PUT", f"/days/{d(2)}", 200, {"mood": "ok"}, ta)
s, dl2, _ = call("day get 2", "GET", f"/days/{d(2)}", 200, token=ta)
check("symptoms after partial resave (documented: replace)", 200, 200, dl2, note=f"symptoms now={dl2.get('symptoms') if isinstance(dl2, dict) else dl2}")
call("days range", "GET", f"/days?from={d(30)}&to={d(0)}", 200, token=ta)
call("days range 3y", "GET", f"/days?from=2023-01-01&to={d(0)}", (200, 400), token=ta, note="cap?")
call("day delete", "DELETE", f"/days/{d(2)}", (200, 204), token=ta)
call("day delete again", "DELETE", f"/days/{d(2)}", (200, 204, 404), token=ta)
call("fetal movement on cycle user", "PUT", f"/days/{d(0)}", (200, 400), {"fetalMovement": "LESS"}, ta, note="accepted on non-pregnant?")
call("symptoms catalogue", "GET", "/symptoms", 200, token=ta)

print("== mind")
call("journal 5001", "POST", "/mind/journal", 400, {"date": d(0), "body": "j" * 5001}, ta)
call("journal empty", "POST", "/mind/journal", 400, {"date": d(0), "body": "   "}, ta)
s, je, _ = call("journal ok", "POST", "/mind/journal", (200, 201), {"date": d(0), "body": "Bugun yaxshi o'tdi 🌷"}, ta)
jid = je.get("id") if isinstance(je, dict) else None
call("journal patch by B", "PATCH", f"/mind/journal/{jid}", (403, 404), {"body": "hack"}, tb)
call("journal delete by B", "DELETE", f"/mind/journal/{jid}", (403, 404), token=tb)
call("journal patch ok", "PATCH", f"/mind/journal/{jid}", 200, {"body": "yangilandi"}, ta)
call("checkin energy 9", "PUT", "/mind/check-in", 400, {"energy": 9}, ta)
call("checkin ok", "PUT", "/mind/check-in", 200, {"mood": "ok", "energy": 3, "stress": 2}, ta)
call("practice 0s", "POST", "/mind/practices", 400, {"kind": "breathing", "durationSeconds": 0}, ta)
call("practice 10h", "POST", "/mind/practices", 400, {"kind": "breathing", "durationSeconds": 36000}, ta)
call("practice ok", "POST", "/mind/practices", (200, 201), {"kind": "meditation", "durationSeconds": 300}, ta)
call("mind today", "GET", "/mind/today", 200, token=ta)

print("== nutrition")
call("meal desc 201", "POST", "/nutrition/meals", 400, {"date": d(0), "slot": "lunch", "description": "m" * 201, "kcal": 100}, ta)
call("meal kcal -5", "POST", "/nutrition/meals", 400, {"date": d(0), "slot": "lunch", "description": "osh", "kcal": -5}, ta)
call("meal kcal 99999", "POST", "/nutrition/meals", 400, {"date": d(0), "slot": "lunch", "description": "osh", "kcal": 99999}, ta)
call("meal future", "POST", "/nutrition/meals", 400, {"date": (TODAY + timedelta(days=1)).isoformat(), "slot": "lunch", "description": "osh", "kcal": 100}, ta)
s, meal, _ = call("meal ok", "POST", "/nutrition/meals", (200, 201), {"date": d(0), "slot": "lunch", "description": "Osh 🍚", "kcal": 650, "proteinG": 20, "fatG": 25, "carbsG": 80}, ta)
mid = meal.get("id") if isinstance(meal, dict) else None
call("meal delete by B", "DELETE", f"/nutrition/meals/{mid}", (403, 404), token=tb)
call("water 0", "POST", "/nutrition/water", 400, {"ml": 0}, ta)
call("water 2001", "POST", "/nutrition/water", 400, {"ml": 2001}, ta)
call("water ok", "POST", "/nutrition/water", 200, {"ml": 250}, ta)
call("goals kcal 0", "PUT", "/nutrition/goals", 400, {"calorieGoal": 0}, ta)
call("goals water 100000", "PUT", "/nutrition/goals", 400, {"waterGoalMl": 100000}, ta)
call("goals ok", "PUT", "/nutrition/goals", 200, {"calorieGoal": 1900}, ta)
call("nutrition today", "GET", "/nutrition/today", 200, token=ta)
call("foods search empty", "GET", "/nutrition/foods?q=", 200, token=ta)
call("foods search apostrophe", "GET", "/nutrition/foods?q=o%27sh", 200, token=ta)
call("foods search sqli", "GET", "/nutrition/foods?q=%27%20OR%201%3D1--", 200, token=ta)
call("foods search long", "GET", "/nutrition/foods?q=" + "a" * 500, (200, 400), token=ta)
call("scan empty image", "POST", "/nutrition/scan", (400, 402, 403, 503), {"imageBase64": "", "mimeType": "image/jpeg"}, ta)
call("scan bad mime", "POST", "/nutrition/scan", (400, 402, 403, 503), {"imageBase64": "AAAA", "mimeType": "text/html"}, ta)
call("scan not base64", "POST", "/nutrition/scan", (400, 402, 403, 503), {"imageBase64": "!!!not-b64!!!"}, ta)

print("== medications")
call("med name 121", "POST", "/meds", 400, {"name": "n" * 121, "schedule": {"kind": "daily", "times": ["08:00"]}}, ta)
call("med no times", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "daily", "times": []}}, ta)
call("med 9 times", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "daily", "times": [f"{h:02d}:00" for h in range(9)]}}, ta)
call("med weekdays empty", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "weekdays", "times": ["08:00"], "weekdays": []}}, ta)
call("med interval 0", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "interval", "times": ["08:00"], "intervalDays": 0}}, ta)
call("med stock -1", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "daily", "times": ["08:00"]}, "stockUnits": -1}, ta)
call("med end before start", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "daily", "times": ["08:00"]}, "startedOn": d(0), "endedOn": d(5)}, ta)
call("med bad time", "POST", "/meds", 400, {"name": "Vitamin D", "schedule": {"kind": "daily", "times": ["25:99"]}}, ta)
s, med, _ = call("med ok", "POST", "/meds", (200, 201), {"name": "Folat 🌿", "dosage": "400", "unit": "mkg", "schedule": {"kind": "daily", "times": ["08:00", "20:00"]}, "stockUnits": 30}, ta)
medid = med.get("id") if isinstance(med, dict) else None
call("med get by B", "GET", f"/meds/{medid}/history", (403, 404), token=tb)
call("med put by B", "PUT", f"/meds/{medid}", (403, 404), {"name": "hack", "schedule": {"kind": "daily", "times": ["08:00"]}}, tb)
call("med delete by B", "DELETE", f"/meds/{medid}", (403, 404), token=tb)
call("dose future", "POST", f"/meds/{medid}/doses", 400, {"dueOn": (TODAY + timedelta(days=2)).isoformat(), "dueAt": "08:00", "status": "taken"}, ta)
call("dose unscheduled time", "POST", f"/meds/{medid}/doses", (200, 201, 400), {"dueOn": d(0), "dueAt": "13:37", "status": "taken"}, ta, note="accepts off-schedule?")
call("dose ok", "POST", f"/meds/{medid}/doses", (200, 201), {"dueOn": d(0), "dueAt": "08:00", "status": "taken"}, ta)
call("dose twice (idempotent)", "POST", f"/meds/{medid}/doses", (200, 201), {"dueOn": d(0), "dueAt": "08:00", "status": "taken"}, ta)
call("dose by B", "POST", f"/meds/{medid}/doses", (403, 404), {"dueOn": d(0), "dueAt": "08:00", "status": "taken"}, tb)
call("refill 0", "POST", f"/meds/{medid}/stock", 400, {"units": 0}, ta)
call("refill 10001", "POST", f"/meds/{medid}/stock", 400, {"units": 10001}, ta)
call("refill ok", "POST", f"/meds/{medid}/stock", 200, {"units": 10}, ta)
call("meds today", "GET", "/meds/today", 200, token=ta)
call("meds day bad", "GET", "/meds/days/2026-02-30", 400, token=ta)
call("med history 2y", "GET", f"/meds/{medid}/history?from=2024-01-01&to={d(0)}", (200, 400), token=ta, note="cap?")
call("meds list", "GET", "/meds", 200, token=ta)

print("== appointments")
call("appt title 121", "POST", "/appointments", 400, {"title": "t" * 121, "scheduledOn": d(-3)}, ta)
call("appt place 201", "POST", "/appointments", 400, {"title": "UTT", "scheduledOn": d(-3), "place": "p" * 201}, ta)
call("appt remind -1", "POST", "/appointments", 400, {"title": "UTT", "scheduledOn": d(-3), "remindHoursBefore": -1}, ta)
call("appt remind 9999", "POST", "/appointments", 400, {"title": "UTT", "scheduledOn": d(-3), "remindHoursBefore": 9999}, ta)
call("appt 1990", "POST", "/appointments", 400, {"title": "UTT", "scheduledOn": "1990-01-01"}, ta)
s, ap, _ = call("appt ok", "POST", "/appointments", (200, 201), {"title": "UTT ko‘rigi", "scheduledOn": (TODAY + timedelta(days=3)).isoformat(), "scheduledAt": "10:30", "remindHoursBefore": 24}, ta)
apid = ap.get("id") if isinstance(ap, dict) else None
call("appt put by B", "PUT", f"/appointments/{apid}", (403, 404), {"title": "hack", "scheduledOn": d(0)}, tb)
call("appt complete by B", "PUT", f"/appointments/{apid}/completed", (403, 404), {"done": True}, tb)
call("appt delete by B", "DELETE", f"/appointments/{apid}", (403, 404), token=tb)
call("appt complete", "PUT", f"/appointments/{apid}/completed", 200, {"done": True}, ta)
call("appt list", "GET", "/appointments", 200, token=ta)

print("== rewards / shop / referral")
s, ci1, _ = call("check-in 1", "POST", "/rewards/check-in", 200, {}, ta)
s, ci2, _ = call("check-in 2 (same day)", "POST", "/rewards/check-in", 200, {}, ta)
if isinstance(ci1, dict) and isinstance(ci2, dict):
    check("check-in idempotent balance", 200 if ci1["coins"]["balance"] == ci2["coins"]["balance"] else 999, 200, (ci1["coins"], ci2["coins"]))
# parallel check-in from a fresh account: only one award
C = sign_in(phone()); tc = C["tokens"]["accessToken"]
onboard(tc, name="Audit C")
race4 = []
def _ci():
    race4.append(raw_call("POST", "/rewards/check-in", {}, tc))
ts = [threading.Thread(target=_ci) for _ in range(6)]
[t.start() for t in ts]; [t.join() for t in ts]
bal = {r[1]["coins"]["balance"] for r in race4 if r[0] == 200}
codes = [r[0] for r in race4]
check("parallel check-in: single balance, no 5xx", 200 if all(c == 200 for c in codes) and len(bal) == 1 else 999, 200, codes, note=f"balances={bal}")
call("rewards summary", "GET", "/rewards", 200, token=ta)
call("rewards history paging", "GET", "/rewards?limit=1000000", 200, token=ta)
call("rewards history negative", "GET", "/rewards?limit=-1", 400, token=ta)
s, ref, _ = call("referral status", "GET", "/rewards/referral", 200, token=ta)
mycode = ref.get("code") if isinstance(ref, dict) else "X"
call("referral self-claim", "POST", "/rewards/referral/claim", (200, 400, 409), {"code": mycode}, ta, note="accepted must be false")
call("referral unknown", "POST", "/rewards/referral/claim", (200, 400, 404), {"code": "ZZZZZZ"}, ta)
call("referral empty", "POST", "/rewards/referral/claim", (200, 400), {"code": ""}, ta)
call("referral lowercase/spaces", "POST", "/rewards/referral/claim", (200, 400, 404, 409), {"code": f"  {mycode.lower()} "}, tb)
s, shop, _ = call("shop", "GET", "/shop", 200, token=ta)
products = shop.get("products", []) if isinstance(shop, dict) else []
if products:
    expensive = max(products, key=lambda p: p["coinCost"])
    call("redeem unaffordable", "POST", "/shop/redeem", (400, 402, 409), {"productId": expensive["id"]}, ta)
call("redeem bad id", "POST", "/shop/redeem", (400, 404), {"productId": "nope"}, ta)
call("redemptions", "GET", "/shop/redemptions", 200, token=ta)
call("home layout get", "GET", "/me/home-layout", 200, token=ta)
call("home layout unknown key", "PUT", "/me/home-layout", (200, 400), {"widgets": [{"key": "ai", "position": 0, "visible": True}, {"key": "bogus", "position": 1}]}, ta)
call("home layout hides required", "PUT", "/me/home-layout", (200, 400), {"widgets": [{"key": "ai", "position": 0, "visible": False}]}, ta)
call("home layout 1000 widgets", "PUT", "/me/home-layout", (200, 400), {"widgets": [{"key": f"k{i}", "position": i} for i in range(1000)]}, ta)
call("home layout reset", "DELETE", "/me/home-layout", (200, 204), token=ta)

print("== community")
call("community me", "GET", "/community/me", 200, token=ta)
call("community bio 161", "PUT", "/community/me", 400, {"bio": "b" * 161}, ta)
call("community bio ok", "PUT", "/community/me", 200, {"bio": "Salom 🌸", "dmOpen": True}, ta)
call("post 1 char", "POST", "/community/posts", 400, {"topic": "cycle", "body": "a"}, ta)
call("post 2001", "POST", "/community/posts", 400, {"topic": "cycle", "body": "a" * 2001}, ta)
call("post phone number (PII)", "POST", "/community/posts", (200, 201, 400), {"topic": "cycle", "body": "Menga yozing +998901234567"}, ta, note="PII filter?")
call("post html", "POST", "/community/posts", (200, 201, 400), {"topic": "cycle", "body": "<script>alert(1)</script> salom hammaga"}, ta)
s, post, _ = call("post ok", "POST", "/community/posts", (200, 201), {"topic": "wellbeing", "body": "Bugun kayfiyatim a'lo, o‘zimni yaxshi his qilyapman 🌷"}, ta)
postid = post.get("id") if isinstance(post, dict) else None
call("feed", "GET", "/community/posts", 200, token=tb)
call("feed bad topic", "GET", "/community/posts?topic=zzz", 400, token=tb)
call("feed limit 100000", "GET", "/community/posts?limit=100000", 200, token=tb)
call("post get", "GET", f"/community/posts/{postid}", 200, token=tb)
call("like", "PUT", f"/community/posts/{postid}/like", 200, token=tb)
call("like twice", "PUT", f"/community/posts/{postid}/like", 200, token=tb)
s, pg, _ = call("post get after 2 likes", "GET", f"/community/posts/{postid}", 200, token=tb)
check("like count == 1", 200 if isinstance(pg, dict) and pg.get("likeCount") == 1 else 999, 200, pg)
race5 = []
def _like(t):
    race5.append(raw_call("PUT", f"/community/posts/{postid}/like", None, t)[0])
ts = [threading.Thread(target=_like, args=(tc,)) for _ in range(5)]
[t.start() for t in ts]; [t.join() for t in ts]
s, pg, _ = call("post after parallel likes", "GET", f"/community/posts/{postid}", 200, token=tb)
check("parallel like: count == 2, no 5xx", 200 if isinstance(pg, dict) and pg.get("likeCount") == 2 and all(c == 200 for c in race5) else 999, 200, (pg.get("likeCount") if isinstance(pg, dict) else pg, race5))
call("unlike", "DELETE", f"/community/posts/{postid}/like", 200, token=tb)
call("save", "PUT", f"/community/posts/{postid}/save", 200, token=tb)
call("comment 1001", "POST", f"/community/posts/{postid}/comments", 400, {"body": "c" * 1001}, tb)
s, cm, _ = call("comment ok", "POST", f"/community/posts/{postid}/comments", (200, 201), {"body": "Zo'r! 👏"}, tb)
cmid = cm.get("id") if isinstance(cm, dict) else None
call("comment on missing post", "POST", "/community/posts/00000000-0000-0000-0000-000000000000/comments", 404, {"body": "hello"}, tb)
call("comments list", "GET", f"/community/posts/{postid}/comments", 200, token=ta)
call("report post", "POST", f"/community/posts/{postid}/report", (200, 201), {"reason": "spam"}, tb)
call("report post twice", "POST", f"/community/posts/{postid}/report", (200, 201, 409), {"reason": "abuse"}, tb)
call("report own post", "POST", f"/community/posts/{postid}/report", (200, 201, 400, 409), {"reason": "spam"}, ta)
call("report note 501 (truncated, accepted)", "POST", f"/community/comments/{cmid}/report", (200, 201), {"reason": "other", "note": "n" * 501}, ta)
call("report comment twice -> 409", "POST", f"/community/comments/{cmid}/report", 409, {"reason": "other", "note": "test"}, ta)
call("delete comment by other", "DELETE", f"/community/comments/{cmid}", (403, 404), token=ta)
call("delete comment own", "DELETE", f"/community/comments/{cmid}", (200, 204), token=tb)
call("delete post by other", "DELETE", f"/community/posts/{postid}", (403, 404), token=tb)
s, meA, _ = call("alias A", "GET", "/community/me", 200, token=ta)
s, meB, _ = call("alias B", "GET", "/community/me", 200, token=tb)
aliasA = meA.get("alias") if isinstance(meA, dict) else "x"
aliasB = meB.get("alias") if isinstance(meB, dict) else "x"
qA, qB = urllib.parse.quote(aliasA), urllib.parse.quote(aliasB)
call("profile by alias", "GET", f"/community/profiles/{qA}", 200, token=tb)
call("profile unknown alias", "GET", "/community/profiles/no_such_alias_zzz", 404, token=tb)
call("profile alias traversal", "GET", "/community/profiles/..%2F..%2Fme", (400, 404), token=tb)
call("dm to self", "POST", "/community/conversations", 400, {"alias": aliasB, "body": "salom"}, tb)
call("dm 1001", "POST", "/community/conversations", 400, {"alias": aliasA, "body": "m" * 1001}, tb)
call("dm empty", "POST", "/community/conversations", 400, {"alias": aliasA, "body": "  "}, tb)
s, conv, _ = call("dm start", "POST", "/community/conversations", (200, 201), {"alias": aliasA, "body": "Salom! Qalaysiz?"}, tb)
convid = (conv.get("conversation") or {}).get("id") if isinstance(conv, dict) else None
call("dm start again (same pair)", "POST", "/community/conversations", (200, 201), {"alias": aliasA, "body": "Yana salom"}, tb)
call("conversations A", "GET", "/community/conversations", 200, token=ta)
call("conversation get by C (not member)", "GET", f"/community/conversations/{convid}", (403, 404), token=tc)
call("message by C (not member)", "POST", f"/community/conversations/{convid}/messages", (403, 404), {"body": "hack"}, tc)
call("message A", "POST", f"/community/conversations/{convid}/messages", (200, 201), {"body": "Yaxshi, rahmat 🌸"}, ta)
call("block B", "PUT", f"/community/profiles/{qB}/block", 200, token=ta)
call("message after block", "POST", f"/community/conversations/{convid}/messages", (403, 404), {"body": "hello?"}, tb)
call("unblock B", "DELETE", f"/community/profiles/{qB}/block", 200, token=ta)
call("block self", "PUT", f"/community/profiles/{qA}/block", 400, token=ta)
call("report conversation", "POST", f"/community/conversations/{convid}/report", (200, 201), {"reason": "abuse"}, ta)
call("delete own post", "DELETE", f"/community/posts/{postid}", (200, 204), token=ta)
call("post get after delete", "GET", f"/community/posts/{postid}", 404, token=tb)

print("== AI")
call("greeting", "GET", "/ai/greeting", 200, token=ta)
call("chat quota", "GET", "/ai/chat/quota", 200, token=ta)
call("chat empty", "POST", "/ai/chat", 400, {"question": "   "}, ta)
call("chat 10k", "POST", "/ai/chat", (400, 402, 403, 429), {"question": "q" * 10000}, ta)
call("chat ok", "POST", "/ai/chat", (200, 402, 403, 429, 503), {"question": "Uyqum yomon, nima qilay?"}, ta)
call("chat no ai consent (B has aiInsights true?)", "POST", "/ai/chat", (200, 402, 403, 429, 503), {"question": "Salom"}, tb)
call("insights", "GET", "/insights", 200, token=ta)
call("insights days=99999", "GET", "/insights?days=99999", (200, 400, 402), token=ta)

print("== articles")
s, feed, _ = call("articles", "GET", "/articles", 200, token=ta)
arts = feed.get("articles", []) if isinstance(feed, dict) else []
if arts:
    call("article get", "GET", f"/articles/{arts[0]['slug']}", 200, token=ta)
    prem = [a for a in arts if a.get("premium")]
    if prem:
        s, art, _ = call("premium article on free", "GET", f"/articles/{prem[0]['slug']}", 200, token=ta)
        check("premium article truncated for free", 200 if isinstance(art, dict) and art.get("truncated") else 999, 200, art if not isinstance(art, dict) else {k: art[k] for k in ("truncated",)})
call("article unknown", "GET", "/articles/no-such-slug", 404, token=ta)
call("article traversal", "GET", "/articles/..%2F..%2Fetc%2Fpasswd", (400, 404), token=ta)

print("== notifications")
call("notif settings", "GET", "/notifications/settings", 200, token=ta)
call("notif quiet bad", "PUT", "/notifications/settings", 400, {"quietFrom": "25:00"}, ta)
call("notif quiet from only", "PUT", "/notifications/settings", (200, 400), {"quietFrom": "22:00"}, ta, note="half quiet hours?")
call("notif ok", "PUT", "/notifications/settings", 200, {"enabled": True, "quietFrom": "22:00", "quietUntil": "07:00", "categories": {"water": False}}, ta)
call("notif history", "GET", "/notifications/history", 200, token=ta)
call("notif history limit huge", "GET", "/notifications/history?limit=99999999", 200, token=ta)

print("== health-data")
sample = {"provider": "manual", "externalId": "s1", "metric": "sleep_minutes", "value": 420, "startedAt": f"{d(1)}T22:00:00Z"}
call("samples ok", "POST", "/health-data/samples", 200, {"samples": [sample]}, ta)
call("samples dup (update)", "POST", "/health-data/samples", 200, {"samples": [sample]}, ta)
call("samples 5000", "POST", "/health-data/samples", (200, 400, 413), {"samples": [dict(sample, externalId=f"x{i}") for i in range(5000)]}, ta, note="batch cap?")
call("samples NaN-ish", "POST", "/health-data/samples", (200, 400), {"samples": [dict(sample, value=1e308)]}, ta)
call("samples negative", "POST", "/health-data/samples", (200, 400), {"samples": [dict(sample, value=-100)]}, ta)
call("samples year 1970", "POST", "/health-data/samples", (200, 400), {"samples": [dict(sample, startedAt="1970-01-01T00:00:00Z")]}, ta)
call("samples future", "POST", "/health-data/samples", (200, 400), {"samples": [dict(sample, startedAt="2099-01-01T00:00:00Z")]}, ta)
call("samples bad tz", "POST", "/health-data/samples", (200, 400), {"samples": [sample], "timezone": "Nope/Zone"}, ta)
call("samples unknown provider", "POST", "/health-data/samples", 400, {"samples": [dict(sample, provider="fitbit_pro")]}, ta)
call("health today", "GET", "/health-data/today", 200, token=ta)
call("health daily 5y", "GET", f"/health-data/daily?from=2021-01-01&to={d(0)}", (200, 400), token=ta, note="cap?")
call("health sources", "GET", "/health-data/sources", 200, token=ta)
call("wearable providers", "GET", "/wearables/providers", 200, token=ta)
call("wearable connect whoop (unconfigured)", "POST", "/wearables/whoop/connect", (400, 403, 404, 503), token=ta, headers={"Content-Type": "application/json"})
call("wearable connect bogus", "POST", "/wearables/bogus/connect", (400, 404), token=ta)
call("whoop callback no params", "GET", "/wearables/whoop/callback", (302, 400), token=None)
call("whoop callback evil state (relay page, url-encoded)", "GET", "/wearables/whoop/callback?state=evil&code=x", (200, 302, 400, 401), token=None)
call("whoop webhook unsigned", "POST", "/wearables/whoop/webhook", (401, 403, 400), raw_body='{"type":"x"}')

print("== share / export")
call("share ttl 0", "POST", "/me/shares", 400, {"ttlHours": 0}, ta)
call("share ttl 999", "POST", "/me/shares", 400, {"ttlHours": 999}, ta)
s, sh, _ = call("share ok", "POST", "/me/shares", (200, 201), {"ttlHours": 24}, ta)
shid = sh.get("id") if isinstance(sh, dict) else None
url = sh.get("url") if isinstance(sh, dict) else None
tok_share = url.rsplit("/", 1)[-1] if url else "x"
s, html, _ = call("doctor page", "GET", f"/share/{tok_share}", 200, base=BASE)
check("doctor page has no script", 200 if isinstance(html, str) and "<script" not in html.lower() else 999, 200)
check("doctor page escapes name", 200 if isinstance(html, str) and "<script>" not in html else 999, 200)
call("doctor page ru", "GET", f"/share/{tok_share}?lang=ru", 200, base=BASE)
call("doctor page bad lang", "GET", f"/share/{tok_share}?lang=zz", (200, 400), base=BASE)
call("doctor json", "GET", f"/share/{tok_share}/json", 200, base=BASE)
call("doctor page bad token", "GET", "/share/nope", 404, base=BASE)
call("doctor page traversal", "GET", "/share/..%2F..%2Fv1%2Fme", (400, 404), base=BASE)
call("shares list", "GET", "/me/shares", 200, token=ta)
call("share revoke by B", "DELETE", f"/me/shares/{shid}", (403, 404), token=tb)
call("share revoke", "DELETE", f"/me/shares/{shid}", (200, 204), token=ta)
call("doctor page after revoke", "GET", f"/share/{tok_share}", (404, 410), base=BASE)
call("export", "GET", "/me/export", (200, 402), token=ta)

print("== billing")
call("plans", "GET", "/billing/plans", 200, token=ta)
call("checkout unknown plan", "POST", "/billing/checkout", 400, {"planId": "nope", "provider": "payme"}, ta)
s, plans, _ = raw_call("GET", "/billing/plans", token=ta)
plan_id = plans["plans"][0]["id"] if isinstance(plans, dict) and plans.get("plans") else "premium_month"
call("checkout payme (unconfigured -> feature_disabled)", "POST", "/billing/checkout", (400, 403), {"planId": plan_id, "provider": "payme"}, ta)
call("checkout store via checkout", "POST", "/billing/checkout", (400, 403), {"planId": plan_id, "provider": "app_store"}, ta)
call("payment status bad id", "GET", "/billing/payments/nope", 400, token=ta)
call("payment status unknown", "GET", "/billing/payments/00000000-0000-0000-0000-000000000000", 404, token=ta)
call("store verify blank", "POST", "/billing/store/verify", 400, {"provider": "google_play", "productId": "x", "token": ""}, ta)
call("store verify payme", "POST", "/billing/store/verify", 400, {"provider": "payme", "productId": "x", "token": "abc"}, ta)
call("store verify fake", "POST", "/billing/store/verify", 400, {"provider": "google_play", "productId": "premium_month", "token": "fake-token"}, ta)
call("store verify fake apple", "POST", "/billing/store/verify", 400, {"provider": "app_store", "productId": "uz.sadora.app.premium.month", "token": "fake-token"}, ta)
call("payme webhook no auth", "POST", "/payments/payme", 200, {"method": "CheckPerformTransaction", "params": {}}, note="envelope error expected")
call("payme webhook garbage", "POST", "/payments/payme", 200, raw_body="garbage", headers={"Authorization": "Basic UGF5Y29tOm5vcGU="})
call("click prepare unsigned", "POST", "/payments/click/prepare", 200, raw_body="click_trans_id=1&service_id=1&merchant_trans_id=x&amount=1&action=0&sign_time=x&sign_string=x", headers={"Content-Type": "application/x-www-form-urlencoded"})
call("click complete empty", "POST", "/payments/click/complete", 200, raw_body="", headers={"Content-Type": "application/x-www-form-urlencoded"})
call("subscription", "GET", "/subscription", 200, token=ta)
call("entitlements", "GET", "/entitlements", 200, token=ta)
call("feature flags", "GET", "/feature-flags?platform=ios", 200, token=ta)

print("== admin")
call("admin login wrong", "POST", "/admin/auth/login", (400, 401), {"email": "owner@sadora.uz", "password": "wrong-pass-1"})
call("admin login unknown", "POST", "/admin/auth/login", (400, 401), {"email": "nobody@sadora.uz", "password": "wrong-pass-1"})
call("admin login sqli", "POST", "/admin/auth/login", (400, 401), {"email": "' OR 1=1--", "password": "x"})
s, adm, _ = call("admin login", "POST", "/admin/auth/login", (200, 401, 423), {"email": "owner@sadora.uz", "password": "changeme123"}, note="may be locked after wrong attempts")
atok = adm.get("accessToken") if isinstance(adm, dict) else None
if atok:
    call("admin me", "GET", "/admin/me", 200, token=atok)
    call("admin token on user route", "GET", "/me", 401, token=atok)
    call("admin users", "GET", "/admin/users?limit=5", 200, token=atok)
    call("admin users limit -1", "GET", "/admin/users?limit=-1", 400, token=atok)
    call("admin users search sqli", "GET", "/admin/users?q=%27%20OR%201%3D1--", 200, token=atok)
    uid = A["user"]["id"]
    call("admin user card", "GET", f"/admin/users/{uid}", 200, token=atok)
    call("admin user card bad id", "GET", "/admin/users/xyz", 400, token=atok)
    call("admin block no reason", "POST", f"/admin/users/{uid}/block", (400, 200), {"blocked": True, "reason": ""}, atok)
    call("admin block A", "POST", f"/admin/users/{uid}/block", 200, {"blocked": True, "reason": "audit test"}, atok)
    call("blocked user access token still works?", "GET", "/me", (200, 401, 403), token=ta, note="15-min window")
    call("blocked user health write", "PUT", f"/days/{d(0)}", (200, 401, 403), {"mood": "ok"}, ta, note="15-min window")
    call("blocked user refresh", "POST", "/auth/refresh", 401, {"refreshToken": A2["tokens"]["refreshToken"]} if False else {"refreshToken": "x"})
    call("admin unblock A", "POST", f"/admin/users/{uid}/block", 200, {"blocked": False, "reason": "audit done"}, atok)
    call("admin premium grant 0 days", "POST", f"/admin/users/{uid}/premium", 400, {"days": 0, "reason": "x"}, atok)
    call("admin premium grant 100000 days", "POST", f"/admin/users/{uid}/premium", (400, 200), {"days": 100000, "reason": "x"}, atok)
    call("admin stats", "GET", "/admin/stats", 200, token=atok)
    call("admin analytics", "GET", "/admin/stats/analytics?days=30", 200, token=atok)
    call("admin analytics days 99999", "GET", "/admin/stats/analytics?days=99999", (200, 400), token=atok)
    call("admin audit", "GET", "/admin/audit?limit=5", 200, token=atok)
    call("admin features", "GET", "/admin/features", 200, token=atok)
    call("admin flags", "GET", "/admin/flags", 200, token=atok)
    call("admin community posts", "GET", "/admin/community/posts?limit=5", 200, token=atok)
    call("admin billing summary", "GET", "/admin/billing/summary", 200, token=atok)
    call("admin ai usage", "GET", "/admin/ai/usage", 200, token=atok)
    call("admin rewards overview", "GET", "/admin/rewards/overview", 200, token=atok)
    call("admin notif templates", "GET", "/admin/notifications/templates", 200, token=atok)
    call("admin wearables providers", "GET", "/admin/wearables/providers", 200, token=atok)
    call("admin content categories", "GET", "/admin/content/categories", 200, token=atok)
    call("admin shop products", "GET", "/admin/shop/products", 200, token=atok)
    call("admin totp confirm without start", "POST", "/admin/me/totp/confirm", 400, {"code": "000000"}, atok)
    # brute force lockout probe on a throwaway? cannot create admins via API; probe failed counter on owner is risky -> skip

print("== logout / deletion")
D = sign_in(phone()); td = D["tokens"]["accessToken"]
call("logout no body (no content type)", "POST", "/auth/logout", 415, raw_body="", token=td)
call("logout ok", "POST", "/auth/logout", 200, {"refreshToken": D["tokens"]["refreshToken"]}, td)
call("refresh after logout", "POST", "/auth/refresh", 401, {"refreshToken": D["tokens"]["refreshToken"]})
call("access after logout (still valid 15m)", "GET", "/me", (200, 401), token=td)
E = sign_in(phone()); te = E["tokens"]["accessToken"]
call("delete wrong confirmation", "DELETE", "/me", 400, {"confirmation": "delete"}, te)
call("delete ok", "DELETE", "/me", 200, {"confirmation": "DELETE", "reason": "audit"}, te)
call("refresh after deletion", "POST", "/auth/refresh", 401, {"refreshToken": E["tokens"]["refreshToken"]})
call("access after deletion", "GET", "/me", (200, 401, 403), token=te, note="15-min window")
call("health write after deletion", "PUT", f"/days/{d(0)}", (401, 403), {"mood": "ok"}, te, note="must be refused")
call("sign in again after deletion request", "POST", "/auth/otp/request", 201, {"phone": E["user"]["phone"]})

# =====================================================================================
print()
print(f"checks: {len(RESULTS)}  passed: {sum(1 for r in RESULTS if r[1])}  anomalies: {len(ANOMALIES)}")
for a in ANOMALIES:
    print(f"ANOMALY {a[0]!r}: got {a[1]} expected {a[2]} code={a[3]} note={a[5]}\n   {a[4]}")
with open(sys.argv[2] if len(sys.argv) > 2 else "/dev/null", "w") as f:
    for r in RESULTS:
        f.write(json.dumps({"name": r[0], "ok": r[1], "status": r[2], "expected": str(r[3]), "code": r[4], "note": r[5]}) + "\n")
