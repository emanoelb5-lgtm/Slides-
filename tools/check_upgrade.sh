#!/bin/bash
set -euo pipefail
package_id=online.b5solucoes.slide.debug
expected_code="$(cat test-apks/future-version-code.txt)"
data_path="$(adb shell dumpsys package "$package_id" | sed -n 's/^[[:space:]]*dataDir=//p' | head -1 | tr -d '\r')"
test -n "$data_path"
adb shell run-as "$package_id" ls -la "$data_path/shared_prefs"
adb shell run-as "$package_id" cat "$data_path/shared_prefs/slide.xml" > emulator-results/preferences-before.xml
grep -q 'slide-position-and-import-v1' emulator-results/preferences-before.xml
adb install -r test-apks/future.apk
adb shell run-as "$package_id" cat "$data_path/shared_prefs/slide.xml" > emulator-results/preferences-after.xml
cmp emulator-results/preferences-before.xml emulator-results/preferences-after.xml
adb shell dumpsys package "$package_id" | grep -E "versionCode=${expected_code}([[:space:]]|$)"
printf 'Installed a newer APK and preserved imported presentations and saved position.\n'
