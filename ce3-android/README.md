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


## v0.4 system tutorial

The interactive MORE/LESS tutorial now continues from logic and arithmetic into a minimal computing system:

- signed compare: LT / GE / EQ from X-Y
- shifter
- incrementer
- 2-bit ALU with ADD / SUB / AND / OR
- SR latch
- 2-bit register
- 2-bit counter
- repeated-subtraction divider controlled by MORE/LESS
- finite-state automaton with ENABLE / RESET / STEP
- minimal accumulator CPU datapath

The tutorial keeps the core decomposition explicit:
weighted SUM -> MORE/LESS -> ENABLE/ROUTE -> STATE.


## v0.5 connectome-inspired transfer system

A new interactive **CONNECTOME SYSTEM** screen turns reusable connectome principles into an executable control architecture.

Pipeline:

sensor inputs -> weighted integration -> MORE threshold -> module gates -> winner-take-all competition -> recurrent state -> behavior output

A long-range supervisor can bias SEEK or AVOID without replacing local control. Three local modules (APPROACH / AVOID / HOLD) compete after thresholding. Recurrent memory retains recent winners across steps. A simple reward-modulated engineering learner can change the winning module's input weights.

Biological inspiration kept explicit:
- sparse weighted integration
- excitation / inhibition through signed weights
- thresholding
- local modules
- gating
- recurrence
- competition / winner selection
- long-range modulation

Engineering abstractions kept separate:
- explicit MORE/LESS notation
- three-module WTA implementation
- numeric supervisor bias
- simple reward update rule

This is a transfer architecture, not a claim that the fly brain literally uses these exact equations.


## v0.7 intuitive operation UI

The basic circuit screen was reorganized around one guided sequence:

1. choose a circuit,
2. toggle A/B,
3. read the same result as diagram + binary output + plain-language explanation,
4. press STEP to store the result as temporal state.

Redundancy is intentional. The same state is shown visually, numerically, and in words so a first-time user does not need to infer the meaning of MORE/LESS from the diagram alone.

Preset buttons now explain their condition:
OR = either input, AND = both, NOR = both zero, NAND = anything except 11, NOT A = inversion.

STATE behavior is now reachable and consistent for every preset:
IDLE -> ACTIVE -> LOCKED on consecutive true decisions, and reverses on consecutive false decisions. BLOCK freezes only the state transition while leaving the logic result visible.

The launcher icon is a single white **F** on the FFC navy field.
