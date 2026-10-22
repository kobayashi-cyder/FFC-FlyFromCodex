#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python3 tests/FrameLockBridgeSourceTest.py
mkdir -p build/bridge-host build/host-unit
javac -d build/bridge-host \
  tests/bridge-stubs/android/util/Log.java \
  tests/bridge-stubs/android/graphics/Bitmap.java \
  tests/bridge-stubs/jp/tapmarker/BridgeTestStubs.java \
  tests/bridge-stubs/jp/tapmarker/FrameLockBridgeHostTest.java \
  src/jp/tapmarker/FrameLockBridge.java
java -cp build/bridge-host jp.tapmarker.FrameLockBridgeHostTest
javac -cp baseline-runtime.jar -d build/host-unit tests/InputTargetRecoveryTest.java
java -cp baseline-runtime.jar:build/host-unit jp.tapmarker.InputTargetRecoveryTest
mkdir -p build/owner-host
javac -d build/owner-host src/jp/tapmarker/OwnerDeathCleanup.java tests/OwnerDeathCleanupTest.java
java -cp build/owner-host jp.tapmarker.OwnerDeathCleanupTest
python3 - <<'PY'
from zipfile import ZipFile
from tools.package_apk import manifest_version
with ZipFile('base/runtime-foundation.zip') as z:
 old=z.read('AndroidManifest.xml')
new=manifest_version(old)
assert len(old)==len(new) and '1.131'.encode('utf-16le') in new
print('ManifestVersionTest: v1.131/versionCode134 rewrite passed')
PY
printf 'Host tests passed. Android SDK build and device tests are separate.\n'
