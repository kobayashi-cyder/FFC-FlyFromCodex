# MarkerTap v1.137 — connected graph + two virtual lanes

This candidate layers on the still-unmerged v1.136 virtual-frame branch; it is
not a verified APK or a claim of biological neural simulation.

## Production changes
- Shared sparse event graph `ConnectomeMultiplexer` with **separate per-app,
  per-display frame watermarks** and fail-closed stop/restart isolation.
- Integration into the **real, reconstructed** `SplitPilot.nextVirtual` path.
  A virtual lane is admitted only on a fresh, unconsumed frame. Duplicate,
  expired or future-dated captures request a refresh with bounded 150–350ms
  wakeups rather than resubmitting an expensive analysis.
- Existing two-worker `VirtualWindows` frame analysis and parallel per-slot
  scheduling remain active; no physical-screen gesture code or screenshot
  API changes. This is **event-driven observation admission**, not automatic
  full-frame neural perception or verified throughput improvement.
- Graph publication signals after a fresh sampled frame enters `applySamples`.
- A start/stop reset makes stale events incapable of scheduling another run.
- Android host test and audited-source guards cover two lanes, image freshness,
  duplicate suppression, display recreation and restart isolation.

## Limits
v1.136 CI failed during restore before Android compilation. v1.137 includes
a CI log surfacing fix to locate that inherited issue, but no Android build,
signing or physical Pixel 10 validation is asserted until its workflow passes.
Safety restrictions, current recognition thresholds and signed certificate
requirements are unchanged. Do not distribute unsigned output as an update.

Build: `python3 markertap/v1.137-connectome/build_ci.py --sdk "$ANDROID_HOME" --jadx "$JADX_ALL_JAR"`.
Version: 1.137 / versionCode 140.
