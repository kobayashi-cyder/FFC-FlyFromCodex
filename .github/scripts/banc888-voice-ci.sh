#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="${TEST_CLASS:?TEST_CLASS is required}"
TEST_NAME="${TEST_NAME:-$TEST_CLASS}"

echo "::group::INSTALL"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.ffc.banc888.fly android.permission.RECORD_AUDIO || true
if [ -s /tmp/ci-mic-speech.raw ]; then
  adb push /tmp/ci-mic-speech.raw /data/local/tmp/ci-mic-speech.raw
  adb shell chmod 0644 /data/local/tmp/ci-mic-speech.raw
fi
echo "::endgroup::"

echo "::group::TEST $TEST_NAME"
adb logcat -c
rm -f /tmp/device-logcat.txt
adb logcat -v threadtime > /tmp/device-logcat.txt 2>&1 &
DEVICE_LOGCAT_PID=$!

set +e
timeout --signal=TERM 90s adb shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  com.ffc.banc888.fly.test/androidx.test.runner.AndroidJUnitRunner
STATUS=$?
set -e

kill "$DEVICE_LOGCAT_PID" 2>/dev/null || true
wait "$DEVICE_LOGCAT_PID" 2>/dev/null || true
echo "::endgroup::"

if [ "$STATUS" -eq 0 ]; then
  RESULT="PASS"
else
  RESULT="FAIL ($STATUS)"
fi

echo "=== $TEST_NAME: $RESULT ==="
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '### %s: %s\n' "$TEST_NAME" "$RESULT" >> "$GITHUB_STEP_SUMMARY"
fi

if [ "$STATUS" -ne 0 ]; then
  echo "=== focused device log ==="
  grep -E -n -C 10 'FATAL EXCEPTION|AndroidRuntime|Process: com\.ffc\.banc888\.fly|BANC_VOICE|SpeechRecognizer|RecognitionService|SodaSpeechRecognizer|NetworkSpeechRecognizer|AssertionError|TransactionTooLargeException|TestRunner|chromium|Uncaught|ReferenceError|SyntaxError' /tmp/device-logcat.txt || true
fi

exit "$STATUS"
