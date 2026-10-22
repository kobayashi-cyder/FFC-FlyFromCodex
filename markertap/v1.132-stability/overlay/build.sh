#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT, or run python3 setup-toolchain.py first}"
TAPMARKER_JADX_JAR="${TAPMARKER_JADX_JAR:-$PWD/tooling/jadx/lib/jadx-1.5.3-all.jar}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
LIBS=$(printf '%s:' libs/*.jar)
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
import hashlib,shutil
base=Path('base/runtime-foundation.zip')
if hashlib.sha256(base.read_bytes()).hexdigest()!='b7a3484f690178bf7cadd87390460fe37cefc7e310b26cfcae697cfd991c81cc':raise SystemExit('Unexpected runtime foundation')
for name in ['build/helpers','build/baseline','build/api','build/runtime','build/dex']:
 p=Path(name)
 if p.exists():shutil.rmtree(p)
 p.mkdir(parents=True)
with ZipFile('baseline-api.jar') as z:z.extractall('build/baseline')
PY
python3 tools/build_custom_graph.py assets
java -jar compiler/ecj.jar -nowarn -source 8 -target 8 -cp "$TAPMARKER_JADX_JAR" -d build/helpers tools/PatchRuntime.java tools/DexAudit.java tools/IntegrationAudit.java tools/BiologicalRemovalAudit.java
java -cp "build/helpers:$TAPMARKER_JADX_JAR" PatchRuntime api build/baseline build/api
java -jar compiler/ecj.jar -nowarn -source 8 -target 8 -cp "build/api:baseline-api.jar:$JAR:$LIBS" -d build/api api-src/jp/tapmarker/*.java src/jp/tapmarker/CustomConnectome.java src/jp/tapmarker/RegionImage.java src/jp/tapmarker/HardwarePixelSource.java src/jp/tapmarker/PointPixels.java src/jp/tapmarker/FrameRetirement.java
java -jar compiler/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -cp "build/api:baseline-api.jar:$JAR:$LIBS" -d build/runtime src/jp/tapmarker/*.java
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
for name in ['runtime','api']:
 root=Path('build')/name
 with ZipFile('build/'+name+'.jar','w',compression=ZIP_DEFLATED) as z:
  for p in sorted(root.rglob('*.class')):z.write(p,p.relative_to(root).as_posix())
PY
"$BT/d8" --release --min-api 24 --lib "$JAR" --classpath baseline-api.jar --classpath build/api.jar --output build/dex build/runtime.jar
java -cp "build/helpers:$TAPMARKER_JADX_JAR" PatchRuntime merge base/runtime-foundation.zip build/dex/classes.dex build/merged.dex > build/dex-merge.log
java -cp "build/helpers:$TAPMARKER_JADX_JAR" DexAudit build/merged.dex > build/dex-audit.log
java -cp "build/helpers:$TAPMARKER_JADX_JAR" IntegrationAudit build/merged.dex > build/integration-audit.log
mkdir -p output
python3 tools/package_apk.py base/runtime-foundation.zip build/merged.dex build/unsigned.apk
"$BT/zipalign" -P 16 -f 4 build/unsigned.apk output/MarkerTap-v1.132-unsigned.apk
if [[ -n "${TAPMARKER_KEYSTORE:-}" ]]; then
 "${BT}/apksigner" sign --ks "$TAPMARKER_KEYSTORE" --ks-key-alias tapmarker --ks-pass pass:android --v4-signing-enabled false --out output/MarkerTap-v1.132-integrated.apk output/MarkerTap-v1.132-unsigned.apk
 "$BT/apksigner" verify --verbose --print-certs output/MarkerTap-v1.132-integrated.apk > build/signature.log
 python3 - <<'PY'
from pathlib import Path
expected='Signer #1 certificate SHA-256 digest: 7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
if expected not in Path('build/signature.log').read_text():raise SystemExit('Signing certificate differs from the installed-app lineage')
PY
 java -cp "build/helpers:$TAPMARKER_JADX_JAR" BiologicalRemovalAudit output/MarkerTap-v1.132-integrated.apk > build/biological-removal.log
 "$BT/zipalign" -c -P 16 4 output/MarkerTap-v1.132-integrated.apk
 "$BT/aapt2" dump badging output/MarkerTap-v1.132-integrated.apk > build/badging.log
 echo 'Built output/MarkerTap-v1.132-integrated.apk'
else
 echo 'Built output/MarkerTap-v1.132-unsigned.apk; set TAPMARKER_KEYSTORE to the existing signing backup to sign it.'
fi
