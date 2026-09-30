# Fly Proxy Agent v2 — Connectome Executive + Machine Prostheses

## 1. Definition

Fly Proxy Agent keeps the fly/connectome side as the **executive**. The machine does not become the hidden owner of the agent; it supplies abilities the connectome lacks.

```text
Human intent / microphone / environment
                |
                v
        ConnectomeExecutive
        |      |       |
        |      |       +--> reflex --> BodyArbiter --> device adapter
        |      |
        |      +--> ThreadRouter / VoiceRouter
        |
        +--> PlannerAdapter (proposal only)
                     |
                     v
             executive validation
                     |
                     v
                  ToolBus
             /       |       \
       memory     generation   device/network
          |             |            |
          +------ Observer <----------+
                     |
                     v
                MemoryFabric
```

The critical rule is: **planner output is only a proposal**. `ConnectomeExecutive.evaluate_proposal()` validates every step and capability before anything runs.

## 2. Implemented components

### ConnectomeExecutive

- Routes sensory activity through `GraphConnectomeKernel`.
- High-confidence biological-style motor output stays on the reflex path.
- Human/high-level work is delegated to machine cognition.
- Reviews planner proposals before tool execution.
- Rejects unknown tools and tools whose capability is not allowed.
- Receives local reinforcement from observed outcomes.

### ThreadRouter

- Creates Excel-style labels: `A ... Z, AA, AB ...`.
- Stores independent thread context and listener state.
- Supports explicit routing (`B: ...`, `[B] ...`).
- Otherwise routes by context-token overlap and current activity.
- Persists thread state in the checkpoint.

### VoiceRouter

- Treats the microphone as one physical input stream.
- Each logical thread can enable/disable its listener.
- Explicit thread routing overrides context routing.
- Disabled listeners do not compete for implicit voice routing.

### BodyArbiter

- Serializes shared resources such as Android UI, browser, speaker, microphone, camera, or robot body.
- A logical thread can re-enter a resource it already owns.
- Another thread gets a retryable `resource busy` outcome instead of corrupting the current action.
- Tool specs declare the resource they require.

### ToolBus

- Every tool has a capability and optional shared-resource requirement.
- Capability policy is checked before execution.
- An explicitly empty policy means deny-all.
- Device/network are not enabled by default.
- Unknown tools are `UNSUPPORTED`, blocked tools are `BLOCKED`, resource contention is `RETRY`.

### PlannerAdapter

- `RulePlanner` handles deterministic local commands such as calculation and human output.
- `MachinePlannerAdapter` can call a registered `planner.propose` tool.
- External planners return structured steps; they never directly execute tools.
- A planner cannot smuggle a blocked device/network step through the executive.

### MemoryFabric

- Append-only JSONL episodic memory.
- Global and per-thread recall.
- Thread context remains separate even when many tasks coexist.
- Writes are flushed and fsynced before returning.

### Observer

- Records every tool result as an observation.
- Stores tool, step, goal, status, output, and error.
- Feeds small local reinforcement signals back to the connectome kernel.

### CheckpointStore

- State is written to a temporary file and atomically replaced.
- Last-known-good checkpoint is kept as a backup.
- If the primary JSON is corrupt, startup falls back to the backup.
- A goal that was `RUNNING` during a crash is restored as `QUEUED`, never falsely marked complete.

## 3. Goal state semantics

```text
QUEUED -> RUNNING -> DONE
                 -> QUEUED     (retryable failure)
                 -> BLOCKED    (unsupported / capability denied / invalid proposal)
                 -> FAILED     (retry budget exhausted)
                 -> CANCELLED  (human/system cancellation)
```

Unsupported work is intentionally **not** reported as completed.

## 4. Thread/body model

Logical cognition is multi-threaded; physical action is arbitrated.

```text
Thread A ----\
Thread B -----+--> BodyArbiter --> Android
Thread C ----/                  --> Browser
                                --> Speaker
```

This allows A/B/C to retain separate goals and memory while preventing simultaneous taps or conflicting device ownership.

## 5. Connectome replacement boundary

`config/connectome_minimal.json` is still a synthetic starter graph. It is not presented as biological Drosophila data.

A projection compiler is now provided:

```bash
python tools/connectome_project.py \
  --edges verified_edges.csv \
  --roles verified_roles.json \
  --out config/connectome_verified.json
```

`verified_edges.csv` must contain `src,dst,weight`.

The role map explicitly identifies verified biological node IDs as sensory/motor/interneuron roles. The compiler does **not** guess which biological neuron means “danger”, “evade”, etc. That mapping must come from a cited/verified dataset or an explicit project decision.

## 6. Console

```bash
python -m fly_agent
```

Commands:

- `/new [title]`
- `/listen LABEL on|off`
- `/pause`
- `/resume`
- `/stop`
- `/status`
- `/danger`
- `/novelty`
- `/retry GOAL_ID`
- `/cancel GOAL_ID`
- `/quit`

Normal text is treated as voice-like human input and routed to a listening thread.

## 7. Tool adapter contract

A device or external capability is added by registering a `ToolSpec`.

```python
bus.register(ToolSpec(
    "device.tap",
    tap_impl,
    Capability.DEVICE,
    resource="android",
    side_effect=True,
))
```

The body resource and capability policy are independent checks. A registered adapter is not executable unless its capability is granted.

## 8. Current verification

The v2 test suite covers connectome reflex/delegation, thread routing, voice listeners, pause/resume, checkpoint persistence, unsupported goal blocking, deny-all policy, device capability blocking, body resource exclusion/re-entry, resource-contention retry, backup recovery, interrupted-goal recovery, cross-thread scheduling, planner proposal execution/rejection, and connectome projection.

## 9. Boundary that remains external

This repository now contains the complete orchestration/runtime boundary. Platform-specific implementations still have to be connected where the real hardware/service exists: Android Accessibility/ADB, STT/TTS engine, browser automation, image generator, code model, or network planner. Those are tools/actuators, not replacements for the fly executive.
