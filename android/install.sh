#!/usr/bin/env bash
# Build the Android TV launcher and install it as Home over ADB.
# Usage: TV_HOST=192.168.0.67 android/install.sh   (turn off any VPN on the Mac first)
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV's IP address}"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
adb connect "$TV_HOST:5555" >/dev/null
adb -s "$TV_HOST:5555" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_HOST:5555" shell cmd package set-home-activity --user 0 no.matsfjeldstad.cleanframe/.MainActivity
adb -s "$TV_HOST:5555" shell am start -S -n no.matsfjeldstad.cleanframe/.MainActivity >/dev/null
echo "Installed as Home. Undo: adb shell cmd package set-home-activity --user 0 <other launcher>/<activity>"
