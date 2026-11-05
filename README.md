# BANC888 Cognition v7.13 (experimental)

This development branch adds a bounded symbolic cognition engine to BANC888 for Android. It can infer explicitly supplied rules, plan multi-step tasks, require connectome output before calling tools, and replan when observed outcomes differ from expected effects. This is **not artificial general intelligence**; the built-in graph is still the six-aggregate-node, sixteen-edge Figure 3g sample.

## Android APK

- [Latest cognition APK builds (GitHub Actions)](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-threaded-apk.yml?query=branch%3Abanc888-cognition-v1)
- [Verified initial v7.13 APK build](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41998998786) — select the **BANC888-v7.13-cognition-debug** artifact below the run summary to download the ZIP containing APK, SHA-256 and signature report. GitHub sign-in may be required.
- [Cognition design, safety and API contract](docs/COGNITION_ENGINE_V1.md)
- [Weight-space programming](docs/WEIGHT_SPACE_PROGRAMMING.md)

Install the **debug** APK as a separate app named BANC888 Cognition; it is signed by the Android debug certificate and does not overwrite the earlier BANC888 Compat package. Debug signatures are not production release keys and may differ between CI jobs, so upgrading may require uninstalling the previous debug build and exporting conversations first.

Open the **🧠 BANC認知実験** panel to preview a goal/plan JSON without running tools. Execution requires explicit confirmation, an allowed existing tool, nonzero BANC output, and a tool-reported `observedFacts` value. Without observations it stops rather than claiming a task succeeded.

The 96-case synthetic planning benchmark is available in `tools/cognition_benchmark.cjs`. Passing this benchmark is not evidence of human-equivalent comprehension or open-world generalization.
