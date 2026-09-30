# Fly + Machine Prosthetic Agent v1

## Definition

This module treats the fly-like connectome as a **fast embodied control kernel**, not as a complete human-level agent. Missing abilities are supplied by a machine/tool prosthetic layer.

    sensory events
        |
        v
    connectome graph kernel -- urgent reflex --> registered device adapter
        |
        +-- delegate / inspect
                 |
                 v
          machine prosthetic layer
          +-- goal queue / priorities
          +-- persistent episodic memory
          +-- planner hook
          +-- capability-gated tool bus
          +-- stop / resume / checkpoint
          +-- screen + speech output adapters

## What is real in v1

- A deterministic weighted graph executor with sensory/interneuron/motor nodes.
- A JSON schema that can be replaced by a real connectome-derived graph later.
- Reflex routing for danger / novelty / human-command channels.
- Persistent goals and JSONL episodic memory.
- Stop, resume and crash-resistant checkpoint restore.
- Tool registry with capability gates; network/device access is blocked by default.
- Safe arithmetic tool and human-output channel.
- Optional planner.solve hook for an external high-level planner.
- Screen/speech fan-out interfaces so the fly agent can communicate with a human.

## What is intentionally not claimed

config/connectome_minimal.json is a synthetic starter graph. It is **not** a biological Drosophila connectome. The runtime is designed so a real graph export can replace it without replacing the machine layer.

## Requirement mapping

| Requirement | v1 mechanism |
|---|---|
| Fly is the acting body | GraphConnectomeKernel handles reflex routing first |
| Machine fills capability gaps | abstract goals delegate to tool/planner layer |
| Human can stop/restart it | pause(), resume(), stop() + checkpoint |
| Long-running work survives restart | persisted goal queue + episodes |
| Agent explains what it is doing | event stream through human-output sink |
| Voice and screen can coexist | MultiOutput with write() / speak() adapters |
| Android/device control | register device.* tools explicitly |
| Safer autonomous execution | capability allowlist blocks network/device by default |

## Run

    python -m fly_agent "計算: 2 + 3 * 4"
    python -m fly_agent

REPL commands: /pause, /resume, /stop, /status, /danger, /novelty, /quit.

## Next integration points

1. Replace the starter graph with a real connectome projection and sensory/motor mappings.
2. Register a planner.solve tool backed by the desired local or remote reasoning engine.
3. Add explicit device.* adapters for Android/ADB, browser, robot, or other actuators.
4. Implement concrete STT/TTS adapters behind SpeechInput / SpeechOutput.
5. Feed tool outcomes back into local plasticity/reward signals instead of the current small reinforcement hook.
