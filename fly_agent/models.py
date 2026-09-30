from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any
import time
import uuid


class GoalStatus(str, Enum):
    QUEUED = "queued"
    RUNNING = "running"
    WAITING = "waiting"
    BLOCKED = "blocked"
    DONE = "done"
    FAILED = "failed"
    CANCELLED = "cancelled"


class ResultStatus(str, Enum):
    SUCCESS = "success"
    RETRY = "retry"
    FAILED = "failed"
    BLOCKED = "blocked"
    UNSUPPORTED = "unsupported"


class OutputMode(str, Enum):
    SCREEN = "screen"
    SPEECH = "speech"
    BOTH = "both"
    SILENT = "silent"


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
    thread_id: str | None = None
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    status: GoalStatus = GoalStatus.QUEUED
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    attempts: int = 0
    last_error: str | None = None
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
    expected_effect: str = ""

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PlanStep":
        if not isinstance(data, dict) or not isinstance(data.get("tool"), str):
            raise ValueError("invalid plan step")
        args = data.get("args", {})
        if not isinstance(args, dict):
            raise ValueError("plan step args must be an object")
        return cls(
            tool=data["tool"],
            args=dict(args),
            description=str(data.get("description", "")),
            max_retries=int(data.get("max_retries", 2)),
            expected_effect=str(data.get("expected_effect", "")),
        )


@dataclass(slots=True)
class PlanProposal:
    steps: list[PlanStep]
    rationale: str = ""
    confidence: float = 1.0
    source: str = "rule"
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])

    def to_dict(self) -> dict[str, Any]:
        return {
            "steps": [step.to_dict() for step in self.steps],
            "rationale": self.rationale,
            "confidence": self.confidence,
            "source": self.source,
            "id": self.id,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "PlanProposal":
        if not isinstance(data, dict):
            raise ValueError("invalid plan proposal")
        raw_steps = data.get("steps")
        if not isinstance(raw_steps, list) or not raw_steps:
            raise ValueError("plan proposal requires steps")
        return cls(
            steps=[PlanStep.from_dict(step) for step in raw_steps],
            rationale=str(data.get("rationale", "")),
            confidence=float(data.get("confidence", 1.0)),
            source=str(data.get("source", "recovered")),
            id=str(data.get("id") or uuid.uuid4().hex[:12]),
        )


@dataclass(slots=True)
class PlannerResult:
    status: ResultStatus
    proposal: PlanProposal | None = None
    error: str | None = None

    @property
    def ok(self) -> bool:
        return self.status == ResultStatus.SUCCESS and self.proposal is not None


@dataclass(slots=True)
class ToolResult:
    status: ResultStatus
    output: Any = None
    error: str | None = None

    @property
    def ok(self) -> bool:
        return self.status == ResultStatus.SUCCESS

    @property
    def retryable(self) -> bool:
        return self.status == ResultStatus.RETRY

    @classmethod
    def success(cls, output: Any = None) -> "ToolResult":
        return cls(ResultStatus.SUCCESS, output=output)

    @classmethod
    def retry(cls, error: str) -> "ToolResult":
        return cls(ResultStatus.RETRY, error=error)

    @classmethod
    def failed(cls, error: str) -> "ToolResult":
        return cls(ResultStatus.FAILED, error=error)

    @classmethod
    def blocked(cls, error: str) -> "ToolResult":
        return cls(ResultStatus.BLOCKED, error=error)

    @classmethod
    def unsupported(cls, error: str) -> "ToolResult":
        return cls(ResultStatus.UNSUPPORTED, error=error)


@dataclass(slots=True)
class AgentEvent:
    kind: str
    message: str
    thread_id: str | None = None
    data: dict[str, Any] = field(default_factory=dict)
    ts: float = field(default_factory=time.time)


@dataclass(slots=True)
class AgentThread:
    label: str
    title: str
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    listener_enabled: bool = True
    context: list[str] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AgentThread":
        return cls(**data)


@dataclass(slots=True)
class RoutedText:
    thread_id: str
    thread_label: str
    text: str
    explicit: bool = False


@dataclass(slots=True)
class ToolContext:
    thread_id: str | None = None
    goal_id: str | None = None


@dataclass(slots=True)
class Observation:
    goal_id: str | None
    thread_id: str | None
    tool: str
    result: ToolResult
    step_index: int = 0
