# MarkerTap 1.136 virtual-frame guard (2026-10-24)

Branch: `markertap/virtual-frame-v1.136`, based on `markertap/hardening-v1.135`.

Incident input: 2026-10-24 04:12:06 JST, Android 17 Pixel 10, `STATE_STALL`
in `usagi-default-verify`. Two virtual displays (#16/#17) both awaited new
frames. Last frame on #16 was 939 ms old, analysis error said `analysis exceeded
frame freshness deadline`, no useful home observation. The monitor's 121,118 ms
unverified-progress interval and 2,677 ms phase-entry age were different clocks.

Changes to the *restored shipping Java sources*:
- Keep the physical-screen input engine unchanged; no gesture target changes.
- Admit an early ImageReader decode when the stored frame is at least 750 ms
  old and the particular display is not currently analyzing. Preserve the
  normal adaptive decode cadence and the 1500 ms recognition validity limit.
- Treat an active display analysis lane as contention, not instant failure
  every 120 ms; request an updated frame and retry through the existing
  bounded 250 ms path.
- For current-run failures, record the throwable and request a new capture
  before retry; stale callbacks cannot alter a new display generation.
- Distinguish the monitor's unverified-progress interval from the current
  state-entry age in the `STATE_STALL` diagnostic text.
- Add `FrameFreshnessGate`, Java edge-case tests, and restored-source audit.

This does **not** establish that the app completes games or earns rewards.
Host/CI verification is separate from Pixel 10 real-device testing, which is
still required. Do not treat release as verified until signed APK certificate,
artifact download, capture/input path and long-run stability are validated.

Build:
`python3 markertap/v1.136-frameguard/build_ci.py --sdk "$ANDROID_HOME" --jadx "$JADX_ALL_JAR"`

Version identity: 1.136 / versionCode 139. Requires the existing unchanged
`TAPMARKER_KEYSTORE` and signer configuration for an installable signed APK.
