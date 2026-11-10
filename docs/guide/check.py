"""Free checks on the Sati install guide. Run after build.py and print.mjs.

It fails on:
  1. a font in the PDF that is not embedded, or is not Roboto;
  2. a PDF with no tags or no bookmarks;
  3. a screenshot printed below 250 pixels per inch, so it would blur when zoomed;
  4. a sentence over 25 words, or an em dash;
  5. a screen word in a chip (class "ui") that no saved screen dump contains;
  6. text colours below the WCAG AA contrast of 4.5 to 1;
  7. a contents link whose page number is wrong;
  8. a link the PDF does not hold, or a download link that does not answer.

Usage: python3 -I check.py [--offline]
"""
import html
import re
import subprocess
import sys
import urllib.request
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PDF = ROOT / "Sati-install-guide.pdf"
PAGE = (ROOT / "build/guide.html").read_text()
fails = []


def fail(msg):
    fails.append(msg)
    print("FAIL", msg)


def run(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout


# 1. Fonts.
for line in run("pdffonts", str(PDF)).splitlines()[2:]:
    cols = line.split()
    name, emb = cols[0], cols[-5]
    if emb != "yes":
        fail(f"font {name} is not embedded")
    if "Roboto" not in name:
        fail(f"font {name} is not Roboto")

# 2. Tags and bookmarks, read from the raw and inflated PDF streams.
raw = PDF.read_bytes()
text = raw.decode("latin1")
for m in re.finditer(rb"stream\r?\n", raw):
    end = raw.find(b"endstream", m.end())
    try:
        text += zlib.decompress(raw[m.end():end]).decode("latin1")
    except zlib.error:
        pass
if not re.search(r"/StructTreeRoot\s+\d+\s+0\s+R", text):
    fail("the PDF has no tags")
if not re.search(r"/Outlines\s+\d+\s+0\s+R", text):
    fail("the PDF has no bookmarks")

# 3. Screenshot sharpness.
for line in run("pdfimages", "-list", str(PDF)).splitlines()[2:]:
    cols = line.split()
    xppi, yppi = int(cols[12]), int(cols[13])
    if min(xppi, yppi) < 250:
        fail(f"an image on page {cols[0]} prints at {xppi} ppi")

# 4. Sentences, from the visible text of each sheet.
body = re.sub(r"<(style|script)[^>]*>.*?</\1>", " ", PAGE, flags=re.S)
body = re.sub(r"<p class=\"addr\">.*?</p>", " ", body, flags=re.S)
words = html.unescape(re.sub(r"<[^>]+>", "\n", body))
if "—" in words:
    fail("the text has an em dash")
for block in re.split(r"\n\s*\n", words):
    for sentence in re.split(r"(?<=[.?!])\s+", " ".join(block.split())):
        n = len(sentence.split())
        if n > 25:
            fail(f"a sentence has {n} words: {sentence[:70]}...")

# 5. Screen words must be real. Every chip must match a label in a saved dump.
labels = set()
for xml in (ROOT / "screens").rglob("*.xml"):
    for m in re.finditer(r'(?: text|content-desc)="([^"]+)"', xml.read_text()):
        labels.add(html.unescape(m[1]))
# Sati's own words come from its string resources.
strings = ROOT.parents[1] / "app/src/main/res/values/strings.xml"
labels |= {html.unescape(m) for m in re.findall(r"<string name=\"[^\"]+\">([^<]+)</string>", strings.read_text())}
for word in set(re.findall(r"<b>([^<]+)</b>", PAGE)):
    if "/" not in word and html.unescape(word) not in labels:
        fail(f"the app word '{word}' is in no screen dump and no Sati string")
for chip in set(re.findall(r'<span class="ui">([^<]+)</span>', PAGE)):
    if html.unescape(chip) not in labels:
        fail(f"the screen word '{chip}' is in no screen dump")


# 6. Contrast of the text colour pairs the page uses.
def lum(hexcolor):
    rgb = [int(hexcolor[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


tokens = dict(re.findall(r"--([\w-]+):\s*(#[0-9A-Fa-f]{6})", PAGE))
pairs = [("text", "bg"), ("text-soft", "bg"), ("text-soft", "container"), ("brand", "bg"),
         ("on-brand", "brand"), ("on-tint", "tint"), ("text", "container"), ("link", "container"), ("link", "bg")]
for fg, bg in pairs:
    a, b = sorted((lum(tokens[fg]), lum(tokens[bg])), reverse=True)
    ratio = (a + 0.05) / (b + 0.05)
    if ratio < 4.5:
        fail(f"{fg} on {bg} has contrast {ratio:.2f}")

# 7. Contents page numbers.
ids = re.findall(r'<section class="page" id="(\w+)"', PAGE)
for target, page in re.findall(r'href="#(\w+)"><span>[^<]*</span><span>page (\d+)</span>', PAGE):
    if ids.index(target) + 1 != int(page):
        fail(f"the contents says {target} is on page {page}, but it is on page {ids.index(target) + 1}")

# 8. Links.
hrefs = set(html.unescape(h) for h in re.findall(r'href="(https[^"]+)"', PAGE))
for h in hrefs:
    if f"/URI ({h})" not in text and f"/URI({h})" not in text:
        fail(f"the PDF does not hold the link {h}")
if "--offline" not in sys.argv:
    for h in hrefs:
        req = urllib.request.Request(h, method="HEAD", headers={"User-Agent": "sati-guide-check"})
        try:
            code = urllib.request.urlopen(req, timeout=20).status
        except Exception as e:  # a redirect to a custom scheme fails here, which is expected
            code = getattr(e, "code", str(e))
        if code != 200 and "obtainium://" not in h:
            fail(f"{h} answered {code}")

pages = re.search(r"Pages:\s+(\d+)", run("pdfinfo", str(PDF)))[1]
print(f"{'FAIL' if fails else 'ok'}: {len(fails)} faults, {pages} pages, {len(hrefs)} links, "
      f"{len(set(re.findall(r"<span class=\"ui\">([^<]+)<", PAGE)))} screen words")
sys.exit(1 if fails else 0)
