# MarkerTap12 — Android 12 dedicated line

Status: scaffold / implementation in progress. This directory is the independent Android 12 product line; it is not a claim that an installable APK has already been built or device-tested.

## Product decision

- Product name: MarkerTap12
- Primary device: FCNT arrows F-52A, Android 12
- Android target SDK: 31 (API 31)
- Minimum SDK: retain the proven v1.143 minimum until compatibility tests justify a change
- Independent application ID: `jp.tapmarker12.lite` (must be applied and verified in the final manifest and generated resources before any APK is published)
- Version line: `12.0.x`, with a monotonically increasing Android versionCode starting at 1200
- Existing MarkerTap package `jp.tapmarker.lite` remains untouched and can coexist with MarkerTap12.
- Do not re-sign or replace the existing app as part of this line. The original v1.143 signing key is not present in this repository.

## Implementation order

1. Restore the reviewed v1.143 source into a clean build directory and record its input commit.
2. Apply the Android 12 target-SDK profile and verify all intent-filter components declare `android:exported`; verify the AccessibilityService screenshot capability and required permissions.
3. Apply the new application ID consistently to the manifest, resource generation, build/package scripts, and diagnostics. Do not merely rename the APK file.
4. Add bounded per-gesture diagnostics: request, queue acceptance, completion, rejection, cancellation, timeout, and exception, including event ID, target package, phase, run token, frame age, and stroke count.
5. Run source audit, host regression tests, Android build, APK signature/package/version inspection, and artifact upload in CI.
6. Install alongside MarkerTap on the F-52A and verify marker tapping, volume-button stop/resume, and recovery after popup/ad transitions. Only then label the APK as device-verified.

## Release gates

- [ ] Source restore is deterministic and based on a recorded commit.
- [ ] Android 12 manifest and application-ID audit passes.
- [ ] Host tests pass.
- [ ] APK builds and package/version/signature are inspected.
- [ ] Install alongside the existing app on F-52A succeeds.
- [ ] Actual gesture delivery and stop/resume are verified on device.
- [ ] Diagnostic ZIP confirms the input-event path.

Until these gates are satisfied, this is a development branch, not a release APK.
