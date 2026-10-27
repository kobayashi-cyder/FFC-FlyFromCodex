# MarkerTap v1.144 — Android 12 / F-52A compatibility candidate

This candidate is based on the reviewed v1.143 source branch and changes the target SDK from 35 to 31 (Android 12), while retaining the API 35 compile/build toolchain. The source restore step verifies the target SDK, explicit exported declarations for components with intent filters, and the accessibility screenshot capability before building.

## Scope
- Package: `jp.tapmarker.lite`
- versionName: `1.144`; versionCode: `147`
- minSdkVersion remains 24.
- targetSdkVersion: 31 (Android 12 compatibility profile).
- Existing tap/state machine logic is retained from v1.143, including fresh-frame gating and safe stop/reclassification behavior.
- Each physical gesture request now records a bounded event trace: event ID, target package, phase, run token, image age, stroke count, and queued/completed/rejected/cancelled/timeout/exception outcome. The latest trace is included in physical incident diagnostics; delayed callbacks cannot overwrite a newer event trace.

## Build and verification
Use Java 17, Python 3, Android API 35 / Build Tools 35.0.0, and JADX 1.5.3.

```bash
python3 markertap/v1.143-kaitaku-actions/restore_project.py /tmp/markertap-v1144-source
python3 markertap/v1.143-kaitaku-actions/build_ci.py \
  --sdk /path/to/android-sdk \
  --jadx /path/to/jadx-1.5.3-all.jar \
  --output /path/to/results
```

A build and host tests do not prove that F-52A taps work in the live game. The device acceptance test must confirm: Android 12 installation, accessibility activation, screenshot freshness, actual gesture delivery, volume-button stop/resume, and recovery after an ad or popup.

**Important signing constraint:** the original release signing key is not in the repository. A CI-signed/unsigned candidate is not a drop-in update to the existing signed v1.143 APK unless it uses the same original certificate. Do not uninstall the current app merely to test a differently signed candidate unless its data/settings can be safely recovered. This branch does not claim a signed distributable APK or successful F-52A device test until those are independently verified.
