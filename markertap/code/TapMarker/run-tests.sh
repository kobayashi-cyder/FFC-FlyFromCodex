#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
RUNTIME_CLASSES=${TAPMARKER_TEST_RUNTIME:-build/shrunk.jar}
TEST_GROUP=${TAPMARKER_TEST_GROUP:-core}
[[ "$TEST_GROUP" == full || "$TEST_GROUP" == core ]] || { echo "TAPMARKER_TEST_GROUP must be full or core" >&2; exit 1; }
[[ -f "$RUNTIME_CLASSES" ]] || { echo "Build the compact runtime before running tests" >&2; exit 1; }
java -jar toolchain/ecj.jar -nowarn -source 8 -target 8 -encoding UTF-8 -classpath build/classes:libs/bcprov-jdk15to18-1.81.jar -d build/test-classes tests/*.java
for test in BackendRecoveryBudget PointPixels FrameResource RegionCapture RegionCaptureReplay DisplayReplacement DisplayRecovery Incident66Replay RecoveryEpisode DiagnosticArchive ManualIntervention Video6774Replay Video6772Replay RestoreReceipt PopupControlBounds GamePopupEvidence PoipoiMissionVerification RewardReceipt StateStallRecovery CoinSearchBudget LowResolutionGames Video6767Replay VirtualResolutionPolicy ProgressWatchdog VirtualLoadPolicy FrameCircuit Video6759Replay UsagiControlReplay UiCatalog CancelAdInterruption AutomationIncident Video6750Replay RecoveryContinuity VirtualPreview VirtualPreviewReplay FarmAdErrorRecovery Video6745Replay Video6742Replay AdContinuation GameInputRepair VisualLocator LearnedReflow MarkerlessRelocation ConfiguredTargetReplay FarmFeverTap Video6732Replay F52AMarkerless PcPlacementRequest PcRunLease BancSelector BancAutonomy BancKnowHow RecordedAutomatonRepair StableTransition StableChangeDuration MainLoopBranch Disappear StaleCompletion DialogSafety PhaseEngine CenterTap ClearArrows StateMigration RegionalLogic HistoryBranch MissionJourney DailyRoute GameTarget WindowCoordinates MultiWindowLayout VirtualTarget VirtualPreparation DisplayLane WirelessModePolicy VirtualDisplayPolicy FramePixels AutomationRouter VirtualRunActivation VirtualFrameQueue InputTargetRecovery TargetedInput DisplayTaskGuard CancelledAction VirtualPresetProfile VirtualRecoveryReplay AdaptiveLayout ContentViewport Video6677Replay Video6680Replay Video6682Replay Video6687Replay Video6717Replay Video6720Replay Video6722Replay Video6724Replay Video6725Replay PoipoiDeclineScreenshot HomeReturnSequence FarmControlEvidence FarmDialogReplay FarmCloseScreenshot AdStallScreenshot AdExitPolicy AdExitSwitch ContinuousRecovery ObservationLearner RecordedAutomatonGenerator LearnedLayout ObservationRelations AutomatonGraph AutomatonEdits EditHistory AutomatonAudit AutomatonNormalization RecoveryRequest PerformancePolicy FrameRaster TilePixels FrameSampleCache ContinuousTap TapInputPolicy Tapper DiscreteTapPosition DiscreteTapTargets RandomTapTargets GestureDispatchGuard DiscreteTouch F52AContact UpgradeEngine CoinNoAdPolicy CoinCancelScreenshot ContinuousCoinReplay CircuitWorkspace PortablePreset; do
 java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.${test}Test
done
if [[ "$TEST_GROUP" == full ]]; then
 for test in Video6651Replay Video6662Replay Video6666Replay Video6669Replay; do
  java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.${test}Test
 done
fi
java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.CircuitPolicyTest assets/circuit.bin
java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.UsagiPresetTest tests/usagi-replay.bin

java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.FarmPresetTest tests/farm-replay.bin
java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.PairingInfoTest
java -cp "$RUNTIME_CLASSES":build/test-classes jp.tapmarker.CertificateDerTest
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT for the host crypto check}"
bash build-host-crypto.sh
java -Djava.library.path=build/test-native -cp "$RUNTIME_CLASSES":build/test-classes:"$ANDROID_SDK_ROOT/platforms/android-35/android.jar" io.github.muntashirakon.adb.AdbPairingCryptoTest

python3 tests/adb_starter_test.py
