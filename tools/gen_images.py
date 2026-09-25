#!/usr/bin/env python3
"""
Generates the SADORA illustration set with Gemini and drops it into the shared
module's Compose resources.

Usage:
    GEMINI_API_KEY=... python3 tools/gen_images.py            # all assets
    GEMINI_API_KEY=... python3 tools/gen_images.py lotus       # one asset

The key is also read from `.env` (GEMINI_API_KEY=...). Image generation is not on
the Gemini free tier — the API answers 429 with "limit: 0" until billing is enabled
for the project at https://aistudio.google.com/. Until then the app draws these
pictures itself (see `ui/components/Illustrations.kt`); once the PNGs exist under
`sadora-client/shared/src/commonMain/composeResources/drawable/`, reference them with
`painterResource(Res.drawable.<name>)` in place of the drawn versions.
"""

import base64
import json
import os
import sys
import urllib.error
import urllib.request

MODEL = os.environ.get("GEMINI_IMAGE_MODEL", "gemini-2.5-flash-image")
ENDPOINT = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "sadora-client", "shared", "src", "commonMain", "composeResources", "drawable")

STYLE = (
    "Soft pastel digital illustration for a women's wellness app. Lavender (#F7F5FF) "
    "and blush pink palette with a purple (#7B61FF) to pink (#FF6FB8) accent, airy, "
    "high-key lighting, clean edges, no text, no watermark, no people's faces."
)

ASSETS = {
    # Cycle screen, the "Bugun" card.
    "lotus": {
        "prompt": "A single pink lotus flower in full bloom, translucent petals, floating, "
                  "isolated on pure white background, gentle glow.",
        "aspect": "1:1",
    },
    # Welcome (onboarding 02) illustration.
    "welcome_portrait": {
        "prompt": "Stylised profile silhouette of a woman with long flowing hair made of "
                  "lavender and pink gradients, leaves and small sparkles around her, "
                  "circular composition, pure white background.",
        "aspect": "1:1",
    },
    # Splash background flowers (bottom-left corner).
    "splash_blooms": {
        "prompt": "Delicate translucent pink and lavender flower petals clustered in the "
                  "bottom-left corner fading into a soft lavender gradient, dreamy, "
                  "portrait orientation, lots of empty space at the top.",
        "aspect": "9:16",
    },
    # Meal thumbnails on the nutrition screen.
    "meal_breakfast": {
        "prompt": "Overhead photo-style illustration of a breakfast bowl: yoghurt, granola "
                  "and berries in a white bowl on a light table.",
        "aspect": "1:1",
    },
    "meal_lunch": {
        "prompt": "Overhead photo-style illustration of a lunch plate: grilled chicken "
                  "salad with flatbread on a light table.",
        "aspect": "1:1",
    },
    "meal_dinner": {
        "prompt": "Overhead photo-style illustration of a dinner plate: roasted vegetables "
                  "and fish on a light ceramic plate.",
        "aspect": "1:1",
    },
    "meal_snack": {
        "prompt": "Overhead photo-style illustration of a snack: two small jars of fruit "
                  "yoghurt with a spoon on a light table.",
        "aspect": "1:1",
    },
    # Food scan sample result.
    "scan_salmon_bowl": {
        "prompt": "Overhead photo-style illustration of a salmon quinoa bowl with avocado "
                  "slices, cherry tomatoes, broccoli and a lemon wedge in a ceramic bowl "
                  "on a grey stone table.",
        "aspect": "4:3",
    },
}


def api_key() -> str:
    key = os.environ.get("GEMINI_API_KEY")
    if key:
        return key
    env = os.path.join(ROOT, ".env")
    if os.path.exists(env):
        for line in open(env):
            line = line.strip()
            if line.startswith("GEMINI_API_KEY=") and line.split("=", 1)[1].startswith("AIza"):
                return line.split("=", 1)[1]
    sys.exit("GEMINI_API_KEY is not set (env or .env)")


def generate(name: str, spec: dict, key: str) -> None:
    body = {
        "contents": [{"parts": [{"text": f"{spec['prompt']} {STYLE}"}]}],
        "generationConfig": {
            "responseModalities": ["IMAGE"],
            "imageConfig": {"aspectRatio": spec["aspect"]},
        },
    }
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body).encode(),
        headers={"x-goog-api-key": key, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:400]
        sys.exit(f"{name}: HTTP {error.code} — {detail}")

    for part in payload["candidates"][0]["content"]["parts"]:
        data = part.get("inlineData")
        if not data:
            continue
        os.makedirs(OUT_DIR, exist_ok=True)
        path = os.path.join(OUT_DIR, f"{name}.png")
        with open(path, "wb") as out:
            out.write(base64.b64decode(data["data"]))
        print(f"{name}: wrote {path}")
        return
    sys.exit(f"{name}: no image in the response")


def main() -> None:
    wanted = sys.argv[1:] or list(ASSETS)
    unknown = [w for w in wanted if w not in ASSETS]
    if unknown:
        sys.exit(f"unknown asset(s): {', '.join(unknown)} — choose from {', '.join(ASSETS)}")
    key = api_key()
    for name in wanted:
        generate(name, ASSETS[name], key)


if __name__ == "__main__":
    main()
