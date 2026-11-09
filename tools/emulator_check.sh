#!/bin/sh
# Checks Sati on emulators, one Android version at a time. On each one it:
#   1. runs the instrumented tests, such as the database migration tests;
#   2. turns the hourly bell on and taps Test bell with Sati in the background.
#      The bell must ring, and Android must not mute it;
#   3. restarts the emulator. The hourly bell's alarm must be set again.
#
# Never run this with a phone connected. Gradle installs a debug build on
# every connected device. On a phone with the release build of Sati, that
# fails on the signature, and Gradle uninstalls the app after the tests. The
# log on that phone would be lost. So this script stops when any device that
# is not an emulator is connected.
#
# Each emulator starts without a window and with wiped data, then shuts down.
# Only one runs at a time.
#
# Usage: tools/emulator_check.sh [avd ...]
# The default AVDs are api31 (Android 12), api36 (Android 16) and api37 (Android 17).
set -eu
cd "$(dirname "$0")/.."

sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"
avds=${*:-"api31 api36 api37"}
# A fixed port that other emulators on this Mac do not use by default.
port=5580
serial="emulator-$port"
apk=app/build/outputs/apk/debug/app-debug.apk

# Stops when a phone is connected. Called again right before every test run.
no_phone() {
    phones=$("$adb" devices | awk 'NR > 1 && $1 != "" && $1 !~ /^emulator-/ { print $1 }')
    if [ -n "$phones" ]; then
        echo "A phone is connected: $phones. Unplug it or turn off USB debugging, then run this again." >&2
        exit 1
    fi
}

a() { "$adb" -s "$serial" "$@"; }

wait_boot() {
    a wait-for-device
    until [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
}

stop_emulator() {
    a emu kill >/dev/null 2>&1 || true
    while "$adb" devices | grep -q "^$serial"; do sleep 1; done
}

# The emulator, the Gradle daemon and the Kotlin compiler daemon use about
# 3 GB of memory together. All three stop on exit, so other work gets it back.
clean_up() {
    stop_emulator
    ./gradlew --stop >/dev/null 2>&1 || true
    pkill -f KotlinCompileDaemon || true
}
trap clean_up EXIT

# Saves the screen's elements to build/ui.xml.
dump_ui() {
    a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    a exec-out cat /sdcard/ui.xml > build/ui.xml
}

# Taps the last element whose text is exactly $1. Returns 1 if there is none.
tap_text() {
    dump_ui
    xy=$(python3 -I tools/ui_center.py "$1" < build/ui.xml)
    [ -n "$xy" ] || return 1
    # shellcheck disable=SC2086
    a shell input tap $xy
}

scroll_down() {
    for _ in 1 2 3 4 5; do a shell input swipe 540 1800 540 600 200; done
    sleep 1
}

# True when the hourly bell's alarm is set. Past alarms in the dump start with "[tag=", so they do not count.
bell_alarm_set() {
    a shell dumpsys alarm | grep -qE '^ +tag=\*walarm\*:com\.padakanti\.sati/\.BellReceiver'
}

open_sati() {
    a shell am start -n com.padakanti.sati/.MainActivity >/dev/null 2>&1
    sleep 3
}

no_phone
if "$adb" devices | grep -q "^$serial"; then
    echo "$serial is already running. Stop it first." >&2
    exit 1
fi

./gradlew -q assembleDebug assembleDebugAndroidTest
mkdir -p build

summary=""
failed=0

for avd in $avds; do
    echo "== $avd"
    "$emulator" -avd "$avd" -port "$port" -no-window -no-snapshot -wipe-data -no-boot-anim >/dev/null 2>&1 &
    wait_boot
    api=$(a shell getprop ro.build.version.sdk | tr -d '\r')

    # 1. Instrumented tests.
    no_phone
    if ANDROID_SERIAL="$serial" ./gradlew -q connectedDebugAndroidTest; then tests=pass; else tests=FAIL; failed=1; fi
    # Gradle overwrites its report on the next run, so each one is kept here.
    mkdir -p "build/emulator_check/$avd"
    cp -R app/build/outputs/androidTest-results/connected/. "build/emulator_check/$avd/" 2>/dev/null || true

    # 2. Test bell. Gradle removed the app after the tests, so it is installed again.
    # The whitelist keeps the first turn-on from opening the battery dialog.
    no_phone
    a install -r "$apk" >/dev/null
    a shell cmd deviceidle whitelist +com.padakanti.sati >/dev/null
    open_sati
    tap_text "Turn on" || true
    sleep 2
    scroll_down
    a logcat -c
    if tap_text "Test bell"; then
        a shell input keyevent HOME
        sleep 20
        a logcat -d > "build/emulator_check/$avd/logcat.txt"
        open_sati
        scroll_down
        dump_ui
        rang=no; played=no; muted=no
        grep -q 'Test at [^"]*: it rang\.' build/ui.xml && rang=yes
        grep -q 'requestAudioFocus.*com\.padakanti\.sati' "build/emulator_check/$avd/logcat.txt" && played=yes
        # Android 17 logs "would be muted" when a foreground service runs. Only a real mute fails.
        grep 'AudioHardening' "build/emulator_check/$avd/logcat.txt" | grep -v 'would be muted' | grep -q 'muted' && muted=yes
        if [ $rang = yes ] && [ $played = yes ] && [ $muted = no ]; then bell=pass; else bell="FAIL (card rang: $rang, sound: $played, muted: $muted)"; failed=1; fi
    else
        bell="FAIL (no Test bell button)"; failed=1
    fi

    # 3. Restart. The hourly bell's alarm must be set before and after.
    if bell_alarm_set; then
        a reboot
        sleep 5
        wait_boot
        sleep 10
        if bell_alarm_set; then boot=pass; else boot="FAIL (alarm missing after restart)"; failed=1; fi
    else
        boot="FAIL (alarm missing before restart)"; failed=1
    fi

    summary="$summary$avd (API $api): instrumented tests $tests, test bell $bell, restart $boot
"
    stop_emulator
done

echo
echo "Results:"
printf '%s' "$summary"
exit $failed
