#!/usr/bin/env bash
set -euo pipefail

echo "::group::INSTALL"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.ffc.banc888.fly android.permission.RECORD_AUDIO || true
adb push /tmp/ci-mic-speech.raw /data/local/tmp/ci-mic-speech.raw
adb shell chmod 0644 /data/local/tmp/ci-mic-speech.raw
echo "::endgroup::"

echo "::group::VOICE TEST PREP"
adb logcat -c
rm -f /tmp/device-logcat.txt
adb logcat -v threadtime > /tmp/device-logcat.txt 2>&1 &
DEVICE_LOGCAT_PID=$!
echo "::endgroup::"

echo "::group::VOICE TEST"
set +e
timeout --signal=TERM 45s adb shell am instrument -w -r \
  -e class com.ffc.banc888.fly.BridgeIntegrationTest \
  com.ffc.banc888.fly.test/androidx.test.runner.AndroidJUnitRunner
STATUS=$?
set -e
echo "::endgroup::"

kill "$DEVICE_LOGCAT_PID" 2>/dev/null || true
wait "$DEVICE_LOGCAT_PID" 2>/dev/null || true

echo "=== device crash / speech log ==="
grep -E -n -C 12 'FATAL EXCEPTION|AndroidRuntime|Process: com\.ffc\.banc888\.fly|BANC_VOICE_TEST|SpeechRecognizer|speech-activity-timeout|AssertionError|TransactionTooLargeException' /tmp/device-logcat.txt || true

exit "$STATUS"
