# MarkerTap12 — dedicated Android 12 line

Status: implementation branch; build and device validation are not yet confirmed.

## Product profile

- Target device: FCNT arrows F-52A / Android 12
- targetSdkVersion: 31; compile/build tools remain API 35
- Independent application ID: `jp.tapmarker12.lite`
- versionName: `12.0.0`; versionCode: `1200`
- Existing MarkerTap (`jp.tapmarker.lite`) remains unchanged and should be able to coexist.
- The original v1.143 signing key is not in this repository; this line does not claim to update the installed MarkerTap app.

## Implementation in this branch

- `restore_project.py` restores the reviewed v1.143 source into a clean destination, applies a dedicated package/version/target-SDK profile, and adds per-gesture diagnostic traces.
- `marker_tap12_audit.py` checks the application ID, Android 12 target SDK, version, exported declarations, screenshot capability, and diagnostic hooks.
- `build_ci.py` adapts the existing host regression/build pipeline to the MarkerTap12 identity.
- `.github/workflows/markertap12-android12.yml` runs the source audit and build pipeline and uploads validation artifacts.

The restore profile intentionally fails closed when an expected source patch anchor changes. This is preferable to silently producing a mislabeled APK.

## Release gates

- [ ] Source restore and MarkerTap12 audit pass in CI.
- [ ] Host regression tests and Android build pass.
- [ ] APK package name, version, alignment, and signature are inspected.
- [ ] APK installs alongside the existing MarkerTap app on F-52A.
- [ ] Manual marker taps actually reach the target app.
- [ ] Volume-button stop/resume works and cannot leave tapping stuck on.
- [ ] Recovery after popup/ad transitions is verified.
- [ ] Diagnostic ZIP confirms the actual input event path.

A CI build does not prove real game taps work. Do not call this a release until F-52A device tests pass.
