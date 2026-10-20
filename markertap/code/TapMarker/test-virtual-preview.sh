#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -d "$TEST_DIR" \
 src/jp/tapmarker/VirtualTarget.java src/jp/tapmarker/VirtualPreview.java \
 src/jp/tapmarker/RegionMath.java src/jp/tapmarker/RegionSampler.java \
 src/jp/tapmarker/FrameSampleCache.java src/jp/tapmarker/FarmAdErrorEvidence.java \
 src/jp/tapmarker/FarmAdErrorRecovery.java \
 tests/VirtualPreviewTest.java tests/VirtualPreviewReplayTest.java tests/FarmAdErrorRecoveryTest.java
for test in VirtualPreview VirtualPreviewReplay FarmAdErrorRecovery; do
 java -cp "$TEST_DIR" jp.tapmarker.${test}Test
done
