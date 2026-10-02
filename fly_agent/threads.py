from __future__ import annotations

import re
import time

from .models import AgentThread, RoutedText

_TOKEN = re.compile(r"[\wぁ-んァ-ン一-龥]+", re.UNICODE)
_EXPLICIT = re.compile(r"^\s*(?:thread\s*)?([A-Z]{1,4})\s*[:：]\s*(.+)$", re.IGNORECASE)
_BRACKET = re.compile(r"^\s*\[([A-Z]{1,4})\]\s*(.+)$", re.IGNORECASE)


def excel_label(index: int) -> str:
    if index < 0:
        raise ValueError("index must be >= 0")
    value = index + 1
    chars: list[str] = []
    while value:
        value, rem = divmod(value - 1, 26)
        chars.append(chr(ord("A") + rem))
    return "".join(reversed(chars))


class ThreadRouter:
    def __init__(self, context_limit: int = 32):
        self.context_limit = context_limit
        self._threads: dict[str, AgentThread] = {}
        self.active_thread_id: str | None = None

    def create(self, title: str | None = None, listener_enabled: bool = True) -> AgentThread:
        label = excel_label(len(self._threads))
        thread = AgentThread(label=label, title=title or f"Thread {label}", listener_enabled=listener_enabled)
        self._threads[thread.id] = thread
        if self.active_thread_id is None:
            self.active_thread_id = thread.id
        return thread

    def threads(self) -> list[AgentThread]:
        return sorted(self._threads.values(), key=lambda t: t.created_at)

    def get(self, thread_id: str) -> AgentThread | None:
        return self._threads.get(thread_id)

    def get_by_label(self, label: str) -> AgentThread | None:
        label = label.upper()
        return next((t for t in self._threads.values() if t.label.upper() == label), None)

    def set_active(self, thread_id: str) -> None:
        if thread_id not in self._threads:
            raise KeyError(thread_id)
        self.active_thread_id = thread_id
        self._threads[thread_id].updated_at = time.time()

    def set_listener(self, thread_id: str, enabled: bool) -> None:
        thread = self._require(thread_id)
        thread.listener_enabled = bool(enabled)
        thread.updated_at = time.time()

    def record(self, thread_id: str, text: str) -> None:
        text = text.strip()
        if not text:
            return
        thread = self._require(thread_id)
        thread.context.append(text)
        if len(thread.context) > self.context_limit:
            del thread.context[:-self.context_limit]
        thread.updated_at = time.time()
        self.active_thread_id = thread_id

    def route(self, text: str, explicit_thread: str | None = None, listeners_only: bool = False) -> RoutedText:
        text = text.strip()
        if explicit_thread:
            thread = self._resolve(explicit_thread)
            if thread is None:
                raise KeyError(f"unknown thread: {explicit_thread}")
            return RoutedText(thread.id, thread.label, text, explicit=True)

        parsed = self._parse_explicit(text)
        if parsed:
            label, body = parsed
            thread = self.get_by_label(label)
            if thread is not None:
                return RoutedText(thread.id, thread.label, body, explicit=True)

        candidates = [t for t in self.threads() if (t.listener_enabled or not listeners_only)]
        if not candidates:
            if listeners_only and self._threads:
                raise RuntimeError("no thread voice listeners are enabled")
            candidates = [self.create()]

        query = self._tokens(text)
        scored: list[tuple[float, float, AgentThread]] = []
        for thread in candidates:
            corpus = self._tokens(" ".join([thread.title, *thread.context[-8:]]))
            overlap = len(query & corpus)
            score = float(overlap * 3)
            if thread.id == self.active_thread_id:
                score += 0.75
            scored.append((score, thread.updated_at, thread))
        scored.sort(key=lambda row: (row[0], row[1]), reverse=True)
        best = scored[0][2]
        return RoutedText(best.id, best.label, text, explicit=False)

    def to_state(self) -> dict:
        return {
            "active_thread_id": self.active_thread_id,
            "threads": [thread.to_dict() for thread in self.threads()],
        }

    def restore(self, state: dict | None) -> None:
        self._threads.clear()
        if not state:
            self.active_thread_id = None
            return
        for raw in state.get("threads", []):
            thread = AgentThread.from_dict(raw)
            self._threads[thread.id] = thread
        active = state.get("active_thread_id")
        self.active_thread_id = active if active in self._threads else (self.threads()[0].id if self._threads else None)

    def _resolve(self, ref: str) -> AgentThread | None:
        return self.get(ref) or self.get_by_label(ref)

    def _require(self, thread_id: str) -> AgentThread:
        thread = self.get(thread_id)
        if thread is None:
            raise KeyError(thread_id)
        return thread

    @staticmethod
    def _tokens(text: str) -> set[str]:
        return {token.lower() for token in _TOKEN.findall(text)}

    @staticmethod
    def _parse_explicit(text: str) -> tuple[str, str] | None:
        for pattern in (_EXPLICIT, _BRACKET):
            match = pattern.match(text)
            if match:
                return match.group(1).upper(), match.group(2).strip()
        return None
