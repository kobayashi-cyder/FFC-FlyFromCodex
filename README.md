# FFC — FlyFromCodex

FFC development repository. Android APK builds are isolated on dedicated build branches before any merge to main.

## MarkerTap: source recovery and current APK

MarkerTap is stored on the dedicated [markertap/recovery branch](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/tree/markertap/recovery/markertap).

- [Source restoration instructions](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/README.md): v1.117 source archive in four parts, with SHA-256 and ZIP CRC verification; current changes are applied from code/ when extracting.
- [Current APK v1.119](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.119/MarkerTap-v1.119-region-capture.apk): keeps the phone's normal display resolution and DPI, reads automaton-dependent regions directly from native buffers and reuses unchanged preview patches.
- [Build and test validation](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/releases/VALIDATION-v1.119.json): core regression tests (including region capture, image/reader lease retirement and exact detector results on 52 recorded frames), existing signing certificate and 16 KB alignment verified; Android device testing remains pending.
- [Next work](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/WORK_NEXT.md) and [automaton-defined region-only rendering specification](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/ROI_MULTIPLEX_SPEC.md). Region capture and preview patch reuse are implemented in v1.119. Device throughput, GPU memory and multiplex capacity remain unmeasured.

If direct APK saving is unavailable, download the [one-APK ZIP](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.119/MarkerTap-v1.119.zip) and extract it. Both public download URLs were checked without login and their APK contents matched the verified SHA-256.

The signing key and plaintext transfer credentials are excluded from this repository.
