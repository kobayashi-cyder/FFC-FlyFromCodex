#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 \
 -classpath build/classes:libs/bcprov-jdk15to18-1.81.jar -d build/test-classes tests/*.java
for test in VirtualResolutionPolicy PerformancePolicy UiCatalog FrameCircuit VirtualPreparation VirtualRunActivation VirtualDisplayPolicy VirtualPreview VirtualPreviewReplay VirtualFrameQueue VirtualLoadPolicy VirtualTarget DisplayLane DisplayTaskGuard TargetedInput CancelledAction GestureDispatchGuard ProgressWatchdog; do
 java -cp build/shrunk.jar:build/test-classes jp.tapmarker.${test}Test
done
python3 tests/adb_starter_test.py
