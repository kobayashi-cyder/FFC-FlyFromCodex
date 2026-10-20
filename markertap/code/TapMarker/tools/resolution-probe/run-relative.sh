#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build/resolution-probe build/test-classes
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 \
 -classpath build/classes:libs/bcprov-jdk15to18-1.81.jar -d build/test-classes tests/*.java
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 \
 -classpath build/shrunk.jar:build/test-classes -d build/resolution-probe tools/resolution-probe/RelativeElementProbe.java
java -Xmx1024m -cp build/resolution-probe:build/shrunk.jar:build/test-classes jp.tapmarker.RelativeElementProbe
