#!/usr/bin/env python3
"""
Voice-over takes from Gemini TTS, for "The Path".

    python3 design/ad/engine/vo_gemini.py uz Sulafat [model]   -> build/ad/vo/uz_Sulafat_<n>.wav

The key is read from the repo's .env (GEMINI_API_KEY). Requests go through curl because
the framework Python on this Mac has no CA bundle. The script is read as ONE flowing
take; the picture is locked to it afterwards, cut only at its own pauses.
"""
import base64, json, os, re, subprocess, sys, wave

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUT = os.path.join(ROOT, "build", "ad", "vo")

SCRIPT = {
    # Uzbek Latin with the proper oʻ / gʻ (U+02BB) and tutuq belgisi (U+02BC).
    "uz": ("Tanangiz jadval emas — u bosqichlardan oʻtadi. "
           "Har kuni bir kichik qadam: suv, dori, kayfiyat. "
           "Savol tugʻilsa — Sadora tushuntiradi. "
           "Jiddiy boʻlsa — shifokor javob beradi. "
           "Yaqiningiz esa taxmin qilmaydi… biladi. "
           "Sikl, homiladorlik, onalik, menopauza. "
           "Har bir ayol. Har bir lahza. "
           "Sadora."),
    "en": ("Your body isn't a calendar — it moves through stages. "
           "One small step a day: water, meds, mood. "
           "Got a question? Sadora explains. "
           "Something serious? A doctor answers. "
           "And the people who love you don't guess… they know. "
           "Cycle, pregnancy, motherhood, menopause. "
           "Every woman. Every moment. "
           "Sadora."),
}

DIRECTION = {
    "uz": ("Read this in native, natural Uzbek (Tashkent pronunciation), as a warm, calm, "
           "confident young woman — a friend who happens to know medicine, never a commercial "
           "announcer. Unhurried, intimate, smiling slightly. Give each comma a small breath, "
           "hold the pause at the ellipsis a beat longer, and say the last word \"Sadora\" "
           "softly with the stress on the second syllable (sa-DO-ra):"),
    # Round two: the first takes scored as announcer-like and ran 25–30 s for a 22 s slot.
    "uz2": ("Say this in native Tashkent Uzbek as a young woman talking to ONE close friend across a "
            "kitchen table — conversational, warm, quietly confident, at a natural everyday pace "
            "(not slow, not dramatic, absolutely not an advertisement voice). Short natural "
            "breaths at the commas, one longer beat at the ellipsis, and the final \"Sadora\" "
            "said softly, almost smiling, stressed sa-DO-ra:"),
    "en2": ("A young woman talking to ONE close friend across a kitchen table — conversational, "
            "warm, quietly confident, at a natural everyday pace (not slow, not dramatic, "
            "absolutely not an advertisement voice). Short breaths at the commas, one longer beat "
            "at the ellipsis, and the final \"Sadora\" said softly, almost smiling (sa-DOR-a):"),
    # Round three: same voice, a quicker everyday pace so the read fits the 22-second slot.
    "uz3": ("Read this in native, natural Uzbek (Tashkent pronunciation), as a warm, calm, "
            "confident young woman — a friend who happens to know medicine, never a commercial "
            "announcer. Speak at a brisk, natural, conversational pace, the way people really "
            "talk — the whole read should take about 20 seconds. Keep the pauses short, a "
            "slightly longer beat only at the ellipsis, and say the last word \"Sadora\" "
            "softly with the stress on the second syllable (sa-DO-ra):"),
    "en3": ("Read this as a warm, calm, confident young woman — a friend who happens to know "
            "medicine, never a commercial announcer. Speak at a brisk, natural, conversational "
            "pace, the way people really talk — the whole read should take about 20 seconds. "
            "Keep the pauses short, a slightly longer beat only at the ellipsis, and say the "
            "last word \"Sadora\" softly (sa-DOR-a):"),
    "en": ("Read this as a warm, calm, confident young woman — a friend who happens to know "
           "medicine, never a commercial announcer. Unhurried, intimate, smiling slightly. "
           "Give each comma a small breath, hold the pause at the ellipsis a beat longer, and "
           "say the last word \"Sadora\" softly (sa-DOR-a):"),
}


def key():
    for line in open(os.path.join(ROOT, ".env")):
        m = re.match(r"\s*GEMINI_API_KEY\s*=\s*(.+)", line)
        if m:
            return m.group(1).strip().strip("\"'")
    sys.exit("GEMINI_API_KEY missing from .env")


def take(lang, voice, model="gemini-3.8-flash-tts"):
    body = {
        "contents": [{"parts": [{"text": DIRECTION[lang] + "\n\n" + SCRIPT[lang.rstrip("23")]}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}},
        },
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    r = subprocess.run(
        ["curl", "-s", "-X", "POST", url, "-H", f"x-goog-api-key: {key()}",
         "-H", "Content-Type: application/json", "--data-binary", "@-"],
        input=json.dumps(body).encode(), capture_output=True, timeout=300)
    data = json.loads(r.stdout)
    if "error" in data:
        sys.exit(f"{model}/{voice}: {data['error'].get('code')} {data['error'].get('message', '')[:300]}")
    part = data["candidates"][0]["content"]["parts"][0]["inlineData"]
    raw = base64.b64decode(part["data"])
    mime = part.get("mimeType", "")
    os.makedirs(OUT, exist_ok=True)
    n = 1
    while os.path.exists(os.path.join(OUT, f"{lang}_{voice}_{n}.wav")):
        n += 1
    path = os.path.join(OUT, f"{lang}_{voice}_{n}.wav")
    rate = re.search(r"rate=(\d+)", mime)
    if "l16" in mime.lower() or "pcm" in mime.lower() or not raw[:4] in (b"RIFF", b"OggS", b"ID3\x03", b"fLaC"):
        with wave.open(path, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(int(rate.group(1)) if rate else 24000)
            w.writeframes(raw)
    else:
        # A container (wav / mp3 / ogg): let ffmpeg normalise it to 24 kHz mono PCM.
        src = path + ".src"
        open(src, "wb").write(raw)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-ac", "1", "-ar", "24000", path], check=True)
        os.remove(src)
    with wave.open(path) as w:
        print(path, mime, raw[:4], f"{w.getnframes() / w.getframerate():.2f}s")
    return path


if __name__ == "__main__":
    take(sys.argv[1], sys.argv[2], *(sys.argv[3:4] or []))
