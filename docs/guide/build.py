"""Builds the Sati install guide as HTML, ready to print to PDF.

It reads guide.html, which holds the words, and fills its placeholders.
  {{fig:NAME}}  a screenshot crop with a ring on the control to tap (FIGS below)
  {{icon:NAME}} a Lucide icon, read from the Sati app's own vector drawables
  {{url:NAME}}  a link address (URLS below)
It writes build/guide.html and the image crops in build/img/.

Screens are real emulator screenshots at 1440x3200 (Android 16, Obtainium 1.7.4).
Sati's own screens are the store screenshots in fastlane/.
Each screen has a uiautomator dump beside it, so a ring is placed on the real
bounds of the control, found by its label.

Usage: python3 -I build.py
"""
import html
import re
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
SCREENS = ROOT / "screens"
OUT = ROOT / "build"
REPO = ROOT.parents[1]
DRAWABLES = REPO / "app/src/main/res/drawable"
# Sati's own screens are the store screenshots, so the guide and the store never differ.
STORE = REPO / "fastlane/metadata/android/en-US/images/phoneScreenshots"

URLS = {
    "obtainium_apk": "https://github.com/ImranR98/Obtainium/releases/latest/download/app-arm64-v8a-release.apk",
    "obtainium_apk_all": "https://github.com/ImranR98/Obtainium/releases/latest/download/app-release.apk",
    "add_sati": "https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/5h1vmani/sati",
    "sati_repo": "https://github.com/5h1vmani/sati",
    "issues": "https://github.com/5h1vmani/sati/issues/new/choose",
    "obtainium_repo": "https://github.com/ImranR98/Obtainium",
}

# name: (screen, crop box in px or None for the whole screen, label to ring, xml to read, width in mm)
# A dialog crop is its panel's bounds from the dump, plus a margin.
DIALOG = (53, 1313, 1387, 1973)
FIGS = {
    "downloaded": ("01-downloaded", (0, 150, 1440, 660), "Open", None, 84),
    "blocked": ("02-blocked", (53, 1305, 1387, 1981), "Settings", None, 84),
    "allow_chrome": ("03-allow-off", (0, 760, 1440, 1480), "Allow from this source", None, 84),
    "install_obtainium": ("06-install-obtainium", DIALOG, "Install", None, 84),
    "installed_obtainium": ("07-installed", DIALOG, "Open", None, 84),
    "notifications": ("08-obtainium-first", (0, 1050, 1440, 2236), "Allow", None, 84),
    "add_app": ("12-add-app", None, "Add", None, 44),
    "sati_page": ("13-after-add", None, "Install", None, 44),
    "install_sati": ("15-install-sati", DIALOG, "Install", None, 84),
    "apps_list": ("16-after-install", None, "Open Sati", None, 44),
    "update_list": ("17-update-available", (0, 580, 1440, 1180), "Update", None, 84),
    "update_prompt": ("18-update-prompt", DIALOG, "Update", None, 84),
    # Sati's own store screenshots, shown whole with no ring.
    "sati_bell": ("store/1", None, None, None, 25),
    "sati_sadhana": ("store/7", None, None, None, 25),
    "no_data": ("sati/appinfo", (0, 2300, 1440, 2540), "No data used", None, 76),
    "sati_log": ("store/9", None, None, None, 25),
}

# The ring sits this many px outside the control, so it does not cover the words.
RING_PAD = 18


def bounds(xml_name, label):
    """The bounds of the last element whose text or description is the label."""
    text = (SCREENS / f"{xml_name}.xml").read_text()
    for node in reversed(re.findall(r"<node [^>]*>", text)):
        found = re.search(r' text="([^"]*)"', node)
        desc = re.search(r'content-desc="([^"]*)"', node)
        names = {html.unescape(found[1]) if found else "", html.unescape(desc[1]) if desc else ""}
        if label in names:
            return tuple(map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', node)[1])))
    raise SystemExit(f"build: no element '{label}' in {xml_name}.xml")


def figure(name):
    screen, crop, label, xml, width = FIGS[name]
    path = STORE / f"{screen[6:]}.png" if screen.startswith("store/") else SCREENS / f"{screen}.png"
    image = Image.open(path).convert("RGB")
    crop = crop or (0, 0, image.width, image.height)
    part = image.crop(crop)
    (OUT / "img").mkdir(parents=True, exist_ok=True)
    part.save(OUT / "img" / f"{name}.jpg", quality=92, subsampling=0)
    whole = " whole" if crop == (0, 0, image.width, image.height) else ""
    if label is None:
        return (f'<figure class="shot{whole}" style="width:{width}mm">'
                f'<img src="img/{name}.jpg" alt=""></figure>')
    x1, y1, x2, y2 = bounds(xml or screen, label)
    w, h = crop[2] - crop[0], crop[3] - crop[1]
    left = max(0, x1 - RING_PAD - crop[0]) / w * 100
    top = max(0, y1 - RING_PAD - crop[1]) / h * 100
    right = min(w, x2 + RING_PAD - crop[0]) / w * 100
    bottom = min(h, y2 + RING_PAD - crop[1]) / h * 100
    word = label.split("\n")[0]
    return (
        f'<figure class="shot{whole}" style="width:{width}mm">'
        f'<img src="img/{name}.jpg" alt="The screen, with {html.escape(word)} marked">'
        f'<span class="ring" style="left:{left:.2f}%;top:{top:.2f}%;'
        f'width:{right - left:.2f}%;height:{bottom - top:.2f}%"></span></figure>'
    )


def icon(name):
    """An inline SVG made from the Lucide drawable that the Sati app ships."""
    xml = (DRAWABLES / f"lucide_{name}.xml").read_text()
    paths = "".join(f'<path d="{d}"/>' for d in re.findall(r'android:pathData="([^"]*)"', xml))
    return (
        '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{paths}</svg>'
    )


def main():
    source = (ROOT / "guide.html").read_text()
    source = re.sub(r"\{\{fig:(\w+)\}\}", lambda m: figure(m[1]), source)
    source = re.sub(r"\{\{icon:(\w+)\}\}", lambda m: icon(m[1]), source)
    source = re.sub(r"\{\{url:(\w+)\}\}", lambda m: html.escape(URLS[m[1]]), source)
    pages = source.count("{{page}}")
    for n in range(1, pages + 1):
        source = source.replace("{{page}}", str(n), 1)
    source = source.replace("{{pages}}", str(pages))
    left = re.findall(r"\{\{[^}]*\}\}", source)
    if left:
        raise SystemExit(f"build: unknown placeholders {left}")
    (OUT / "guide.html").write_text(source)
    print("build: wrote build/guide.html and", len(list((OUT / "img").glob("*.jpg"))), "crops")


if __name__ == "__main__":
    main()
