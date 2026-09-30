# Verified Connectome Projection Input

The runtime deliberately separates **connectivity** from **semantic role assignment**.

## Edge CSV

Required columns:

```csv
src,dst,weight
123,456,37
456,789,81
```

`weight` may be a synapse count or another connection strength. The compiler compresses magnitude logarithmically into the runtime range.

## Role JSON

```json
{
  "sensory": [
    {"id": "123", "channel": "danger"}
  ],
  "motor": [
    {"id": "789", "action": "evade"}
  ],
  "interneuron": [
    {"id": "456"}
  ]
}
```

The IDs must be verified against the source connectome. The compiler will not infer biological meaning from graph position alone.

## Projection

`--max-hops` selects the local neighborhood around mapped sensory/motor nodes. This keeps the runtime graph small while preserving local paths relevant to the mapped behavior.

The output is directly loadable with `GraphConnectomeKernel.from_json()`.
