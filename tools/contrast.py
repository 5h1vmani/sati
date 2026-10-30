"""Checks WCAG contrast for every text-on-background pair in Sati's palette.

Fails with exit code 1 if any pair is below 4.5:1, the WCAG AA level for body text.
Keep PAIRS in step with app/src/main/java/com/padakanti/sati/ui/Theme.kt.
"""
import sys

PAIRS = {
    # name: (foreground, background)
    "light onPrimary / primary (hero, buttons)": ("#FFFFFF", "#A55E3B"),
    "light primary / surface (links, accents)": ("#A55E3B", "#FFF8F5"),
    "light onSurface / surface": ("#221A16", "#FFF8F5"),
    "light onSurfaceVariant / surfaceContainer": ("#53443D", "#F7EBE5"),
    "light onPrimaryContainer / primaryContainer": ("#3B1607", "#FFDBCC"),
    "light onSecondaryContainer / secondaryContainer": ("#2C1A12", "#F2DED5"),
    "light error / surface": ("#B3261E", "#FFF8F5"),
    "dark onPrimary / primary": ("#4A1F0B", "#F0A884"),
    "dark onSurface / surface": ("#F1DFD8", "#1A1110"),
    "dark onSurfaceVariant / surfaceContainer": ("#D8C2BA", "#261D1A"),
    "dark primary / surface": ("#F0A884", "#1A1110"),
    "dark onPrimaryContainer / primaryContainer": ("#FFDBCC", "#7E4227"),
    "dark onSecondaryContainer / secondaryContainer": ("#FFDBCC", "#5A4038"),
    "dark error / surface": ("#FFB4AB", "#1A1110"),
    "light onSurfaceVariant / surfaceContainerHigh": ("#53443D", "#F2E4DD"),
    "dark onSurfaceVariant / surfaceContainerHigh": ("#D8C2BA", "#312825"),
    "light onSecondaryContainer... paused hero": ("#2C1A12", "#F2DED5"),
    "both white / brand hero": ("#FFFFFF", "#A55E3B"),
    "icon near-white / brand": ("#FFF8F2", "#A55E3B"),
}


def lum(hex_):
    rgb = [int(hex_[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


bad = 0
for name, (fg, bg) in PAIRS.items():
    r = ratio(fg, bg)
    ok = r >= 4.5
    bad += not ok
    print(f"{'ok  ' if ok else 'FAIL'} {r:5.2f}  {name}")
sys.exit(1 if bad else 0)
