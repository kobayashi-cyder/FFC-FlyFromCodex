# BANCv888 — Weight-space programming v1

## Scope and evidence

This feature extends the existing `banc888-apk-ai-compat-stage1` Android WebView runtime. It enables **explicit, deterministic numerical programs over every directed edge present in the loaded graph**.

It does **not** synthesize missing biological connections, infer neurotransmitter signs, create language-model knowledge, demonstrate general intelligence, or prove brain-wide neuron-level coverage. The currently embedded Figure 3g circuit has **six display-name aggregate nodes and sixteen directed edges**. Its embedded neuron table contains more root IDs, but its edge graph remains aggregate. Full root-to-root imports need independently verified source edges and engineering input/output bindings.

## Program format

Inspect the active graph from the WebView context:

```js
const meta = FFC_PROXY_AGENT.weightSpaceRead({lane:'A',start:0,limit:64});
console.log(meta.edgeCount, meta.rows);
```

Supply `graphIdentity` from the returned value. The exact identity locks a program to the same graph nodes, edges, provenance, routing and tool bindings. It is a comparison string, **not a cryptographic authenticity check**.

```js
const program = {
  schema: 1,
  graphIdentity: meta.graphIdentity,
  ops: [
    {op: 'set', edges: [0,1], value: 1.1},
    {op: 'scale', edges: 'all', value: 0.95}
  ]
};
const preview = FFC_PROXY_AGENT.weightSpaceApply(program,{lane:'A',preview:true});
const commit = FFC_PROXY_AGENT.weightSpaceApply(program,{lane:'A'});
```

Supported operations: `set`, `add`, `scale`, `reset`, `ablate`. `edges` is `'all'` or a list of zero-based edge indices. `reset` restores 1; `ablate` sets the functional gain to 0. Values must be finite; effective gains must stay in `[0,2]`. At most 4096 operations are accepted per transaction; `'all'` covers the full topology regardless of edge count. Programs validate before commit; any failed operation leaves all gains unchanged. Only up to 32 changes are returned in the summary to bound output.

The graph's topology, base edge weights, and transmitter metadata remain unchanged. All modifications are **engineered gain factors**. The result is not equivalent to learning biological meaning or acquiring general intelligence.

## Android integration

`FFC_PROXY_AGENT.weightSpaceRead` and `weightSpaceApply` are available from the APK WebView. The updated `ConnectomeMultiplexer` keeps gains isolated per logical thread, serializes modified gains in the existing checkpoint, and restores only when graph identity matches. Modifying a lane with pending learning credit is rejected until the credit is consumed/discarded. Preview never persists. Unsupported commands return `ok:false`, not a successful answer.

The old `reinforce` clamp of `0.75..1.25` still applies to ordinary learning. Explicitly programmed out-of-band gain values are retained rather than silently projected back into that range. Persistent zeros are kept as lesions.

## Validation and outstanding work

Run `node tests/weight_space.test.cjs` and the existing connectome, compatibility and manifest tests. Confirm the signed debug APK from the Android Actions build before treating this as installable.

This milestone provides **programmability**, not a validated general-purpose AI. Next validation gates: verified root-to-root dataset import; benchmark on held-out tasks; topology shuffle / ablation control; latency, memory and battery on an Android device; then a safe program editor UI and versioned deployment pipeline. Any claim of general-purpose AI requires passing those gates independently.
