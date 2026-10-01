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
timeout --signal=TERM --kill-after=5s 120s adb shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  com.ffc.banc888.fly.test/androidx.test.runner.AndroidJUnitRunner > /tmp/instrumentation-result.txt 2>&1
STATUS=$?
set -e
cat /tmp/instrumentation-result.txt
# adb may exit zero even when instrumentation assertions fail. Require the
# runner's explicit successful test summary as well as a successful process.
if [ "$STATUS" -eq 0 ] && ! grep -Eq '^OK \([1-9][0-9]* tests?\)' /tmp/instrumentation-result.txt; then
  STATUS=1
fi

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
  grep -E -n -C 10 'FATAL EXCEPTION|AndroidRuntime|Process: com\.ffc\.banc888\.fly|BANC_VOICE|BANC888-WebView|chromium|CONSOLE|SpeechRecognizer|RecognitionService|SodaSpeechRecognizer|NetworkSpeechRecognizer|AssertionError|TransactionTooLargeException|TestRunner' /tmp/device-logcat.txt || true
fi

if [ "$STATUS" -eq 0 ] && [ "$TEST_CLASS" = "com.ffc.banc888.fly.VideoExportIntegrationTest" ]; then
  MP4_SAMPLE_PATH=$(adb shell run-as com.ffc.banc888.fly find cache/exports -name '*BANC888_video.mp4' | tr -d '\r' | head -n 1)
  if [ -n "$MP4_SAMPLE_PATH" ]; then
    adb exec-out run-as com.ffc.banc888.fly cat "$MP4_SAMPLE_PATH" > /tmp/BANC888-connectome-sample.mp4
    test -s /tmp/BANC888-connectome-sample.mp4
  fi
fi

exit "$STATUS"
