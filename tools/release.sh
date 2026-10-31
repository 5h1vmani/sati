#!/bin/sh
# Builds the signed release APK and prints its certificate.
#
# The key lives at ~/keys/sati-release.jks, outside the repo. Its password
# lives in the macOS Keychain as "sati-release-key". Neither is ever written
# to a file in this repo. Run from any folder.
set -eu
cd "$(dirname "$0")/.."

SATI_KEYSTORE="$HOME/keys/sati-release.jks"
if [ ! -f "$SATI_KEYSTORE" ]; then
    echo "No release key at $SATI_KEYSTORE. Restore it from the backup first." >&2
    exit 1
fi
SATI_KEY_PASSWORD="$(security find-generic-password -s sati-release-key -a sati -w)"
export SATI_KEYSTORE SATI_KEY_PASSWORD

./gradlew testDebugUnitTest lintRelease assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
sdk="$(sed -n 's/^sdk.dir=//p' local.properties)"
apksigner="$(ls -d "$sdk"/build-tools/*/apksigner | sort -V | tail -1)"
"$apksigner" verify --print-certs "$apk"
echo "Signed APK: $apk"
