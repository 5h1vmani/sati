# Sati install guide

This folder builds `Sati-install-guide.pdf`. The guide shows a friend how to install Obtainium and then Sati, and how updates look. Its pages are sized for a phone screen.

The PDF is attached to every GitHub release, so this link always gives the newest guide:
https://github.com/5h1vmani/sati/releases/latest/download/Sati-install-guide.pdf

## Build

The build needs Python 3 with Pillow, Node 22 or newer, Google Chrome and poppler. Run it from this folder.

```bash
python3 -I build.py && node print.mjs && python3 -I check.py
```

- `guide.html` holds the words and the look. Its colours are the Sati app's light theme.
- `build.py` crops the screens and puts a ring on the control to tap. It finds each control by its label in the screen's uiautomator dump.
- `print.mjs` prints the PDF with tags and bookmarks. It fails when a page's content is taller than the page.
- `check.py` runs the free checks. Its header lists them. Pass `--offline` to skip the link check.

## Screens

The screens in `screens/` are real screenshots from an Android 16 emulator at 1440x3200, with Obtainium 1.7.4. Each PNG has its dump beside it as an XML file. The screens of Sati itself are the store screenshots in `fastlane/`. `tools/capture.sh` holds the adb helpers that take them. Take the screens again when Obtainium changes its screens.

## Font

The font is Roboto from the Android emulator's `/system/fonts`, cut to Latin letters. Roboto uses the Apache 2.0 license.
