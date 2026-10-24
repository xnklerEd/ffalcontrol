#!/bin/sh
# Build ffalcontrol.apk without Gradle.
#
# Requirements: JDK 17 and an Android SDK with platform 34 + build-tools 34.0.0.
#
#   export JAVA_HOME=/path/to/jdk-17
#   export ANDROID_HOME=/path/to/android-sdk
#   ./build.sh
#
# Signing uses the default debug keystore unless KS/KS_PASS/KS_ALIAS are set.
set -eu
cd "$(dirname "$0")"

VERSION=0.1.4
OUT="ffalcontrol_v${VERSION}.apk"

: "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
AJ="$ANDROID_HOME/platforms/android-34/android.jar"
BT="$ANDROID_HOME/build-tools/34.0.0"

mkdir -p build/classes build/dex
"$JAVA_HOME/bin/javac" --release 8 -cp "$AJ" -d build/classes src/app/ffalcontrol/*.java
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$AJ" --min-api 28 --output build/dex build/classes/app/ffalcontrol/*.class
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml
cp build/dex/classes.dex build/
(cd build && zip -q -u base.apk classes.dex)
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
"$JAVA_HOME/bin/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks "${KS:-$HOME/.android/debug.keystore}" \
    --ks-pass "${KS_PASS:-pass:android}" \
    --ks-key-alias "${KS_ALIAS:-androiddebugkey}" \
    --out "$OUT" build/aligned.apk
echo "built: $(pwd)/$OUT"
