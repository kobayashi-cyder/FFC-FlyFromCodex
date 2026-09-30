from __future__ import annotations

from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Any
import time
import uuid


class GoalStatus(str, Enum):
    QUEUED = "queued"
    RUNNING = "running"
    PAUSED = "paused"
    DONE = "done"
    FAILED = "failed"


@dataclass(slots=True)
class Stimulus:
    channel: str
    value: float = 1.0
    salience: float = 1.0
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class Intent:
    action: str
    confidence: float
    source: str = "connectome"
    payload: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class Goal:
    text: str
    priority: int = 50
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    status: GoalStatus = GoalStatus.QUEUED
    created_at: float = field(default_factory=time.time)
    attempts: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["status"] = self.status.value
        return data

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Goal":
        copy = dict(data)
        copy["status"] = GoalStatus(copy.get("status", GoalStatus.QUEUED.value))
        return cls(**copy)


@dataclass(slots=True)
class PlanStep:
    tool: str
    args: dict[str, Any] = field(default_factory=dict)
    description: str = ""
    max_retries: int = 2


@dataclass(slots=True)
class ToolResult:
    ok: bool
    output: Any = None
    error: str | None = None


@dataclass(slots=True)
class AgentEvent:
    kind: str
    message: str
    data: dict[str, Any] = field(default_factory=dict)
    ts: float = field(default_factory=time.time)
