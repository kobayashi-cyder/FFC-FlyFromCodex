#!/usr/bin/env bash
set -euo pipefail

echo "::group::INSTALL"
adb emu avd hostmicon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.ffc.banc888.fly android.permission.RECORD_AUDIO || true
echo "::endgroup::"

echo "::group::VOICE TEST PREP"
pactl info
adb logcat -c
rm -f /tmp/device-logcat.txt /tmp/ci-audio-sync.log
adb logcat -v threadtime > /tmp/device-logcat.txt 2>&1 &
DEVICE_LOGCAT_PID=$!

(
  timeout 45s adb logcat -v brief | while IFS= read -r line; do
    case "$line" in
      *VOICE_PCM_PROBE_ARMED*)
        echo "PCM_PROBE_AUDIO_PLAY"
        sleep 0.15
        paplay --device=ci_mic_sink /tmp/ci-mic-speech.wav
        ;;
      *VOICE_CAPTURE_ARMED*)
        echo "DICTATION_AUDIO_PLAY"
        sleep 0.7
        paplay --device=ci_mic_sink /tmp/ci-mic-speech.wav
        break
        ;;
    esac
  done
) >/tmp/ci-audio-sync.log 2>&1 &
AUDIO_SYNC_PID=$!
echo "::endgroup::"

echo "::group::VOICE TEST"
set +e
timeout --signal=TERM 45s adb shell am instrument -w -r \
  -e class com.ffc.banc888.fly.BridgeIntegrationTest \
  com.ffc.banc888.fly.test/androidx.test.runner.AndroidJUnitRunner
STATUS=$?
set -e
echo "::endgroup::"

kill "$AUDIO_SYNC_PID" 2>/dev/null || true
wait "$AUDIO_SYNC_PID" 2>/dev/null || true
kill "$DEVICE_LOGCAT_PID" 2>/dev/null || true
wait "$DEVICE_LOGCAT_PID" 2>/dev/null || true

echo "=== audio sync log ==="
cat /tmp/ci-audio-sync.log || true
echo "=== device crash / speech log ==="
grep -E -n -C 12 'FATAL EXCEPTION|AndroidRuntime|Process: com\.ffc\.banc888\.fly|BANC_VOICE_TEST|SpeechRecognizer|speech-activity-timeout|TransactionTooLargeException' /tmp/device-logcat.txt || true

exit "$STATUS"
