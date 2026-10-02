from __future__ import annotations

import json
import os
from pathlib import Path
import tempfile
from typing import Any


class AtomicCheckpointStore:
    """Durable JSON checkpoint with last-known-good fallback."""

    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.backup = self.path.with_suffix(self.path.suffix + ".bak")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.recovered_from_backup = False

    def save(self, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8")
        if self.path.exists():
            try:
                previous = self.path.read_bytes()
                json.loads(previous.decode("utf-8"))
                self._atomic_write(self.backup, previous)
            except (OSError, UnicodeDecodeError, json.JSONDecodeError):
                pass
        self._atomic_write(self.path, encoded)

    def load(self, default: dict[str, Any] | None = None) -> dict[str, Any]:
        self.recovered_from_backup = False
        for candidate, is_backup in ((self.path, False), (self.backup, True)):
            if not candidate.exists():
                continue
            try:
                data = json.loads(candidate.read_text(encoding="utf-8"))
                if not isinstance(data, dict):
                    raise ValueError("checkpoint root must be an object")
                self.recovered_from_backup = is_backup
                return data
            except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError):
                continue
        return {} if default is None else dict(default)

    @staticmethod
    def _atomic_write(path: Path, data: bytes) -> None:
        fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
        try:
            with os.fdopen(fd, "wb") as f:
                f.write(data)
                f.flush()
                os.fsync(f.fileno())
            os.replace(temp_name, path)
            try:
                dir_fd = os.open(path.parent, os.O_RDONLY)
            except (AttributeError, OSError):
                dir_fd = None
            if dir_fd is not None:
                try:
                    os.fsync(dir_fd)
                finally:
                    os.close(dir_fd)
        finally:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass
