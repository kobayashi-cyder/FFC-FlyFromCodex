# MarkerTap v1.159 screenshot pacing trial

Date: 2026-11-04. EXPERIMENTAL, not Android 17 / Pixel 10 accepted.

## Diagnosed
Physical kaitaku-default-check: no live recognition frames, FRAME_STALE after 15,011ms and Android screenshot error 3 (interval too short). AutoPilot and reward HUD use distinct capture request paths.

## Mirrored in locally signed APK
- TransitionTiming.remaining interval: 350 -> 600ms.
- RewardV158Capture.capture HUD interval: 1200 -> 1600ms.
- Package: jp.tapmarker.lite, versionName 1.159, versionCode 162.
- Signed APK sha256: d45f808edcc7b53967a45f914e6adda3b6f8c8d382226165390b7b1bf95fd3a5.
- Owner certificate sha256: 7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1.
- DEX class count remains 1637 and all original assets are preserved. Original minimization and gesture input code unchanged.
- APK v2/v3 signature and content digests verified locally. Signing material is not committed.

## Limitations
- These are pacing mitigations, NOT a shared screenshot scheduler across all producers.
- 1ms tap / 5ms recognition host prototype is NOT integrated into this APK. Its existing tap behavior remains.
- No Android 17/Pixel10 install, 2 virtual displays, point-earning, or long-duration acceptance.
- No APK release asset is uploaded by this PR; do not update current stable release links.

## Rollback
Locally signed code-identical v1.158 rollback APK with versionCode 163 allows app-data-preserving forward-numbered rollback if test build 162 fails. SHA256 abcb7e903f0e354eb23ccf65d174a683b113fe4f48e9287d15d1e0c4f8305a55. Future update versionCodes must be >=164 after rollback.

## Device test
Install over v1.158 without uninstalling. Confirm 1.159 and accessibility service; test 開拓する, minimized floating restore, error 3, FRAME_STALE, receipt detection, virtual 2-screen behavior and 30-minute continuous run. Save diagnostic ZIP if any fails; do not publish as stable without passing.
