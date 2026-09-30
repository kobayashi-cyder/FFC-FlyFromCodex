# BANC888 Capability Tool Vocabulary Handoff

Branch: banc888-apk-thread-router

Adds a vocabulary -> capability -> tool layer on top of the threaded fly-agent APK.

Implemented capability families:
- code.*: generate/revise and optional Android text-file share
- image.*: route generation to preserved O2/O2 Photo+ body
- voice.*: native Android STT/TTS/status
- document.*: draft and export DOCX plus Markdown/HTML/TXT

The vocabulary classifier is pure JavaScript and unit-tested. It only intercepts requests above a confidence threshold; ordinary conversation continues to the existing fly agent.

DOCX export uses a minimal OOXML ZIP written by the Android shell and shared with FileProvider. No third-party DOCX library is required.

Preserved unchanged in index.html: BANC/O0, CPF, O2/O3, F38/F42/F46, ToolBus, Human Display, v5.x UI.