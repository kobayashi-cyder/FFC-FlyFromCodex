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

## Autonomy scope

MarkerTap12 is intended to become a bounded state-machine controller, not merely a coordinate repeater. The first target is Kurashiru mini-game 「農園うさぎ」, with manual marker mode retained as fallback. The controller must classify only known states, select state-allowed actions, verify the post-tap frame, and pause safely on unknown/stale frames or repeated no-progress. Do not invent game coordinates or claim unknown-screen learning. See [AUTONOMY_CONTRACT.md](AUTONOMY_CONTRACT.md) for the explicit states, diagnostics, and acceptance gates.

## Release gates

- [ ] Source restore and MarkerTap12 audit pass in CI.
- [ ] Host regression tests and Android build pass.
- [ ] APK package name, version, alignment, and signature are inspected.
- [ ] APK installs alongside the existing MarkerTap app on F-52A.
- [ ] Manual marker taps actually reach the target app and produce diagnostics.
- [ ] The state-machine controller passes recorded-fixture tests, including unknown/stale-frame safe stop.
- [ ] At least one Farm Rabbit state transition is verified on F-52A from frame recognition through dispatched tap to a changed frame.
- [ ] Recovery after popup/ad transitions is verified.
- [ ] Diagnostic ZIP confirms the actual input event path.

Volume-button stop/resume is not an independent release gate for this iteration. A reachable emergency stop/cancel path and bounded automatic behavior remain required. A CI build does not prove real game taps or autonomy; do not call this a release until the source/build checks pass and F-52A input behavior is verified.
