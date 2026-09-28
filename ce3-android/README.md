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


## v0.3 MORE / LESS circuit tutorial

The app now contains an interactive tutorial that derives digital logic and arithmetic from the comparator primitives.

- MORE(T): output 1 when weighted SUM >= T
- LESS(T): output 1 when weighted SUM < T
- NOT, OR, NOR, AND, NAND
- XOR as an exact-one range using MORE(1) + LESS(2)
- Half adder and full adder
- Half subtractor and full subtractor
- 1-bit multiplication
- 2-bit multiplication via partial products and half adders
- n-bit multiplication as AND partial-products + shifts + adder tree
- ENABLE gating

Subtraction demonstrates signed weights directly:
D = A - B - Bin, and borrow is LESS(0, D).
