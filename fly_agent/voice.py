from __future__ import annotations

from .models import RoutedText
from .threads import ThreadRouter


class VoiceRouter:
    """Routes one physical microphone stream to logical thread listeners."""

    def __init__(self, threads: ThreadRouter):
        self.threads = threads

    def set_listener(self, thread_id: str, enabled: bool) -> None:
        self.threads.set_listener(thread_id, enabled)

    def route_transcript(self, transcript: str, explicit_thread: str | None = None) -> RoutedText:
        routed = self.threads.route(transcript, explicit_thread=explicit_thread, listeners_only=True)
        self.threads.record(routed.thread_id, f"VOICE {routed.text}")
        return routed
