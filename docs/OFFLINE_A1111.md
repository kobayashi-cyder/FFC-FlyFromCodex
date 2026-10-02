# v7.8: offline connectome and optional A1111

The default generation mode is `offline`: bundled connectome selection and local procedural image/video bodies; no A1111 request. Settings → media → generation mode explicitly enables A1111. The connectome governs the `o3.generate` tool in both modes. It does not replace Stable Diffusion inference or its weights.

For optional A1111, install the desired checkpoint on a local PC and start WebUI with `--api --listen`. Enter the PC LAN URL, e.g. `http://192.168.1.10:7860`. Phone localhost addresses the phone, not the PC. With installed dependencies/models this needs LAN connectivity but no internet. Public endpoints require HTTPS. API authentication and AnimateDiff/Deforum extension endpoints are not implemented.

A1111 video uses standard txt2img followed by img2img on shifted preceding frames. Native HTTP avoids WebView CORS/mixed-content failures; native H.264 writes an actual silent MP4. 2–24 frames, one API image per request, bounded response sizes, explicit cancel/share. It is image animation, not a native temporal diffusion model.

Image generation retains the existing semantic/category/render quality gates and bounded adaptive search. Video uses 32×32 luminance samples for contrast, clipping and temporal change. Offline video attempts one contrast correction and keeps it only if score improves. A1111 retries low-scoring frames up to two attempts by default (maximum three), lowers img2img denoising, keeps the best frame and stops on target/plateau. Metrics are recorded with the video. These are technical quality measures, not learned semantic recognition or proof of model-level quality. All loops have budgets; no perpetual background generation.

Validation: mocked A1111 standard-API sequencing, offline/no-request behavior, settings snapshots, identity, contrast/continuity unit tests; APK and instrumentation APK compilation. A real A1111 server/model has not been supplied, so end-to-end model generation is unverified.
