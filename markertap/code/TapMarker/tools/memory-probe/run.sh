#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build/memory-probe
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 \
 -classpath build/shrunk.jar:build/test-classes -d build/memory-probe tools/memory-probe/*.java
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('build/memory-probe-agent.jar','w',compression=ZIP_DEFLATED) as jar:
 jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nPremain-Class: jp.tapmarker.HeapAgent\n\n')
 jar.write('build/memory-probe/jp/tapmarker/HeapAgent.class','jp/tapmarker/HeapAgent.class')
PY
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
 -javaagent:build/memory-probe-agent.jar \
 -cp build/memory-probe:build/shrunk.jar:build/test-classes jp.tapmarker.MemoryFootprintProbe
