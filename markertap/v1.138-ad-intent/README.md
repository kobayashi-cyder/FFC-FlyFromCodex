# MarkerTap v1.138 — observed ad-destination handoff

Scope: **physical screen**. Separates ad destination foreground detection from
the registered game's existing relaunch Intent.

1. If the foreground switches to an external app while a factory ad state is
   active, pause the game's tapper. Wait for at least two observations of the
   **same external package** over at least 1200ms.
2. Only then enter the existing `recoverExternal() -> returnRegistered() ->
   getLaunchIntentForPackage(target)` path. That path retains guards,
   cancellation, retries and recorded intent events.
3. Unknown or System UI windows do not authorize a relaunch. A destination
   change resets the handshake. Returning to the game clears the gate.
4. Unrelated, non-ad recovery keeps existing behavior. The virtual two-lane
   admission graph is unchanged.
5. This patch **does not automatically click arbitrary advertisements**,
   guarantee a credited view, or verify actual delivery on Pixel 10. External
   ad navigation must be triggered by a user tap or an existing,
   explicitly configured control.

Regression: `ExternalAdHandoffTest` asserts browser / Play Store transitions,
duplicate/unknown samples, package swaps, observation gaps, stop/reset and
unrelated external behavior. Physical runtime source audit checks real shipped
AutoPilot integration, not only reference source.

Based on draft v1.137. New versionName 1.138, versionCode 141.
The normal CI artifact is unsigned until a valid private signer is supplied.
