# CIFA v1.161.1 — floating minimize repair (independent test APK)

Based on v1.161 CIFA source candidate; **not a production MarkerTap update**.

## Diagnosis
The v1.161 panel implemented `compactPanel()`, which only folds detailed controls; it did not create a floating minimize/restore handle. Its `AutoPilot.hide()` re-showed the entire panel, preventing a durable minimized mode.

## Implemented
- An always-accessible `最小化` button in the CIFA overlay's quick controls.
- Hides the full panel and shows a 76×56 dp CIFA floating control; tap to restore, drag to reposition.
- Saves floating x/y separately from expanded panel position.
- `AutoPilot.hide()` now respects minimized state, including after screen/foreground changes.
- Hides both overlays on screen lock and during import mode; keeps current minimization state on unlock.
- Removes the floating handle when service stops; clamps coordinates after display rotation.
- Does **not** stop automation, reset the automaton, credit rewards, or bypass gesture safety checks.

## Automated validation
GitHub Actions: https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41849272324
- ZIP integrity and SHA256 verified: `96110cfab3224770e89e65bed651f098779a6f1db22c89b1b26cbf35a557eeb8`
- Android APK Signature Scheme v2 and v3: passed.
- 24 floating-panel geometry/visibility checks: passed.
- Existing 2011 dense selector, 80 CIFA autonomy, 23 cross-game knowledge, 5 migration, 33 recovery assertions: passed.
- **Pixel 10 real device touch, dragging, minimization, virtual dual-screen operation: NOT TESTED.**

## Installation caveat
Package `jp.tapmarker.cifa.lab` and vCode2, versionName 1.161.1-cifa-lab. Signed by a **new ephemeral CI key**, not by the key used for previous CIFA lab APK. Therefore Android will not accept it as an in-place update of the previously installed CIFA lab even with increasing versionCode. **Uninstall only the older CIFA Lab** before installing this revision; this deletes that lab app's local data. Do NOT uninstall the main `jp.tapmarker.lite` MarkerTap app. The main app remains unchanged.

This lab APK is built from an archived source mirror and not proven DEX-parity-compatible with v1.159 production.