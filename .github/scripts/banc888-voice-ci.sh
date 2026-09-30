#!/usr/bin/env bash
set -euo pipefail

adb emu avd hostmicon
pactl info
adb logcat -c
rm -f /tmp/device-logcat.txt /tmp/ci-audio-sync.log
adb logcat -v threadtime > /tmp/device-logcat.txt 2>&1 &
DEVICE_LOGCAT_PID=$!

rm -f /tmp/ci-audio-sync.log
(
  timeout 120s adb logcat -v brief | while IFS= read -r line; do
    case "$line" in
      *VOICE_PCM_PROBE_ARMED*)
        sleep 0.15
        paplay --device=ci_mic_sink /tmp/ci-mic-speech.wav
        ;;
      *VOICE_CAPTURE_ARMED*)
        sleep 0.7
        paplay --device=ci_mic_sink /tmp/ci-mic-speech.wav
        break
        ;;
    esac
  done
) >/tmp/ci-audio-sync.log 2>&1 &
AUDIO_SYNC_PID=$!

# Build outside the instrumentation timeout so compile time cannot be
# confused with speech/dictation time.
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest

set +e
timeout --signal=TERM 90s gradle --no-daemon :app:connectedDebugAndroidTest
STATUS=$?
set -e

kill "$AUDIO_SYNC_PID" 2>/dev/null || true
wait "$AUDIO_SYNC_PID" 2>/dev/null || true
kill "$DEVICE_LOGCAT_PID" 2>/dev/null || true
wait "$DEVICE_LOGCAT_PID" 2>/dev/null || true

echo "=== audio sync log ==="
cat /tmp/ci-audio-sync.log || true
echo "=== device crash / speech log ==="
grep -E -n -C 12 'FATAL EXCEPTION|AndroidRuntime|Process: com\.ffc\.banc888\.fly|BANC_VOICE_TEST|SpeechRecognizer|speech-activity-timeout|TransactionTooLargeException' /tmp/device-logcat.txt || true
echo "=== instrumentation XML ==="
find app/build -type f \( -name '*.xml' -o -name '*.txt' \) -path '*androidTest*' -print -exec sh -c 'echo "--- $1"; tail -n 160 "$1"' _ {} \; 2>/dev/null || true

exit "$STATUS"
