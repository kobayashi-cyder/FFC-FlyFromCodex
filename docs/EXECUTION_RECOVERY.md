# Execution recovery and side-effect semantics

The proxy runtime checkpoints execution at plan-step granularity.

## Before planning

A selected goal is persisted as `RUNNING` before invoking a planner.

## Before each tool

The accepted proposal and the next step index are persisted. Immediately before a tool call the checkpoint also records:

- in-flight step index
- tool name
- whether the tool is marked `side_effect=True`

## After success

The next-step index advances and is checkpointed before the following step starts.

If a later step returns `RETRY`, the goal resumes from that failed step. Earlier successful steps are not replayed and the planner is not called again.

## Crash during a non-side-effect tool

The goal is restored to `QUEUED` at the same step. Re-execution is allowed.

## Crash during a side-effect tool

The outcome is unknowable: the external action might have completed even though the process died before recording success.

The runtime therefore restores the goal as `BLOCKED`, not `QUEUED`. It requires explicit reconciliation/retry before performing the action again.

This is intentional at-most-once bias for Android taps, network writes, messages, file mutations, or any other registered side effect.

Tool adapters must mark real external mutations with `side_effect=True`.
