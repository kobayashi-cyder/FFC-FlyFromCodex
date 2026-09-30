from __future__ import annotations

from collections import deque
from dataclasses import asdict
import json
from pathlib import Path
import re
from typing import Any, Iterable

from .models import AgentEvent

_TOKEN = re.compile(r"[\wぁ-んァ-ン一-龥]+", re.UNICODE)


class EpisodeMemory:
    def __init__(self, path: str | Path | None = None, working_limit: int = 128):
        self.path = Path(path) if path else None
        self.working = deque(maxlen=working_limit)
        if self.path:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            if self.path.exists():
                for line in self.path.read_text(encoding="utf-8").splitlines()[-working_limit:]:
                    try:
                        self.working.append(json.loads(line))
                    except json.JSONDecodeError:
                        continue

    def append(self, event: AgentEvent) -> None:
        row = asdict(event)
        self.working.append(row)
        if self.path:
            with self.path.open("a", encoding="utf-8") as f:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")

    def recall(self, query: str, limit: int = 5) -> list[dict[str, Any]]:
        q = {t.lower() for t in _TOKEN.findall(query)}
        if not q:
            return list(self.working)[-limit:]
        scored: list[tuple[int, float, dict[str, Any]]] = []
        for row in self.working:
            text = f"{row.get('message','')} {json.dumps(row.get('data',{}), ensure_ascii=False)}"
            tokens = {t.lower() for t in _TOKEN.findall(text)}
            overlap = len(q & tokens)
            if overlap:
                scored.append((overlap, float(row.get("ts", 0)), row))
        scored.sort(key=lambda x: (x[0], x[1]), reverse=True)
        return [row for _, _, row in scored[:limit]]

    def tail(self, limit: int = 20) -> Iterable[dict[str, Any]]:
        return list(self.working)[-limit:]
