# MarkerTap12 autonomy contract — Farm Rabbit first

Status: design/acceptance contract. This document is not evidence that the runtime implementation or APK already satisfies these requirements.

## Goal

After the Android 12 tap/input path is proven, evolve MarkerTap12 from a coordinate repeater into a bounded, observable state-machine controller. First target: Kurashiru mini-game 「農園うさぎ」 (also previously referred to as 開拓うさぎ in project notes). Preserve manual marker mode as a fallback and keep the existing MarkerTap app unchanged.

## Control loop

1. Capture a fresh frame and record capture time, target package/window, display geometry, and run token.
2. Classify the frame into a known state using explicit evidence: visible text/controls, image templates/features, and transition context. Never treat an unknown frame as a known game state.
3. Select only an action allowed for that state. Every action has a timeout, retry limit, and a precondition.
4. Submit the gesture through the existing guarded input queue. Record request, queue acceptance, completion/cancel/timeout, and exception separately.
5. Capture a new frame and verify the expected transition. A successful Android gesture callback alone is not proof that the game registered the tap.
6. If the expected transition is absent, retry only within a bounded policy. Repeated no-progress, stale frames, unexpected app/window, ad/popup, or a low-confidence classification must pause the automaton and record why.
7. Stop safely on an unknown state, stale capture, exceeded retry budget, lost accessibility/capture capability, or explicit stop. Never run an unbounded tap loop.

## Initial state model

- `APP_NOT_READY`: target app/window not confirmed; no game taps.
- `START_SCREEN`: start control is visually/accessibly confirmed; tap once, then verify.
- `GAME_ACTIVE`: game surface is confirmed and fresh; run only the action policy backed by observed reference material.
- `REWARD_OR_CHEST`: reward/chest control is confirmed; tap once and verify the resulting state.
- `AD_OR_POPUP`: pause game actions; wait for a known dismissal/return condition and re-classify.
- `RESULT_SCREEN`: verify result and use only a known continue/restart action.
- `RECOVERING`: bounded fresh-frame reacquisition; return to a known state or stop.
- `UNKNOWN_SAFE_STOP`: no automatic gesture; preserve a diagnostic snapshot/report.

Transitions must be explicit and unit-tested. A timer alone must not promote the app into `GAME_ACTIVE`.

## Farm Rabbit policy data

Do not invent coordinates or assume every screen has the same layout. The first playable policy must be grounded in the supplied/reference recording and verified screenshots: for each observed state, document the identifying evidence, eligible action(s), expected next state, timeout, and retry budget. If the recording does not establish a control or transition, leave that action unsupported rather than guessing.

## Diagnostics required

Each state decision/action record should include a monotonically increasing event ID, run token, target package/window, state and confidence/evidence, frame age, chosen action, gesture queue result, elapsed time, expected transition, observed transition, retry count, and stop/recovery reason. Include these records in the incident ZIP without logging private screen contents beyond what is needed to diagnose the game.

## Acceptance gates

1. Source restore and audit pass.
2. Android build succeeds; APK package/version/alignment/signature are inspected.
3. APK installs alongside legacy MarkerTap on F-52A / Android 12.
4. Manual marker mode produces an input event and diagnostic trace.
5. The autonomous controller can distinguish the states above on recorded fixtures, with unknown/ambiguous fixtures always taking the safe-stop path.
6. On-device Farm Rabbit test demonstrates at least one verified state transition from frame recognition through tap dispatch to a changed post-action frame.
7. Repeated no-progress and stale-frame fixtures stop within the configured retry/deadline budget.

Volume-button stop/resume is not an independent release gate for this iteration. A reachable emergency stop / cancel path and bounded automatic behavior remain required. Host tests and a generated APK do not, by themselves, prove real-game autonomy; report device results separately.
