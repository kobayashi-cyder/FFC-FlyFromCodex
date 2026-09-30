# BANC888 v5.7 Route + Reply

Fixes the voice-thread UX where routing metadata could be the only visible/aural result for conversational utterances.

Behavior:
- Routing metadata remains visible as secondary information.
- Every queued utterance gets an actual assistant reply panel beneath the routing line.
- Non-tool conversation falls through to Fly Agent and, if necessary, a direct chat.compose fallback.
- Thread-routed Agent.run temporarily suppresses its browser TTS to prevent duplicate speech.
- Voice-originated requests are spoken once through Android native TTS when available.
- Spoken form is concise: 'スレッドA。<actual reply>'.
- Human Display is updated with the actual reply plus routing metadata.
- Tool requests continue to show their completion/validation response.

Example:
Routing → A · context · 78%
🪰 こんにちは。今日は何を進めますか？