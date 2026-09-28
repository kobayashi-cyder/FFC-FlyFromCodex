# FFC Ce3 Automaton + ML Circuit

Scratch-built Android experiment for the FFC/Ce3 line.

## Core idea

The same small vocabulary is used throughout:

- **SUM**: weighted integration
- **COMPARE**: MORE (>= threshold) / LESS (< threshold)
- **ENABLE**: gate a transition or route
- **STATE**: retain history across steps
- **ROUTE**: choose the next state/path
- **ML extension**: make weights trainable while keeping the final MORE/LESS decision explicit

## Included

### Automaton
- Inputs A/B
- ENABLE
- MORE/LESS threshold
- OR / AND / NOR / NOT-A presets
- Persistent states: IDLE -> ACTIVE -> LOCKED
- STEP and RESET
- Live circuit diagram

### ML circuit
- 2-2-1 neural circuit
- AND / OR / NOR / XOR targets
- Back-propagation training
- Explicit final MORE/LESS 0.5 comparator
- Weight/loss display
- Local save/load of learned weights
- No network permission

This is intentionally independent of FlyWire Codex. BANC adapters can be added later as a separate data layer.
