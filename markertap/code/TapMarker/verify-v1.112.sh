#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -classpath build/classes:libs/bcprov-jdk15to18-1.81.jar -d build/test-classes tests/*.java
for test in StateStallRecovery CoinSearchBudget Video6720Replay MissionJourney RecoveryRequest LowResolutionGames Video6767Replay UsagiControlReplay Video6759Replay CancelAdInterruption RecoveryContinuity Video6742Replay AdContinuation GameInputRepair ConfiguredTargetReplay FarmFeverTap FarmAdErrorRecovery FarmControlEvidence FarmDialogReplay CoinNoAdPolicy CoinCancelScreenshot AdExitPolicy AdExitSwitch ContinuousRecovery InputTargetRecovery TargetedInput DisplayTaskGuard CancelledAction GestureDispatchGuard FrameCircuit VirtualFrameQueue VirtualResolutionPolicy VirtualLoadPolicy VirtualPreparation VirtualRunActivation VirtualDisplayPolicy VirtualTarget DisplayLane ProgressWatchdog BancAutonomy BancKnowHow StableTransition StaleCompletion; do
 java -cp build/shrunk.jar:build/test-classes jp.tapmarker.${test}Test
done
python3 tests/adb_starter_test.py
