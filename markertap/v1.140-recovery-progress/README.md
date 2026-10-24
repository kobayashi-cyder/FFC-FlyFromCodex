# MarkerTap v1.140 — bounded physical return recovery

Addresses the Pixel 10 v1.137 incident at 2026-10-25 06:24:15 JST: repeated cancellation of a registered-app return while `farm-default-verify` remains unresolved. Inherits v1.139, including its physical screenshot path.

- The shipping `AutoPilot.verifyRegisteredReturn` DEX method calls `ReturnProgressBridge`. The original remains as the fallback for custom and other automata.
- Farm/usagi built-in physical flows require two distinct images less than 1500ms old, for one unambiguous eligible rule and the same state entry/window, before cancelling a return. A transient candidate or cooldown can wait at most 2500ms per request.
- Three cancellations exhaust that unresolved episode's allowance. Subsequent requests use the existing Intent/restart budgets. Re-entering a state or issuing another request does not replenish the allowance. Two distinct fresh home observations clear it. A still-unresolved episode after 90 seconds produces `RECOVERY_NO_PROGRESS` and stops safely.
- Physical diagnostics use the owning AutoPilot run token, freeze the state observation at detection, label physical/virtual mode, and record screenshot failure status. A physical incident excludes images taken before that incident. Pixel copying/JPEG encoding stay off the main thread on bounded workers.
- Native game/display resolution, image thresholds, input guards, lock protection, virtual frame circuits, and the original signing certificate remain in place.

Build with `python3 markertap/v1.140-recovery-progress/build_ci.py --sdk SDK --jadx JADX_JAR`. Set `TAPMARKER_KEYSTORE` only to the existing private signing key for an update-compatible signed output; never commit the key. The CI output is explicitly unsigned.

Validation includes 25 return-gate regressions, inherited core/host tests and eight incident replays, plus an APK-level audit checking the actual return wrapper, original fallback, episode monitor, physical token access and screenshot path. Pixel 10 / F-52A real-device behavior and long unattended runs require device verification; passing host tests does not establish those outcomes.
