# Fly Agent ToolBus v0.1

## Purpose

The fly/connectome is the computing subject. A human is the principal; the fly is the proxy. ToolBus is only the actuator/observation boundary between the fly's decision and external capabilities.

This is an additive layer. It does **not** replace or rewrite BANC888 O0/O1/O2/O3, the v3 data lineage, the domain-pack pipeline, or another chat's active implementation.

## Control loop

Human message / goal
→ translation / semantic layer
→ BANC888 / connectome activation
→ O1′-style candidate actions
→ Fly Agent ToolBus
→ local tool or non-executing bridge proposal
→ normalized observation
→ connectome feedback / next activation
→ Human-facing response

The important rule is that ToolBus does not invent the intention. The fly supplies candidate actions and their excitation/inhibition. ToolBus selects deterministically among eligible candidates and executes only a declared tool.

## Implemented tools

| Tool | Capability | Effect | Purpose |
|---|---|---|---|
| `compute.math` | `compute` | none | bounded arithmetic |
| `memory.put` | `memory.write` | local | session memory write |
| `memory.get` | `memory.read` | none | session memory read |
| `memory.delete` | `memory.write` | local | session memory delete |
| `graph.neighbors` | `graph.read` | none | 1-hop connectome lookup |
| `graph.hops` | `graph.read` | none | bounded 0..3-hop traversal |
| `bridge.propose` | `bridge.propose` | proposal only | describe a future browser/OS/native action without executing it |
| `system.manifest` | `compute` | none | enumerate tool contracts |

No default tool executes network requests, subprocesses, arbitrary code, or OS control. This is deliberate while APK/native control is deferred. Later browser/native adapters can consume `bridge.propose` behind explicit permissions without changing the connectome-side contract.

## Candidate packet

```json
{
  "tool": "compute.math",
  "args": {"expression": "2+3*4"},
  "excitation": 0.91,
  "inhibition": 0.08,
  "confidence": 0.95,
  "source": "O1-prime-shortcut"
}
```

Activation is `(excitation - inhibition) * confidence`. Selection is deterministic: activation descending, confidence descending, tool name ascending.

## Why this matches the proxy-agent goal

The useful agent is not just a chat layer. It needs a closed loop: the person states a goal, the fly produces action candidates, ToolBus gates/executes an allowed capability, the result returns as an observation, and the fly can alter its next action from that observation.

This preserves the fly as the subject while making its outputs operational.

## BANC888 integration position

Do not edit the immutable BANC v3 source datasets or overwrite existing O0/O1/O2/O3 artifacts. Integrate by adapting the existing O1′/shortcut output into ToolBus candidate packets. O2/O3 image/video generation can later be exposed as tools, but their current implementations remain untouched.

A browser implementation can mirror the same tool names and packet format inside the existing single-HTML runtime. Because the protocol is JSON-only and deterministic, the Python reference implementation is also a test oracle for that port.

## Collaboration / non-collision rules

- New work lives on an isolated branch.
- Existing files are not rewritten by this v0.1 implementation.
- `tools/banc888_domain_pack.py`, its tests, and current domain-pack behavior are untouched.
- No version-number claim is made for existing BANC888 HTML artifacts.
- Merge/integration should happen only after comparing the active separate-chat branch and recording provenance.
