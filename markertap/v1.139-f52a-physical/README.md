# MarkerTap v1.139 — F-52A real-display flow hardening

**Purpose:** establish safe, observable physical-display recovery before virtual multitasking.

The existing v1.138 ad -> external destination -> registered game Intent return remains intact. This patch does **not** bypass advertiser conditions or claim reward credit.

The F-52A specific state bridge acts only for `com.kurashiru.fue.usagi` under the built-in preset. In MAIN or VERIFY, it observes two **distinct**, fresh screenshot sample timestamps over >=1.6 s. If the game still isn't recognized, it stops the tapper, routes to CLASSIFY and requests another real-screen observation. It does not invent a successful recognition. After three failed reclassifications within a 90s episode, it saves `USAGI_HOME_UNRECOGNIZED` and stops safely rather than looping indefinitely. A confirmed main screen resets the budget. Farm remains unchanged.

Known risks/limitations: the strict fixed-header image match may still fail on F-52A; a new screenshot/diagnostic ZIP is required to tune it. Android 12 screenshot callbacks may still stall; this patch does not forcibly use stale screenshots or suppress the existing FRAME_STALE guard. No physical device tests or signed release are claimed. Build outputs are unsigned until they are signed using the established original certificate in a separate secured process.

Acceptance on F-52A: (1) clear startup overlays, (2) main recognized, (3) tapper resumes with a fresh sample, (4) ad interruption, (5) return without duplicate restarts, (6) recheck main; if not possible, collect explicit diagnostic and stop safely.

Implementation caveat: the reconstructed shipping APK contains AutoPilot in its preserved DEX but does not include editable AdaptiveLayout.java. The candidate optimization for an earlier F-52A home-layout match was **not shipped** after CI restore rejected the absent source file. The shipped candidate only changes evidence-gated phase reclassification through the existing patched AutoPilot DEX bridge. Do not claim that image matching speed itself was improved.


## F-52A physical-display diagnostic ZIP

On a progress watchdog event in real-display mode (including F-52A / Android 12),
`ProgressErrors.save` requests a one-shot `AccessibilityService.takeScreenshot(DEFAULT_DISPLAY)`.
The service already declares `canTakeScreenshot=true` in `res/xml/service.xml`.
The screenshot is admitted only if the registered target app is foreground
before the request and again on callback. Conversion to an immutable software
bitmap happens on one bounded background worker. It is sampled as a bounded
JPEG thumbnail by the existing `DiagnosticFrameCapture` queue and is encoded
*before* the matching `DiagnosticJournal.incident` ZIP task, so
`frame-01.jpg` and `frame-01.json` (kind `physical-error`) can be present.
`incident.json.frame_count` must be checked to confirm the bytes actually
landed; successful API callback alone does not prove an image was saved.

If Android denies a screenshot, the app moves away from the requested game,
conversion fails, the queue is full, or 4.5 seconds elapse, a text-only ZIP
is still requested, with an explicit failure reason in `report.txt`.
The accessibility callback and timeout race are one-shot; there is no
periodic full-resolution capture or automatic upload. The existing virtual
display pipeline is unchanged. Password/account/system screens must not be
collected from unrelated foreground apps. Because the screen is captured via
the device default display, MarkerTap overlays may appear in the JPEG.

**Not yet established:** F-52A device screenshot permission at runtime,
actual `frame_count > 0` and `frame-01.jpg` under its OEM Android 12 build,
or an end-to-end F-52A autonomous run. CI only tests source/compilation and
does not replace device evidence.
