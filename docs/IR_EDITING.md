# Fly-editable Intermediate Representation

The fly executive can now revise intermediate representations directly instead of being limited to selecting finished tool outputs.

## Why this layer exists

Generated code, plans, image scene graphs, UI trees, prompts, document outlines, action sequences, and other internal artifacts should not require complete regeneration whenever one part is wrong.

The IR layer provides four edit scales:

| Scale | Intended use | Examples |
|---|---|---|
| MICRO | scalar/local parameter correction | one coordinate, one threshold, one token/flag |
| LOCAL | a small group of fields/list entries | add an object, delete a step, merge attributes |
| STRUCTURAL | subtree or collection reorganization | replace a scene object set, rewrite a plan section |
| GLOBAL | whole-representation replacement | new global layout/schema/state |

The declared scale is not trusted blindly. The runtime re-estimates the minimum required scale from operation count, operation type, path depth, new payload size, **and the size/type of the existing target being replaced or deleted**. A large existing subtree cannot be erased by replacing it with a tiny scalar while claiming `MICRO`.

## Patch model

Paths use JSON Pointer syntax.

Supported operations:

- `set`: create/overwrite an object field, or overwrite an existing list index
- `replace`: overwrite an existing target
- `delete`: remove an existing object field or list item
- `insert`: insert at a list index
- `append`: append to a target list
- `merge`: shallow-merge an object

Example:

```json
{
  "document_id": "scene-1",
  "scale": "LOCAL",
  "expected_version": 4,
  "reason": "move the focal object closer and add a light",
  "patches": [
    {"op": "replace", "path": "/camera/distance", "value": 2.8},
    {"op": "append", "path": "/lights", "value": {"type": "key", "power": 0.7}}
  ]
}
```

## Transaction rules

All patches in one transaction are applied to a private copy first.

If any path, operation, validator, protection rule, size bound, or version precondition fails, **none** of the changes are committed.

Every successful commit:

- increments the document version
- stores a bounded undo snapshot
- records declared and required edit scale
- records the actual thread that executed the edit
- records author, reason, operation count, and transaction ID
- persists via an atomic checkpoint

A caller can supply `transaction_id` to make retry delivery idempotent. Committed transaction IDs are kept in a durable ledger separate from the bounded undo snapshots, so a delayed retry remains idempotent even after its undo snapshot has aged out.

## Optimistic concurrency

`expected_version` prevents a stale thread from overwriting a newer edit.

GLOBAL edits always require `expected_version`.

This matters when several A/B/C/... threads are reasoning over the same IR at once.

## Protected paths

Documents can declare immutable subtrees:

```python
agent.create_ir(
    "plan",
    plan,
    protected_paths=["/safety/policy", "/identity"],
)
```

A patch is rejected if it targets the protected path, anything below it, or an ancestor replacement that would erase it.

## Preview before commit

`ir.preview` runs the full validation and produces:

- current/next version
- declared/required scale
- operation count
- before/after digest
- resulting byte size
- whether state would actually change

No state is modified.

## Undo

`ir.undo` restores the last committed IR snapshot and increments the version again. Undo history is bounded to prevent unbounded memory growth.

## Fly ownership

The available tools are:

- `ir.create`
- `ir.get`
- `ir.list`
- `ir.preview`
- `ir.patch`
- `ir.undo`
- `ir.history`

They use the dedicated `intermediate_representation` capability.

An external planner can propose an `ir.patch` step, but it still passes through `ConnectomeExecutive` and `ToolBus`. The IR store then independently enforces scale, version, protection, and transaction rules. This creates two checks:

```text
planner proposal
    -> ConnectomeExecutive capability/tool validation
        -> IR transactional validation
            -> commit
```

The fly runtime also exposes direct methods:

- `create_ir(...)`
- `preview_ir_edit(...)`
- `edit_ir(...)`
- `undo_ir(...)`

This lets the fly make anything from a one-value correction to a full representation rewrite while retaining provenance and rollback.
