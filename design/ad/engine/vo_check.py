#!/usr/bin/env python3
"""
Listens to a voice-over take with Gemini and reports back as a native-speaker reviewer:
a verbatim transcript, mispronounced words, and a 1–10 nativeness score.

    python3 design/ad/engine/vo_check.py build/ad/vo/uz_Sulafat_1.wav uz
"""
import base64, json, subprocess, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vo_gemini import SCRIPT, key

ASK = {
    "uz": ("You are a native Uzbek speaker from Tashkent and a voice-casting director. "
           "Listen to this voice-over take. Reply as JSON only, with keys: "
           "transcript (verbatim, Uzbek Latin), missing_or_extra_words (list), "
           "mispronounced (list of {word, how_it_sounded, why}), accent (one sentence: does it "
           "sound like a native Uzbek woman, or like Turkish/Kazakh/Russian/foreign?), "
           "nativeness_1_to_10, warmth_1_to_10, announcer_like_1_to_10, "
           "verdict (one sentence). The intended script is:\n"),
    "en": ("You are a voice-casting director. Listen to this voice-over take. Reply as JSON only "
           "with keys: transcript, missing_or_extra_words, mispronounced (list), accent, "
           "naturalness_1_to_10, warmth_1_to_10, announcer_like_1_to_10, verdict. The intended "
           "script is:\n"),
}


def check(path, lang, model="gemini-3.8-flash"):
    audio = base64.b64encode(open(path, "rb").read()).decode()
    body = {"contents": [{"parts": [
        {"inlineData": {"mimeType": "audio/wav", "data": audio}},
        {"text": ASK[lang] + SCRIPT[lang]}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.2}}
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    r = subprocess.run(["curl", "-s", "-X", "POST", url, "-H", f"x-goog-api-key: {key()}",
                        "-H", "Content-Type: application/json", "--data-binary", "@-"],
                       input=json.dumps(body).encode(), capture_output=True, timeout=300)
    data = json.loads(r.stdout)
    if "error" in data:
        sys.exit(f"{data['error'].get('code')} {data['error'].get('message', '')[:300]}")
    text = data["candidates"][0]["content"]["parts"][0]["text"]
    return json.loads(text)


if __name__ == "__main__":
    out = check(sys.argv[1], sys.argv[2])
    print(json.dumps(out, ensure_ascii=False, indent=1))
