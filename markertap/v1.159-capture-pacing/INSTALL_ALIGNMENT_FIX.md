# v1.159 installer alignment fix (2026-11-04)

## Confirmed defect in the prior local candidate
- Earlier candidate `MarkerTap-v1.159-capture-hotfix-signed.apk` had `resources.arsc` ZIP data start at byte 2695 (offset modulo 4 = 3); the original v1.158 placed it at 2696 (aligned).
- Android targetSdk=35 requires `resources.arsc` to be uncompressed and 4-byte aligned. A correctly signed APK can still be rejected with this defect.
- Never distribute the superseded hash `d45f808edcc7b53967a45f914e6adda3b6f8c8d382226165390b7b1bf95fd3a5`.

## Corrected local deliverables
- `MarkerTap-v1.159-installfix-vcode164-signed.apk`: SHA256 `8707a9020ae342f807bb63763f0129631afc4f433906850d4aaf453ec66b8ca2`, versionName 1.159, versionCode 164.
- `MarkerTap-v1.158-rollbackfix-vcode165-signed.apk`: SHA256 `076c3144e9d003118710a4529f49e8f2cc1c0251eacde35c652b5f016db31c88`, versionName 1.158, versionCode 165.
- `resources.arsc` is stored and aligned at byte 2700; all 4 STORED payloads 4-byte aligned.
- Every original APK ZIP member preserved byte-for-byte except patched classes.dex and AndroidManifest.xml.
- Signer certificate SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` unchanged.
- APK v2 and v3 RSA signatures and complete signed APK content digests checked by offline verifier.

## Limitations
- Signed APK artifacts are shared separately via ChatGPT. They are NOT hosted in this GitHub repository or release; this file only documents checksums and changes.
- No ADB `pm install` test on Pixel 10 / Android 17 and no Android SDK `apksigner` independent check have run yet.
- This is a capture pacing mitigation only; 1ms tap/5ms recognition scheduler not integrated into the actual Android binary.
- VersionCode165 rollback is only if needed; any subsequent release must use versionCode166+.

Android reference: https://developer.android.com/about/versions/11/behavior-changes-11#resources-arsc

## Release gate
Keep draft. Have the owner update in place (do NOT uninstall), check package acceptance, and collect Android's exact install failure text if one remains.
