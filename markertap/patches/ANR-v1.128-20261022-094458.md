# MarkerTap v1.128 / 2026-10-22 09:44:58 JST: ANALYSIS_SLOW and prior input ANRs

## Verified incident evidence

- Device: Pixel 10 / Android 17 (API 37); process `jp.tapmarker.lite`; app `com.kurashiru.nouen.usagi`; virtual display 13.
- Error `ANALYSIS_SLOW` in `farm-default-verify` for 54,063 ms; last analysis 3,090 ms; last successful analysis image 57,765 ms old.
- Display count 2/2; native display 1080 x 2424, 420 dpi; source age 462 ms at detection.
- `main` is BLOCKED waiting on object `0x04792358`; holder `TapMarker-frame-1` (thread 40).
- Thread 40 is performing `Bitmap.nativeRecycle` via `RegionImage`, `TilePixels.get`, `RegionPreviewData.capture`, while holding the same object lock.
- `TapMarker-frame-circuit` is also BLOCKED waiting for that lock.
- Android exit history records INPUT DISPATCHING TIMEOUT ANRs on 2026-10-22 at 05:44, 08:10, 08:11, 08:35 and 09:43, waiting about 5 s for MotionEvent.
- ZIP frames 01-07 display ad pages and frame 08 a full-screen advert with a top-left close icon. This documents ad loading, **not** successful ad close or reward credit.
- Detection UI reports manual interaction / automatic input paused; manual virtual taps sent twice. Do not diagnose that pause as a completed autonomous recovery.

## Interpretation (inferences, not observed proof)

1. Strong evidence of lock contention between UI and preview decoding; native bitmap recycling can hold the lock long enough to cause ANR.
2. Heavy preview work on the ImageReader callback thread can delay fresh frames even if lock scope is shortened.
3. Whether the ad close automation / farm rewards work after recovery is **unverified**; no reward receipt and no full game-loop trace.

## Source remediation applied in GitHub recovery branch

- Existing earlier change: isolate native image retirements and pixel evaluation from `frameLock`; keep the lock for short ownership transfers.
- Additional commit `60e000f357cfb4114f2aed0787f4577f0710b596`: move `RegionPreviewData.capture` from `TapMarker-frame-N` (ImageReader callback) to bounded `TapMarker-preview-worker`, with queue capacity 2 and one active preview per window. Keep generation/revision/freshness guards and orderly worker shutdown.

## Release caveat and acceptance gate

- GitHub `markertap/fix-anr-chatgpt-20261022` derives from a **v1.123 recovery baseline**, not the user's v1.128 or v1.129 source.
- A separately archived **v1.129 signed APK** exists with asynchronous native image retirement and a reported successful build and regression suite, but no Pixel 10 real-device stability verification. The preview-worker GitHub change is **not** included in that APK.
- Never distribute a CI artifact with an ephemeral test signing key as an in-place update; future release must be based on a v1.129 or newer complete runtime, with monotonically increasing versionCode and matching release certificate.
- Acceptance: 2 virtual screens at native resolution, manual intervention, multiple interstitial ads, no 5-second input ANR, no >1.5-second effective analysis stalls sustained for 60 s, and actual verified reward progress; repeat with clean start/stop and no orphaned displays.
- Do not infer that a CI pass alone proves runtime success.

## Follow-up

Use this incident as a regression input when porting the preview-worker fix to the latest binary/source kit. Preserve the existing in-app user-initiated ChatGPT diagnostic share and the separate ZIP export. Never upload signing keys or unredacted private screenshots into public source control.
