#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build/resolution-probe
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 \
 -classpath build/shrunk.jar:build/test-classes -d build/resolution-probe tools/resolution-probe/*.java
java -cp build/resolution-probe:build/shrunk.jar:build/test-classes jp.tapmarker.ResolutionReplayProbe
