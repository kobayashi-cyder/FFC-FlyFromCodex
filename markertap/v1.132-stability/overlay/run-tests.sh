#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
LIBS=$(printf '%s:' libs/*.jar)
TEST_LIBS=$(printf '%s:' test-libs/*.jar)
JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/tests
java -jar compiler/ecj.jar -nowarn -source 8 -target 8 -cp "build/runtime:baseline-api.jar:$JAR:$LIBS" -d build/tests $(find tests -maxdepth 1 -name '*.java' ! -name 'ResidualFenceTest.java' ! -name 'DiagnosticCaptureAnrTest.java' ! -name 'HardwarePixelSourceTest.java')
for test in AutonomySupervisor PointPixels CancelledLease IntegratedRuntime CustomConnectome CustomMemory ActionConnectome BancAutonomy BancKnowHow FrameCircuit FrameSampleCache TilePixels FramePixels VisualLocator DisplayRecovery ManualIntervention ContinuousRecovery; do
 java -Xmx512m -cp "build/runtime:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.${test}Test
done
if [[ "${TAPMARKER_REPLAY:-0}" == 1 ]]; then
 java -Xmx512m -cp "build/tests:baseline-runtime.jar:$JAR:$LIBS" jp.tapmarker.IncidentDigestBefore > build/incident-before.log
 java -Xmx512m -cp "build/runtime:build/tests:baseline-runtime.jar:$JAR:$LIBS" jp.tapmarker.IncidentDigestAfter > build/incident-after.log
 cmp build/incident-before.log build/incident-after.log
 echo 'Incident replay: all 8 exact result digests match.'
fi

# Independent verification for the bounded Binder probe used in v1.126.
mkdir -p build/standalone-tests
javac -d build/standalone-tests src/jp/tapmarker/BoundedProbeCache.java src/jp/tapmarker/AnalysisBudget.java tests/BoundedProbeCacheTest.java
java -cp build/standalone-tests jp.tapmarker.BoundedProbeCacheTest

# ANR trace excerpt parser is independent from Android UI and can be validated on host.
javac -d build/standalone-tests src/jp/tapmarker/AnrTraceExcerpt.java tests/AnrTraceExcerptTest.java
java -cp build/standalone-tests jp.tapmarker.AnrTraceExcerptTest

# Pre-start residue fence: pure JVM stubs, no device permissions required.
mkdir -p build/preflight-tests
javac -encoding UTF-8 -d build/preflight-tests src/jp/tapmarker/ResidualFence.java \
 tests/preflight-stubs/jp/tapmarker/VirtualShell.java \
 tests/preflight-stubs/android/content/Context.java \
 tests/preflight-stubs/android/view/Display.java \
 tests/preflight-stubs/android/hardware/display/DisplayManager.java \
 tests/ResidualFenceTest.java
java -cp build/preflight-tests jp.tapmarker.ResidualFenceTest

# Wireless reconnect fixes retained from installed v1.128
java -cp "build/runtime:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.WirelessReconnectStabilityTest

# No native frame retirement may stall UI or frameLock while backend close blocks.
java -cp "build/runtime:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.FrameRetirementTest
java -cp "build/runtime:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.OwnerDeathCleanupTest

# v1.130 static bridge invariants (before device testing).
python3 tests/FrameLockBridgeSourceTest.py
