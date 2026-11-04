# Weight-Space Lab — Android APK proof of concept

This is a **standalone, offline APK demo**, NOT an update to the existing MarkerTap APK. It does not request accessibility or screen-recording permissions, inspect other apps, tap the screen on behalf of the user, or interact with ads. It is an interactive demonstration of the exact synthetic 8-input → 5-ReLU → 4-action model.

- Start with the repaired model, editing only W2[CLAIM, stale_recovery_evidence] from -12.0 to 0.0.
- Toggle to original weight (-12.0) and back from the Android screen to reproduce how stale recovery evidence suppresses a claim.
- Toggle eight synthetic observations, see WAIT / CLAIM / RECOVER / RESUME and all activation scores.
- Automated tests confirm 15/16 expected predictions in the original model and 16/16 in the edited model; all 256 possible binary input combinations pass the stated safety invariants.
- Build from source with GitHub Actions workflow weight-space-demo-apk.yml. The action creates a self-contained APK with an **ephemeral test signing key** and checks APK signatures and alignment.

**Limitations:** Synthetic inputs are not screenshot recognition. No MarkerTap production code has been altered. Android installation, actual touch injection and rewards are unverified. The test APK is a different Android package (jp.tapmarker.weightspace) and cannot replace jp.tapmarker.lite. Do not install it over existing MarkerTap; they are separate apps.

The build key changes on each workflow run; to replace this test app with a new build, it may need to be uninstalled first. It does not store user data.
