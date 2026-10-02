from __future__ import annotations

from collections import deque
import json
import os
from pathlib import Path
import re
import threading
from typing import Any, Iterable

from .models import AgentEvent
from .serialization import json_safe

_TOKEN = re.compile(r"[\wぁ-んァ-ン一-龥]+", re.UNICODE)


class MemoryFabric:
    """Global + per-thread episodic memory backed by append-only JSONL."""

    def __init__(self, path: str | Path | None = None, working_limit: int = 512):
        self.path = Path(path) if path else None
        self.working = deque(maxlen=working_limit)
        self._lock = threading.RLock()
        if self.path:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            if self.path.exists():
                for line in self.path.read_text(encoding="utf-8").splitlines()[-working_limit:]:
                    try:
                        row = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if isinstance(row, dict):
                        self.working.append(row)

    def append(self, event: AgentEvent) -> None:
        row = json_safe({
            "kind": event.kind,
            "message": event.message,
            "thread_id": event.thread_id,
            "data": event.data,
            "ts": event.ts,
        })
        with self._lock:
            self.working.append(row)
            if self.path:
                with self.path.open("a", encoding="utf-8") as f:
                    f.write(json.dumps(row, ensure_ascii=False, allow_nan=False) + "\n")
                    f.flush()
                    os.fsync(f.fileno())

    def recall(self, query: str, thread_id: str | None = None, limit: int = 8) -> list[dict[str, Any]]:
        q = {t.lower() for t in _TOKEN.findall(query)}
        with self._lock:
            rows = list(self.working)
        scored: list[tuple[float, float, dict[str, Any]]] = []
        for row in rows:
            row_thread = row.get("thread_id")
            if thread_id is not None and row_thread not in (None, thread_id):
                continue
            blob = f"{row.get('message','')} {json.dumps(row.get('data',{}), ensure_ascii=False)}"
            tokens = {t.lower() for t in _TOKEN.findall(blob)}
            overlap = len(q & tokens) if q else 0
            thread_bonus = 1.5 if thread_id is not None and row_thread == thread_id else 0.0
            if q and not overlap and not thread_bonus:
                continue
            scored.append((float(overlap) + thread_bonus, float(row.get("ts", 0)), row))
        scored.sort(key=lambda item: (item[0], item[1]), reverse=True)
        if not q:
            relevant = [row for row in rows if thread_id is None or row.get("thread_id") in (None, thread_id)]
            return relevant[-limit:]
        return [row for _, _, row in scored[:limit]]

    def thread_tail(self, thread_id: str, limit: int = 20) -> list[dict[str, Any]]:
        with self._lock:
            rows = [row for row in self.working if row.get("thread_id") == thread_id]
        return rows[-limit:]

    def tail(self, limit: int = 20) -> Iterable[dict[str, Any]]:
        with self._lock:
            return list(self.working)[-limit:]


EpisodeMemory = MemoryFabric
