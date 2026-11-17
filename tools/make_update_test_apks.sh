#!/bin/bash
# Test-only certificates; never used to sign a release.
set -euo pipefail
./gradlew --no-daemon assembleDebug -PversionCodeOverride=10001 -PversionNameOverride=1.0.1-test
mkdir -p app/src/androidTest/assets/updates
cp app/build/outputs/apk/debug/app-debug.apk app/src/androidTest/assets/updates/future.apk
keytool -genkeypair -keystore "$RUNNER_TEMP/wrong-test.p12" -storetype PKCS12 -storepass fixture-only -keypass fixture-only -alias wrong-test -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Wrong test signer'
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$RUNNER_TEMP/wrong-test.p12" --ks-key-alias wrong-test --ks-pass pass:fixture-only --key-pass pass:fixture-only --out app/src/androidTest/assets/updates/wrong.apk app/src/androidTest/assets/updates/future.apk
