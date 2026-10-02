# Inter-chat handoff: Fly Agent ToolBus v0.1

Date: 2026-09-30

## Added

- `tools/fly_agent_toolbus.py`
- `tests/test_fly_agent_toolbus.py`
- `docs/FLY_AGENT_TOOLBUS.md`
- this handoff note

## Intentionally unchanged

- BANC888 v3 datasets and provenance
- existing O0/O1/O2/O3 and CPF work
- `tools/banc888_domain_pack.py`
- `tests/test_banc888_domain_pack.py`
- current `docs/INTERCHAT_HANDOFF.md`
- APK branches/builds

## Compatibility contract

The fly/connectome remains the decision source. Existing O1′/shortcut output can be adapted to the ToolBus candidate JSON packet without modifying its underlying inference logic. `bridge.propose` is intentionally non-executing until a browser/native adapter is deliberately attached later.

## Tests

Reference tests cover deterministic candidate selection, threshold rejection, capability blocking, safe math, memory, idempotency, dry-run, bounded graph traversal, bridge proposal behavior, and manifest stability.

## Next safe extension area

Add a single-HTML JavaScript adapter implementing the same JSON contract. Do not replace existing BANC888 HTML; load or paste the adapter as an upper-layer module, then compare against the currently active separate-chat HTML branch before any integration.
