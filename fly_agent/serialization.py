from __future__ import annotations

import base64
from dataclasses import asdict, is_dataclass
from enum import Enum
import math
from pathlib import Path
from typing import Any, Mapping


def json_safe(
    value: Any,
    *,
    max_depth: int = 8,
    max_items: int = 256,
    max_string: int = 4096,
    max_bytes: int = 4096,
    _seen: set[int] | None = None,
    _depth: int = 0,
) -> Any:
    """Convert arbitrary tool/event output into bounded JSON-safe data."""
    if value is None or isinstance(value, (bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else str(value)
    if isinstance(value, str):
        return value if len(value) <= max_string else value[:max_string] + "...<truncated>"
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, Enum):
        return json_safe(value.value, max_depth=max_depth, max_items=max_items, max_string=max_string, max_bytes=max_bytes)
    if isinstance(value, (bytes, bytearray, memoryview)):
        raw = bytes(value)
        clipped = raw[:max_bytes]
        return {
            "__type__": "bytes",
            "base64": base64.b64encode(clipped).decode("ascii"),
            "length": len(raw),
            "truncated": len(raw) > len(clipped),
        }
    if _depth >= max_depth:
        return {"__type__": type(value).__name__, "repr": _safe_repr(value, max_string), "truncated": True}

    seen = _seen if _seen is not None else set()
    identity = id(value)
    track = isinstance(value, (Mapping, list, tuple, set, frozenset)) or is_dataclass(value)
    if track:
        if identity in seen:
            return {"__type__": type(value).__name__, "cycle": True}
        seen.add(identity)

    try:
        if is_dataclass(value):
            value = asdict(value)
        if isinstance(value, Mapping):
            result: dict[str, Any] = {}
            items = list(value.items())
            for key, item in items[:max_items]:
                result[str(key)] = json_safe(
                    item,
                    max_depth=max_depth,
                    max_items=max_items,
                    max_string=max_string,
                    max_bytes=max_bytes,
                    _seen=seen,
                    _depth=_depth + 1,
                )
            if len(items) > max_items:
                result["__truncated_items__"] = len(items) - max_items
            return result
        if isinstance(value, (list, tuple, set, frozenset)):
            items = list(value)
            result = [
                json_safe(
                    item,
                    max_depth=max_depth,
                    max_items=max_items,
                    max_string=max_string,
                    max_bytes=max_bytes,
                    _seen=seen,
                    _depth=_depth + 1,
                )
                for item in items[:max_items]
            ]
            if len(items) > max_items:
                result.append({"__truncated_items__": len(items) - max_items})
            return result
        return {"__type__": type(value).__name__, "repr": _safe_repr(value, max_string)}
    finally:
        if track:
            seen.discard(identity)


def _safe_repr(value: Any, max_string: int) -> str:
    try:
        text = repr(value)
    except Exception:
        text = f"<{type(value).__name__} repr failed>"
    return text if len(text) <= max_string else text[:max_string] + "...<truncated>"
