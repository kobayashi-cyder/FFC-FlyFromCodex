#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${TAPMARKER_KEYSTORE:?Set the original TAPMARKER_KEYSTORE}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
TAPMARKER_OUTPUT="${TAPMARKER_OUTPUT:-../MarkerTap-v1.107-frame-circuit.apk}"
stage=$(mktemp -d release/sign-XXXXXX)
trap 'rm -rf "$stage"' EXIT
"$BT/apksigner" sign --ks "$TAPMARKER_KEYSTORE" --ks-key-alias tapmarker --ks-pass pass:android --v4-signing-enabled false --out "$stage/app.apk" release/MarkerTap-v1.107-unsigned.apk
"$BT/apksigner" verify --verbose --print-certs "$stage/app.apk" > "$stage/signature.txt"
python3 - "$stage/signature.txt" <<'PY'
from pathlib import Path
import sys
expected='7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
text=Path(sys.argv[1]).read_text()
if 'Signer #1 certificate SHA-256 digest: '+expected not in text:
    raise SystemExit('The signing certificate differs from v1.94. No update APK was published.')
PY
"$BT/zipalign" -c -P 16 4 "$stage/app.apk"
python3 - "$stage/app.apk" "$TAPMARKER_OUTPUT" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import os,sys
stage,output=map(Path,sys.argv[1:])
with ZipFile(stage) as apk:
    if apk.testzip() is not None: raise SystemExit('APK checksum failure')
os.replace(stage,output)
print(output)
PY
