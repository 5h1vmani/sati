#!/usr/bin/env python3
"""Checks the manifest against docs/PERMISSIONS.md.

1. Every <uses-permission> in the manifest must appear in the doc.
2. Every permission name in the doc must appear somewhere in the manifest.
3. Only the components in EXPORTED may set android:exported="true".

So the check fails when a permission is added without a reason in the doc,
when the doc names a permission that Sati does not have, and when a
component is opened to other apps. To export a new component, explain why
in docs/PERMISSIONS.md and add it to EXPORTED.

Usage: python3 tools/check_permissions.py
"""
import re
import sys
from pathlib import Path

# MainActivity is the launcher screen. The system binds to BellTileService.
EXPORTED = {".MainActivity", ".BellTileService"}

root = Path(__file__).resolve().parent.parent
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
doc = (root / "docs/PERMISSIONS.md").read_text()

NAME = re.compile(r"\b[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)*\.permission\.[A-Z0-9_]+\b")
uses = set(re.findall(r'<uses-permission\b[^>]*?android:name="([^"]+)"', manifest, re.DOTALL))
in_manifest = set(NAME.findall(manifest))
in_doc = set(NAME.findall(doc))

failures = []
for name in sorted(uses - in_doc):
    failures.append(f"{name} is in the manifest but not in docs/PERMISSIONS.md")
for name in sorted(in_doc - in_manifest):
    failures.append(f"{name} is in docs/PERMISSIONS.md but not in the manifest")

components = re.findall(r"<(?:activity|activity-alias|receiver|service|provider)\b([^>]*)>", manifest, re.DOTALL)
for attrs in components:
    name = re.search(r'android:name="([^"]+)"', attrs)
    exported = re.search(r'android:exported="true"', attrs)
    if exported and name and name.group(1) not in EXPORTED:
        failures.append(f"{name.group(1)} is exported but not in EXPORTED in tools/check_permissions.py")

for failure in failures:
    print(f"FAIL: {failure}")
if failures:
    sys.exit(1)
print(f"docs/PERMISSIONS.md lists all {len(uses)} permissions. "
      f"Only {', '.join(sorted(EXPORTED))} are exported.")
