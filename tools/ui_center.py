"""Prints the centre of the last screen element whose text is exactly the given label.

Reads a uiautomator dump on standard input. tools/emulator_check.sh uses it
to tap buttons by their label. Prints nothing when no element matches.

Usage: adb exec-out cat /sdcard/ui.xml | python3 tools/ui_center.py "Test bell"
"""
import html
import re
import sys

label = sys.argv[1]
found = None
for node in re.findall(r"<node [^>]*>", sys.stdin.read()):
    text = re.search(r' text="([^"]*)"', node)
    box = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if text and box and html.unescape(text.group(1)) == label:
        x1, y1, x2, y2 = map(int, box.groups())
        if x2 > x1 and y2 > y1:
            found = ((x1 + x2) // 2, (y1 + y2) // 2)
if found:
    print(*found)
