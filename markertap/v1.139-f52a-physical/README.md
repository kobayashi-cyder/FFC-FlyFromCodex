# MarkerTap v1.139 — F-52A real-display flow hardening

**Purpose:** establish safe, observable physical-display recovery before virtual multitasking.

The existing v1.138 ad -> external destination -> registered game Intent return remains intact. This patch does **not** bypass advertiser conditions or claim reward credit.

The F-52A specific state bridge acts only for `com.kurashiru.fue.usagi` under the built-in preset. In MAIN or VERIFY, it observes two **distinct**, fresh screenshot sample timestamps over >=1.6 s. If the game still isn't recognized, it stops the tapper, routes to CLASSIFY and requests another real-screen observation. It does not invent a successful recognition. After three failed reclassifications within a 90s episode, it saves `USAGI_HOME_UNRECOGNIZED` and stops safely rather than looping indefinitely. A confirmed main screen resets the budget. Farm remains unchanged.

Known risks/limitations: the strict fixed-header image match may still fail on F-52A; a new screenshot/diagnostic ZIP is required to tune it. Android 12 screenshot callbacks may still stall; this patch does not forcibly use stale screenshots or suppress the existing FRAME_STALE guard. No physical device tests or signed release are claimed. Build outputs are unsigned until they are signed using the established original certificate in a separate secured process.

Acceptance on F-52A: (1) clear startup overlays, (2) main recognized, (3) tapper resumes with a fresh sample, (4) ad interruption, (5) return without duplicate restarts, (6) recheck main; if not possible, collect explicit diagnostic and stop safely.

Implementation caveat: the reconstructed shipping APK contains AutoPilot in its preserved DEX but does not include editable AdaptiveLayout.java. The candidate optimization for an earlier F-52A home-layout match was **not shipped** after CI restore rejected the absent source file. The shipped candidate only changes evidence-gated phase reclassification through the existing patched AutoPilot DEX bridge. Do not claim that image matching speed itself was improved.
