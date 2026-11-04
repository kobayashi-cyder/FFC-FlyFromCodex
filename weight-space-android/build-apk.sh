#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
test -n "$ANDROID_HOME"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
TOOLS="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p build/test-classes build/classes build/dex build/artifacts
javac -encoding UTF-8 -source 8 -target 8 -d build/test-classes src/jp/tapmarker/weightspace/WeightSpacePolicy.java tests/jp/tapmarker/weightspace/WeightSpacePolicyTest.java
java -cp build/test-classes jp.tapmarker.weightspace.WeightSpacePolicyTest | tee build/artifacts/test-report.txt
"$TOOLS/aapt2" link -o build/base.apk --manifest AndroidManifest.xml -I "$PLATFORM"
find src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -source 8 -target 8 -cp "$PLATFORM" -d build/classes
(cd build/classes && jar cf ../classes.jar .)
"$TOOLS/d8" --release --min-api 26 --lib "$PLATFORM" --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a') as f:
    f.write('build/dex/classes.dex','classes.dex')
PY
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
keytool -genkeypair -noprompt -alias weightspace -keyalg RSA -keysize 2048 -validity 365 -keystore build/test-key.jks -storepass android -keypass android -dname 'CN=Weight Space Lab Temporary CI Key'
"$TOOLS/apksigner" sign --ks build/test-key.jks --ks-key-alias weightspace --ks-pass pass:android --v4-signing-enabled false --out build/artifacts/WeightSpaceLab-v0.1-standalone-test-signed.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/artifacts/WeightSpaceLab-v0.1-standalone-test-signed.apk
"$TOOLS/zipalign" -c -p 4 build/artifacts/WeightSpaceLab-v0.1-standalone-test-signed.apk
unzip -t build/artifacts/WeightSpaceLab-v0.1-standalone-test-signed.apk | tail -1
