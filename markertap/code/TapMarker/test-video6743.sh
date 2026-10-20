#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -d "$TMP_DIR" \
 src/jp/tapmarker/RegionMath.java \
 src/jp/tapmarker/RegionSampler.java \
 src/jp/tapmarker/FrameSampleCache.java \
 src/jp/tapmarker/FarmAdErrorEvidence.java \
 src/jp/tapmarker/FarmAdErrorRecovery.java \
 tests/FarmAdErrorRecoveryTest.java
java -cp "$TMP_DIR" jp.tapmarker.FarmAdErrorRecoveryTest
