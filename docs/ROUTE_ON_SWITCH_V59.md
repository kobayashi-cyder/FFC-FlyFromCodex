# BANC888 v5.9 Route On Switch Only

Routing metadata is now a transition notice, not a normal conversation header.

- Same-thread continuation: only the fly reply is shown/spoken.
- A -> B transition: Routing -> B is shown for that response only.
- Voice output prepends 'スレッドB。' only on an actual switch.
- Partial speech-recognition previews never expose routing metadata.
- The next same-thread reply clears the previous routing notice.
- Explicit user switch commands may still announce the requested switch.
- Internal routing remains available to the runtime even when hidden from the user.