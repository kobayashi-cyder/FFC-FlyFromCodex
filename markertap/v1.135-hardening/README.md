# MarkerTap v1.135 hardening candidate (2026-10-23)

Source branch: `markertap/hardening-v1.135`; parent: `markertap/autonomy-v1.134`.

The previous version accepted fresh screenshots but could repeatedly fail to
publish an observation after the 1500ms freshness check. The 17:11:11 incident
has window #9 / target game focused, recent capture, no completed usable
observation, and 20 supervision failures. The 15:25:57 incident included zero
virtual screens but a running old automaton, and its ZIP held no saved images.

Fixes:
- Move `DisplayRecovery` episode ownership to `VirtualWindows.Window`.
  A SplitPilot stop/start no longer resets the one-rebuild-per-episode budget.
  This preserves the separate other-screen lane and input isolation.
- Separate `DISPLAY_MISSING` from `FRAME_STALE` (5 second watchdog grace).
  Use a persistent virtual diagnostic key across display-id changes.
- Classify a null capture source explicitly rather than delivering a null
  exception to the retry pathway.
- Drain and close ImageReader frames under retirement backpressure instead of
  returning before acquireLatestImage. Keep deliberate rebuilds out of false
  stale-frame watchdog alarms.
- Record asynchronous analysis failures and their timestamp in the incident
  report (without extending frame freshness or reducing thresholds).
- Retain up to eight previously captured frame records for <=60s across a
  display-id/run change; their original display metadata is retained.
- Add three regression suites for missing frames, missing displays and
  reuse of the display-owned recovery budget; the restore step also audits
  11 structural source invariants before compilation.

Build: `python3 markertap/v1.135-hardening/build_ci.py --sdk "$ANDROID_HOME" --jadx "$JADX_ALL_JAR"`.

Safety and limits:
- The 2026-10-23 17:11 report was supplied as text, not a photo-bearing ZIP.
  No visual root cause is established.
- No auto-UI clicking of unknown dialogs, no lowered image-recognition
  thresholds, no display resolution changes, no automatic unapproved
  accessibility permission bypass.
- Host tests and Android build must pass CI. No Pixel 10 replay, daily rewards,
  Android 17 stability, ANR-free run, or long unattended run is claimed.
- Existing signing certificate must remain unchanged. This branch never
  distributes keystores. CI without a provided keystore emits unsigned output.
