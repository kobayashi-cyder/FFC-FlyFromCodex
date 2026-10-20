# v1.53 third-party changes

LibADB Android 3.1.1 sources are distributed under the Apache-2.0 option of the upstream dual license. Changes in this bundle:

- `AdbConnection`: bounded socket connection and read timeout.
- `PairingConnectionCtx`: bounded socket connection/read timeouts, cancellable TLS transport, null-safe cleanup, idempotent cleanup of its authentication context. `abort()` closes transport without destroying a crypto context while the worker may still be using it. The worker performs final `close()`.
- `SslUtils`: create a TLS context per transport key, so reconnecting does not retain a destroyed key from an earlier connection.

SPAKE2-Android 2.2.1 is the published Android JNI artifact. Its corresponding tag and C submodule commit `0d15933e5ba3e662cb01245a7ac0dc9fca3eac31` are included. No cryptographic algorithm changes were made. The experimental pure Java implementation is not used or compiled into this APK.

Conscrypt Android 2.5.3, Bouncy Castle 1.81, AndroidX annotation 1.9.1, and the previously used optional Shizuku API jars are unmodified. Their license texts and notices are included in `assets/licenses/` and `assets/THIRD-PARTY-NOTICES.txt`.

The small host-test JNI header comes from AOSP `platform/libnativehelper/include_jni/jni.h` and retains its upstream copyright/license header. It is used only by the Linux crypto test build.


## v1.73 BANC888 and recording repair
BANC888 v7.12 APK assets proxy-agent-core.js and autonomy-core.js were used as the reference for the native candidate selector, outcome credit and procedural acquisition. Figure 3g aggregate counts/transmitter assumptions are preserved. The two original JS modules are included under tests/reference-banc888 to verify numerical parity. Original provenance and hashes are in assets/banc888-recovery-provenance.json. Engineered task bindings and signed propagation assumptions are distinct from biological validation. Existing circuit.bin is unchanged.
