#!/bin/sh
# Runs the instrumented tests on emulators, one Android version at a time.
#
# Never run instrumented tests on a phone. Gradle installs a debug build on
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

# Stops when a phone is connected. Called again right before every test run.
no_phone() {
    phones=$("$adb" devices | awk 'NR > 1 && $1 != "" && $1 !~ /^emulator-/ { print $1 }')
    if [ -n "$phones" ]; then
        echo "A phone is connected: $phones. Unplug it or turn off USB debugging, then run this again." >&2
        exit 1
    fi
}

stop_emulator() {
    "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
    while "$adb" devices | grep -q "^$serial"; do sleep 1; done
}
trap stop_emulator EXIT

no_phone
if "$adb" devices | grep -q "^$serial"; then
    echo "$serial is already running. Stop it first." >&2
    exit 1
fi

./gradlew -q assembleDebug assembleDebugAndroidTest

summary=""
failed=0
for avd in $avds; do
    echo "== $avd"
    "$emulator" -avd "$avd" -port "$port" -no-window -no-snapshot -wipe-data -no-boot-anim >/dev/null 2>&1 &
    "$adb" -s "$serial" wait-for-device
    until [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
    api=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')

    no_phone
    if ANDROID_SERIAL="$serial" ./gradlew -q connectedDebugAndroidTest; then
        result=pass
    else
        result=FAIL
        failed=1
    fi
    # Gradle overwrites its report on the next run, so each one is kept here.
    mkdir -p "build/emulator_check/$avd"
    cp -R app/build/outputs/androidTest-results/connected/. "build/emulator_check/$avd/" 2>/dev/null || true
    summary="$summary$avd (API $api): instrumented tests $result
"
    stop_emulator
done

echo
echo "Results:"
printf '%s' "$summary"
exit $failed
