#!/bin/bash
# Test-only certificates; never used to sign a release.
set -euo pipefail
current_code="$(sed -n 's/^versionCode=//p' version.properties | tr -d '\r')"
current_name="$(sed -n 's/^versionName=//p' version.properties | tr -d '\r')"
future_code=$((current_code + 1))
./gradlew --no-daemon assembleDebug "-PversionCodeOverride=$future_code" "-PversionNameOverride=$current_name-upgrade-test"
mkdir -p app/src/androidTest/assets/updates
cp app/build/outputs/apk/debug/app-debug.apk app/src/androidTest/assets/updates/future.apk
printf '%s\n' "$future_code" > app/src/androidTest/assets/updates/future-version-code.txt
keytool -genkeypair -keystore "$RUNNER_TEMP/wrong-test.p12" -storetype PKCS12 -storepass fixture-only -keypass fixture-only -alias wrong-test -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Wrong test signer'
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$RUNNER_TEMP/wrong-test.p12" --ks-key-alias wrong-test --ks-pass pass:fixture-only --key-pass pass:fixture-only --out app/src/androidTest/assets/updates/wrong.apk app/src/androidTest/assets/updates/future.apk
