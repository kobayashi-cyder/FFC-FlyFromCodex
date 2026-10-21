#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${TAPMARKER_KEYSTORE:?Set TAPMARKER_KEYSTORE}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
TAPMARKER_ABIS="${TAPMARKER_ABIS:-arm64-v8a,armeabi-v7a,x86,x86_64}"
TAPMARKER_OUTPUT="${TAPMARKER_OUTPUT:-../MarkerTap-v1.123-preview-connectome.apk}"
export TAPMARKER_ABIS
python3 - <<'PYBUILD'
from pathlib import Path
import shutil
for name in ('build/classes','build/dex','build/generated'):
    path=Path(name)
    if path.is_dir(): shutil.rmtree(path)
for name in ('resources.zip','base.apk','unsigned.apk','aligned.apk','app-classes.jar','shrunk.jar'):
    Path('build',name).unlink(missing_ok=True)
PYBUILD
python3 tools/generate-locales.py
mkdir -p build/classes build/dex build/generated
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk --manifest AndroidManifest.xml -I "$JAR" --java build/generated --custom-package jp.tapmarker build/resources.zip
LIBS=$(printf '%s:' libs/*.jar)
rg --files src build/generated third-party/libadb-android-master/libadb/src/main/java -g '*.java' > build/sources.txt
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -classpath "$JAR:$LIBS" -d build/classes @build/sources.txt
python3 - <<'PYCLASSES'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
root=Path('build/classes')
with ZipFile('build/app-classes.jar','w',compression=ZIP_DEFLATED) as jar:
    for path in sorted(root.rglob('*.class')): jar.write(path,path.relative_to(root))
PYCLASSES
# Test the same reduced Java input that will be converted into the APK.
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --classfile --release --no-desugaring --lib "$JAR" --pg-conf shrink-rules.pro --pg-conf-output build/shrink-effective.pro --output build/shrunk.jar build/app-classes.jar libs/*.jar
python3 - <<'PYSTRIP'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
p=Path('build/shrunk.jar');temporary=Path('build/shrunk-clean.jar')
with ZipFile(p) as source,ZipFile(temporary,'w',compression=ZIP_DEFLATED) as output:
    for entry in source.infolist():
        name=entry.filename.upper()
        if name=='META-INF/MANIFEST.MF' or name.startswith('META-INF/') and name.endswith(('.SF','.RSA','.DSA','.EC')): continue
        output.writestr(entry,source.read(entry))
temporary.replace(p)
PYSTRIP
"$BT/d8" --release --lib "$JAR" --min-api 24 --output build/dex build/shrunk.jar
cp build/base.apk build/unsigned.apk
python3 - <<'PYPACK'
from pathlib import Path
from zipfile import ZipFile,ZIP_STORED,ZIP_DEFLATED
import os
abis=os.environ['TAPMARKER_ABIS'].split(',')
allowed={'arm64-v8a','armeabi-v7a','x86','x86_64'}
if not abis or any(abi not in allowed for abi in abis): raise ValueError('unsupported ABI selection')
with ZipFile('build/unsigned.apk','a') as apk:
    if 'AndroidManifest.xml' not in apk.namelist(): raise ValueError('compiled manifest missing')
    for asset in sorted(Path('assets').rglob('*')):
        if asset.is_file(): apk.write(asset,'assets/'+asset.relative_to('assets').as_posix(),compress_type=ZIP_DEFLATED,compresslevel=9)
    for dex in sorted(Path('build/dex').glob('classes*.dex')): apk.write(dex,dex.name,compress_type=ZIP_DEFLATED,compresslevel=9)
    for abi in abis:
        libraries=sorted((Path('native')/abi).glob('*.so'))
        if len(libraries)!=2: raise ValueError('native pairing libraries missing: '+abi)
        for library in libraries: apk.write(library,'lib/'+abi+'/'+library.name,compress_type=ZIP_DEFLATED,compresslevel=9)
PYPACK
"$BT/zipalign" -P 16 -f 4 build/unsigned.apk build/aligned.apk
TAPMARKER_SIGNED_DIR=$(mktemp -d build/sign-XXXXXX)
trap 'rm -rf "$TAPMARKER_SIGNED_DIR"' EXIT
TAPMARKER_SIGNED_STAGE="$TAPMARKER_SIGNED_DIR/app.apk"
"$BT/apksigner" sign --ks "$TAPMARKER_KEYSTORE" --ks-key-alias tapmarker --ks-pass pass:android --v4-signing-enabled false --out "$TAPMARKER_SIGNED_STAGE" build/aligned.apk
"$BT/apksigner" verify --verbose --print-certs "$TAPMARKER_SIGNED_STAGE"
"$BT/zipalign" -c -P 16 4 "$TAPMARKER_SIGNED_STAGE"
python3 - "$TAPMARKER_SIGNED_STAGE" "$TAPMARKER_OUTPUT" <<'PYPUBLISH'
from pathlib import Path
from zipfile import ZipFile
import os,sys
stage,output=map(Path,sys.argv[1:])
with ZipFile(stage) as apk:
    if apk.testzip() is not None: raise ValueError('signed APK checksum failure')
    for dex in Path('build/dex').glob('classes*.dex'):
        if apk.read(dex.name)!=dex.read_bytes(): raise ValueError('signed runtime mismatch')
with stage.open('rb') as stream: os.fsync(stream.fileno())
os.replace(stage,output)
PYPUBLISH
