#!/usr/bin/env python3
"""Russian and English copies of the landing page.

landing/index.html (Uzbek) is the only page anyone edits. This tool cuts it into
translatable pieces and writes landing/index.ru.html and landing/index.en.html from the
dictionaries in tools/landing_i18n/{ru,en}.json, which map each Uzbek piece to its
translation.

  tools/landing_i18n.py extract   # list the pieces; say which ones a dictionary lacks
  tools/landing_i18n.py build     # write the two pages; refuses while a piece is missing

A piece is one of:
  * an HTML text run outside <script>/<style> (whitespace around it is kept as is),
  * a human-facing attribute (aria-label, title, alt, placeholder, meta content),
  * a JavaScript string literal that reads as text (see js_is_text).

After editing index.html: run extract, translate the pieces it lists as missing into both
JSON files, then run build. Keys no longer in the page are reported as stale.
"""
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "landing" / "index.html"
DICT_DIR = ROOT / "tools" / "landing_i18n"
LANGS = ("ru", "en")
SITE = "https://sadora.app/"
PAGE = {"uz": "index.html", "ru": "index.ru.html", "en": "index.en.html"}

TEXT_ATTRS = {"aria-label", "title", "alt", "placeholder"}
LETTER = re.compile(r"[A-Za-zА-Яа-яЁё]")


# ---------------------------------------------------------------- tokenising

def html_segments(src):
    """Yield (kind, text) where kind is 'tag', 'text', 'raw' (script/style body) or 'comment'."""
    i, n = 0, len(src)
    raw_until = None
    while i < n:
        if raw_until:
            j = src.lower().find(raw_until, i)
            yield ("script" if raw_until == "</script" else "raw"), src[i:j]
            i, raw_until = j, None
            continue
        if src.startswith("<!--", i):
            j = src.index("-->", i) + 3
            yield "comment", src[i:j]
            i = j
            continue
        if src[i] == "<":
            j = i + 1
            quote = None
            while j < n:
                c = src[j]
                if quote:
                    if c == quote:
                        quote = None
                elif c in "\"'" and re.match(r"[\s\S]*=\s*$", src[i:j]):
                    quote = c
                elif c == ">":
                    break
                j += 1
            tag = src[i:j + 1]
            yield "tag", tag
            name = re.match(r"<\s*([a-zA-Z0-9]+)", tag)
            if name and name.group(1).lower() in ("script", "style"):
                raw_until = "</" + name.group(1).lower()
            i = j + 1
            continue
        j = src.find("<", i)
        j = n if j < 0 else j
        yield "text", src[i:j]
        i = j


def js_tokens(js):
    """Yield (kind, text): 'str' tokens are whole literals with their quotes; 'code' is the rest."""
    i, n, start = 0, len(js), 0
    prev_sig = ""
    while i < n:
        c = js[i]
        if js.startswith("//", i):
            i = js.find("\n", i)
            i = n if i < 0 else i
            continue
        if js.startswith("/*", i):
            i = js.index("*/", i) + 2
            continue
        if c in "'\"`":
            if start < i:
                yield "code", js[start:i]
            j = i + 1
            while js[j] != c:
                j += 2 if js[j] == "\\" else 1
            yield "str", js[i:j + 1]
            i = start = j + 1
            prev_sig = "s"
            continue
        if c == "/" and (prev_sig == "" or prev_sig in "(,=:[!&|?{};+-*%<>~^"):
            # regular expression literal
            j = i + 1
            in_class = False
            while True:
                d = js[j]
                if d == "\\":
                    j += 2
                    continue
                if d == "[":
                    in_class = True
                elif d == "]":
                    in_class = False
                elif d == "/" and not in_class:
                    break
                j += 1
            i = j + 1
            while i < n and js[i].isalpha():
                i += 1
            prev_sig = "r"
            continue
        if not c.isspace():
            prev_sig = c
        i += 1
    if start < n:
        yield "code", js[start:]


def js_unescape(body, q):
    return re.sub(r"\\(.)", lambda m: m.group(1) if m.group(1) in "'\"\\`" else "\\" + m.group(1), body)


def js_escape(text, q):
    return text.replace("\\", "\\\\").replace(q, "\\" + q)


def js_is_text(s):
    """A JS literal is text a reader sees, not a selector, class list, CSS value or HTML scaffold."""
    if not LETTER.search(s) or "<" in s or ">" in s:
        return False
    if re.search(r"[{}#;=]|^[.(\[]|\bpx\b|--|deg\)|translate|cubic|ease|linear\b", s):
        return False
    if re.fullmatch(r"[a-z0-9_-]+", s):          # ids, class names, event names, keys
        return False
    if re.fullmatch(r"[a-z0-9 _.-]+", s) and not re.search(r"[a-z]{3,} [a-z]{3,}", s):
        return False                              # 'viz vc ', 'on off'
    if re.search(r"(^|\s)\.[a-z]", s):           # CSS selectors
        return False
    if re.fullmatch(r"[A-Z]{1,3}", s):            # 'BR'
        return False
    if re.fullmatch(r"[a-z]+[A-Z][A-Za-z0-9]*|[A-Z][a-z]+[A-Z][A-Za-z]*", s):
        return False                              # element ids, 'IntersectionObserver'
    if re.search(r"\($|^:|:$|^-?\d|\.json$|/\w*_$", s) or s == "use strict":
        return False                              # 'scaleX(', 'left:', '-40% 0px', 'img/ic_'
    return True
    return True


# ---------------------------------------------------------------- pieces

def pieces(src):
    """Return ordered list of (kind, key) for every translatable piece in the page."""
    out = []
    for kind, seg in html_segments(src):
        if kind == "text":
            key = seg.strip()
            if key and LETTER.search(html.unescape(key)):
                out.append(("text", key))
        elif kind == "tag":
            for attr, val in tag_attrs(seg):
                out.append(("attr", val))
        elif kind == "script":
            for tk, tok in js_tokens(seg):
                if tk == "str":
                    s = js_unescape(tok[1:-1], tok[0])
                    if js_is_text(s):
                        out.append(("js", s))
    return out


def tag_attrs(tag):
    is_meta_text = re.match(r"<meta\b", tag) and re.search(r'(name="description"|property="og:(title|description)")', tag)
    for m in re.finditer(r'\s([a-zA-Z-]+)="([^"]*)"', tag):
        attr, val = m.group(1), m.group(2)
        if (attr in TEXT_ATTRS or (attr == "content" and is_meta_text)) and LETTER.search(val):
            yield attr, val


# ---------------------------------------------------------------- building

def load(lang):
    p = DICT_DIR / f"{lang}.json"
    return json.loads(p.read_text()) if p.exists() else {}


def translate(src, lang, d):
    missing = []

    def tr(key):
        if key not in d:
            missing.append(key)
            return key
        return d[key]

    out = []
    for kind, seg in html_segments(src):
        if kind == "text":
            key = seg.strip()
            if key and LETTER.search(html.unescape(key)):
                lead = seg[:len(seg) - len(seg.lstrip())]
                trail = seg[len(seg.rstrip()):]
                seg = lead + tr(key) + trail
        elif kind == "tag":
            def sub(m):
                attr, val = m.group(1), m.group(2)
                if val in dict(tag_attrs(seg)).values() and attr in TEXT_ATTRS | {"content"}:
                    v = html.escape(html.unescape(tr(val)), quote=False).replace('"', "&quot;")
                    return f' {attr}="{v}"'
                return m.group(0)
            seg = re.sub(r'\s([a-zA-Z-]+)="([^"]*)"', sub, seg)
        elif kind == "script":
            parts = []
            for tk, tok in js_tokens(seg):
                if tk == "str":
                    q = tok[0]
                    s = js_unescape(tok[1:-1], q)
                    if js_is_text(s):
                        tok = q + js_escape(tr(s), q) + q
                parts.append(tok)
            seg = "".join(parts)
        out.append(seg)
    return "".join(out), missing


def localise_head(page, lang):
    page = page.replace('<html lang="uz">', f'<html lang="{lang}">', 1)
    # Legal pages already exist per language.
    page = page.replace('href="privacy.html"', f'href="privacy.{lang}.html"')
    page = page.replace('href="terms.html"', f'href="terms.{lang}.html"')
    # Language switcher: mark this page as current.
    page = re.sub(r'(<a href="index\.html" hreflang="uz"[^>]*?) aria-current="page"', r"\1", page)
    page = page.replace(f'<a href="{PAGE[lang]}" hreflang="{lang}"', f'<a href="{PAGE[lang]}" hreflang="{lang}" aria-current="page"')
    page = page.replace(f'<link rel="canonical" href="{SITE}">', f'<link rel="canonical" href="{SITE}{PAGE[lang]}">')
    page = page.replace('<meta property="og:locale" content="uz_UZ">',
                        '<meta property="og:locale" content="%s">' % {"ru": "ru_RU", "en": "en_US"}[lang])
    if lang == "ru":
        # Bricolage Grotesque and Figtree have no Cyrillic; Onest fills those glyphs in.
        page = page.replace("&family=Figtree:", "&family=Onest:wght@400;500;600;700&family=Figtree:", 1)
        page = page.replace("--sans: 'Figtree',", "--sans: 'Figtree', 'Onest',", 1)
        page = page.replace("--disp: 'Bricolage Grotesque', 'Figtree',", "--disp: 'Bricolage Grotesque', 'Onest', 'Figtree',", 1)
    # The browser-language redirect lives only on the Uzbek page.
    page = re.sub(r"<script id=\"langPick\">.*?</script>\n", "", page, flags=re.S)
    return page


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "extract"
    src = SRC.read_text()
    keys = list(dict.fromkeys(k for _, k in pieces(src)))
    if cmd == "extract":
        DICT_DIR.mkdir(exist_ok=True)
        (DICT_DIR / "source.json").write_text(json.dumps(keys, ensure_ascii=False, indent=1) + "\n")
        print(f"{len(keys)} pieces -> {DICT_DIR / 'source.json'}")
        for lang in LANGS:
            d = load(lang)
            miss = [k for k in keys if k not in d]
            stale = [k for k in d if k not in set(keys)]
            print(f"{lang}: {len(miss)} missing, {len(stale)} stale")
            for k in miss[:20]:
                print("   missing:", k[:90])
            for k in stale[:20]:
                print("   stale:  ", k[:90])
        return
    if cmd == "build":
        failed = False
        for lang in LANGS:
            page, missing = translate(src, lang, load(lang))
            if missing:
                failed = True
                print(f"{lang}: {len(set(missing))} pieces not translated, page not written")
                for k in list(dict.fromkeys(missing))[:20]:
                    print("   ", k[:90])
                continue
            out = ROOT / "landing" / PAGE[lang]
            out.write_text(localise_head(page, lang))
            print("wrote", out.relative_to(ROOT))
        sys.exit(1 if failed else 0)
    sys.exit(__doc__)


if __name__ == "__main__":
    main()
