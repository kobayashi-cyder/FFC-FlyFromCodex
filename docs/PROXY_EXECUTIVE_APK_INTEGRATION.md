# BANC888 v5.6 Proxy Executive Integration

Integrates agent/fly-prosthetic-v1 semantics into the Android/WebView specialist-tool runtime.

Runtime path: ThreadRouter -> Fly Proxy Executive -> proposal validation -> optional IR patch -> BodyArbiter -> specialist ToolBus -> observation/checkpoint.

Key guarantees:
- planner/classifier output is a proposal, never direct authority
- network/device capabilities are deny-by-default in the APK proxy policy
- unknown or blocked tools cannot be marked DONE
- validation failures are not reported as successful completion
- RUNNING goals restore as QUEUED from browser checkpoint
- primary/backup browser checkpoints mirror the Python AtomicCheckpointStore semantics
- equal-priority thread queues rotate away from the last-served thread when another has work
- shared microphone/speaker/image-generator/artifact/compute resources are leased by thread
- IR patch scopes: micro=1 change, meso=4, macro=16; IR kind is protected
- natural-language revisions patch the prior thread artifact instead of rebuilding context blindly

The Python Fly Proxy v2 reference runtime and its regression tests are also present in this branch.