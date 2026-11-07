#!/bin/sh
# Checks that the release build is reproducible, as F-Droid needs.
#
# It copies the working tree into two different folders, builds an unsigned
# release APK in each, and checks that the two are identical byte for byte.
# Different folders matter, because F-Droid builds in its own path.
#
# With a signed APK as the argument, it also checks that the signature from
# that APK fits the new unsigned build. This is the check F-Droid runs. It
# needs apksigcopier (pip install apksigcopier) and apksigner on the PATH.
#
# Usage: tools/repro_check.sh [signed.apk]
set -eu

# apksigcopier lives in its own Python environment, and apksigner in the
# Android build tools. Both are put on the PATH here, so no session has to.
# Set up the environment once with:
#   python3 -m venv ~/.local/share/sati-tools && ~/.local/share/sati-tools/bin/pip install apksigcopier

root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
sdk=$(sed -n 's/^sdk.dir=//p' "$root/local.properties")
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
PATH="$HOME/.local/share/sati-tools/bin:$build_tools:$PATH"

for dir in a b; do
    mkdir -p "$work/$dir"
    (cd "$root" && git ls-files -co --exclude-standard) |
        rsync -a --files-from=- "$root/" "$work/$dir/"
    cp "$root/local.properties" "$work/$dir/"
    (cd "$work/$dir" && env -u SATI_KEYSTORE -u SATI_KEY_PASSWORD \
        ./gradlew -q --no-configuration-cache assembleRelease)
done

apk=app/build/outputs/apk/release/app-release-unsigned.apk
if cmp -s "$work/a/$apk" "$work/b/$apk"; then
    echo "Two builds in different folders are identical."
else
    echo "FAIL: two builds in different folders differ." >&2
    exit 1
fi

if [ $# -ge 1 ]; then
    apksigcopier compare "$1" --unsigned "$work/a/$apk"
    echo "The signature from $1 fits the new build."
fi
