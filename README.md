# FFC — FlyFromCodex

FFC development repository. Android APK builds are isolated on dedicated build branches before any merge to main.

## MarkerTap: source recovery and current APK

MarkerTap is stored on the dedicated [markertap/recovery branch](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/tree/markertap/recovery/markertap).

- [Source restoration instructions](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/README.md): v1.117 source archive in four parts, with SHA-256 and ZIP CRC verification; current changes are applied from code/ when extracting.
- [Current APK v1.118](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/raw/refs/heads/markertap/recovery/markertap/releases/MarkerTap-v1.118-native-resolution.apk): restores the phone's normal display resolution and DPI and disables saved low-resolution experiments.
- [Build and test validation](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/releases/VALIDATION-v1.118.json): core regression tests, existing signing certificate and 16 KB alignment verified; Android device testing remains pending.
- [Next work](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/WORK_NEXT.md) and [automaton-defined region-only rendering specification](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/recovery/markertap/ROI_MULTIPLEX_SPEC.md). Region-only capture/rendering is specified but is not implemented in v1.118.

The signing key and plaintext transfer credentials are excluded from this repository.
