# BANC888 APK Thread Router Handoff

Branch: `banc888-apk-thread-router`

## Goal

Keep the fly as the proxy operator while allowing multiple independent conversation threads to stay alive in parallel. One physical microphone feeds a fly-owned routing layer; each thread owns a logical voice listener and its own context/history.

## Thread identity

- Immutable internal ID: `1, 2, 3, ...`
- Human-facing code: Excel-style `A ... Z, AA, AB ...`
- IDs are never reused inside a saved state.

## Voice routing

One Android `SpeechRecognizer` provides the transcription. `thread-router.js` routes that transcription to enabled logical listeners using, in order:

1. Explicit address such as `スレッドB` or `B:`
2. Per-thread learned terms from recent user/assistant messages
3. Pending/active context and recency
4. Active-thread fallback when scores are close or weak

`全スレッド` enables explicit multicast. Low-confidence routing remains visible in the UI.

## Parallelism model

Threads are parallel as persistent contexts and queues. The current BANC888 compute/agent body is shared and synchronous, so body execution is serialized by a global executor lock. This prevents F38/F42/F46/ToolBus state races while still preserving independent thread queues and histories. A future Worker/body pool can replace the executor without changing thread IDs or routing contracts.

## Native voice shell

`MainActivity.java` supplies:

- Android runtime `RECORD_AUDIO` permission
- Native `SpeechRecognizer`
- Native `TextToSpeech`
- JavaScript bridge exposed as `AndroidVoice`
- `WebViewAssetLoader` HTTPS-like local origin
- External-navigation escape so the JS bridge is not exposed to arbitrary remote pages

The existing BANC888 v5.3 HTML is preserved as `app/src/main/assets/index.html`; the thread feature is injected additively from separate JS assets.

## Added files

- Android Gradle project files under `app/`
- `app/src/main/assets/index.html` — inherited BANC888 v5.3 native-voice HTML
- `thread-router-core.js` — pure ID/term/routing logic
- `thread-router.js` — thread UI, persistence, queues, native voice interception
- `tests/thread_router_core.test.cjs`
- `.github/workflows/banc888-threaded-apk.yml`

## Preserved

The inherited HTML's BANC/O0, CPF, O2/O3, F38/F42/F46, ToolBus, Human Display and v5.x UI are not rewritten by the router implementation.
