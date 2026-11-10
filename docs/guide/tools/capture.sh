# Helpers to take the guide's screens on an emulator. Source this file from the repo root:
#   OUT=docs/guide/screens . docs/guide/tools/capture.sh
# Use them on an emulator only, never on a phone with your real data.
#   dump        prints each labelled element on the screen with its bounds
#   tapt LABEL  taps the last element whose text or description is LABEL
#   shot NAME   saves the screen as $OUT/NAME.png and its dump as $OUT/NAME.xml
#   demo        shows a clean status bar: 9:00, full battery, no notifications
SDK=$(sed -n 's/^sdk.dir=//p' local.properties)
SERIAL=${SERIAL:-emulator-5580}
HERE=docs/guide/tools
a() { "$SDK/platform-tools/adb" -s "$SERIAL" "$@"; }
dump() { a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; a exec-out cat /sdcard/ui.xml > /tmp/sati-ui.xml; python3 -I $HERE/ui.py /tmp/sati-ui.xml list; }
tapt() { dump >/dev/null; xy=$(python3 -I $HERE/ui.py /tmp/sati-ui.xml find "$1"); if [ -n "$xy" ]; then a shell input tap $xy; else echo "no element: $1"; return 1; fi; }
shot() { a exec-out screencap -p > "$OUT/$1.png"; cp /tmp/sati-ui.xml "$OUT/$1.xml"; }
demo() {
    a shell settings put global sysui_demo_allowed 1
    d() { a shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
    d enter; d clock -e hhmm 0900; d battery -e level 100 -e plugged false
    d network -e wifi show -e level 4 -e fully true; d network -e mobile hide; d notifications -e visible false
}
