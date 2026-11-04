# MarkerTap v1.160 — Weight-Space / Kaitaku diagnostic candidate

**Status:** source-only candidate; production APK is NOT built or installable from this branch without a verified full v1.159 source reconstruction and the same private signing certificate. Do not publish as a stable update yet.

## Grounded observations (2026-11-04 19:01:57 JST)
The user's v1.159 incident identified `kaitaku-default-mission-opening` after opening the daily missions page. It reported matching `mission-opening-mission-page` evidence but held `条件成立を待機`. The foreground was already `com.kurashiru.kaitaku.usagi`, while a prior `physical-return` task remained diagnostically unresolved for about 90 seconds. One captured frame displayed a mission page with a MarkerTap return control overlaid. This is not proof of successful reward claim or of a unique root cause.

## What was actually changed in the mirrored source
- Added `KaitakuMissionReacquire`: **two distinct, fresh, positive mission-page observations** are necessary before a visual-only transition.
- In `AutoPilot.phaseCycle`, this path is restricted to the existing non-customized Kaitaku graph and the `mission-opening` / `verify` states.
- It requires valid foreground/window identity, excludes home/ad/known blocking dialogs and an actively busy physical return process.
- On success, it cancels the old recovery request and re-enters the `missions` state. It **does not issue a tap**, bypass preflight, credit rewards, or resume continuous tapping on a modal.
- Added a Java host regression test (33 assertions, run offline).

## Actual existing custom-connectome.bin investigation
Read from `MarkerTap-v1.159-installfix-vcode164-signed.apk`, SHA-256 `3dd31cd1b68a8862400a824721cfa99fc7c36d1779281113dd279e021c8e80bd`.
- Format CCG1; 1,056 nodes, 1,248 weighted edges, 768 inputs, 96 outputs, six synchronous ticks.
- Zero-weight edges: 0. Duplicate source/destination pairs: 0.
- Lossless time-expanded output-cone schedule: edge updates `7,488 -> 6,720` (-768 / -10.26%), node updates `6,336 -> 4,608` (-1,728 / -27.27%). **No weights changed**.
- Output values were bit-for-bit identical across 256 synthetic fixtures in a synchronous float32 recurrent simulator. This is a structural proof of concept, not a runtime or timing benchmark for the installed APK.
- The optimized schedule must be implemented and tested against the **actual** v1.159 `CustomConnectome` runtime to claim performance improvement; this mirrored source tree does not contain its matching version. No claim of increased accuracy can be made.

## Release blockers
1. Recover full v1.159 source and compare DEX classes/assets with the signed installation APK; the mirrored `TapMarker` tree has a historical `1.123` manifest and is not release-equivalent.
2. Integrate the output-cone kernel in the production connectome runtime, maintaining its activation semantics and frame reset behavior.
3. Reproduce stale recovery versus mission-page from recorded frames, including overlay and loading spinner, verify inputs/receipts across at least three cycles.
4. Build versionCode >= 166, use the original signing key, validate v2/v3 signatures and zip alignment, then run Pixel 10 / Android 17 acceptance with two independent virtual displays.
5. Only after these conditions should README point to a production APK.

No secrets and no unverified signed APK have been committed.