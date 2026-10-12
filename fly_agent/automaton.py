from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Callable, Iterable


SUPPORTED_ACTIONS = {"tap", "swipe", "wait", "back", "launch", "noop"}


@dataclass(slots=True)
class RecordingEvent:
    """Normalized event extracted from a screen recording or companion trace.

    `state_key` is a stable visual-state identifier produced by a recording
    analyzer. Action coordinates are normalized to 0..1 so the generated
    automaton is not tied to one screen resolution.
    """

    timestamp_ms: int
    state_key: str
    kind: str = "frame"
    x: float | None = None
    y: float | None = None
    x2: float | None = None
    y2: float | None = None
    duration_ms: int = 0
    confidence: float = 1.0
    label: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)

    def normalized(self) -> "RecordingEvent":
        def clamp(value: float | None) -> float | None:
            if value is None:
                return None
            return max(0.0, min(1.0, float(value)))

        return RecordingEvent(
            timestamp_ms=max(0, int(self.timestamp_ms)),
            state_key=str(self.state_key or "unknown"),
            kind=str(self.kind or "frame").lower(),
            x=clamp(self.x),
            y=clamp(self.y),
            x2=clamp(self.x2),
            y2=clamp(self.y2),
            duration_ms=max(0, int(self.duration_ms or 0)),
            confidence=max(0.0, min(1.0, float(self.confidence))),
            label=str(self.label or ""),
            metadata=dict(self.metadata or {}),
        )


@dataclass(slots=True)
class ActionSpec:
    kind: str
    args: dict[str, Any] = field(default_factory=dict)
    confidence: float = 1.0
    source: str = "recording"

    def validate(self) -> None:
        if self.kind not in SUPPORTED_ACTIONS:
            raise ValueError(f"unsupported action: {self.kind}")
        if not 0.0 <= float(self.confidence) <= 1.0:
            raise ValueError("action confidence must be within 0..1")


@dataclass(slots=True)
class TransitionSpec:
    to_state: str
    action: ActionSpec
    min_delay_ms: int = 0
    max_delay_ms: int = 5000
    confidence: float = 1.0

    def validate(self) -> None:
        if not self.to_state:
            raise ValueError("transition target is required")
        self.action.validate()
        if self.min_delay_ms < 0 or self.max_delay_ms < self.min_delay_ms:
            raise ValueError("invalid transition delay window")
        if not 0.0 <= float(self.confidence) <= 1.0:
            raise ValueError("transition confidence must be within 0..1")


@dataclass(slots=True)
class StateSpec:
    key: str
    transitions: list[TransitionSpec] = field(default_factory=list)
    terminal: bool = False


@dataclass(slots=True)
class AutomatonSpec:
    name: str
    initial_state: str
    states: dict[str, StateSpec]
    schema_version: int = 1
    source: str = "recording"
    metadata: dict[str, Any] = field(default_factory=dict)

    def validate(self) -> None:
        if self.schema_version != 1:
            raise ValueError(f"unsupported automaton schema: {self.schema_version}")
        if self.initial_state not in self.states:
            raise ValueError("initial state is missing")
        for key, state in self.states.items():
            if key != state.key:
                raise ValueError(f"state key mismatch: {key} != {state.key}")
            for transition in state.transitions:
                transition.validate()
                if transition.to_state not in self.states:
                    raise ValueError(f"unknown target state: {transition.to_state}")

    def to_dict(self) -> dict[str, Any]:
        self.validate()
        return {
            "schema_version": self.schema_version,
            "name": self.name,
            "source": self.source,
            "initial_state": self.initial_state,
            "metadata": self.metadata,
            "states": {
                key: {
                    "key": state.key,
                    "terminal": state.terminal,
                    "transitions": [asdict(item) for item in state.transitions],
                }
                for key, state in self.states.items()
            },
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AutomatonSpec":
        states: dict[str, StateSpec] = {}
        for key, payload in dict(data.get("states") or {}).items():
            transitions = []
            for raw in payload.get("transitions") or []:
                action_raw = raw.get("action") or {}
                action = ActionSpec(
                    kind=str(action_raw.get("kind") or "noop"),
                    args=dict(action_raw.get("args") or {}),
                    confidence=float(action_raw.get("confidence", 1.0)),
                    source=str(action_raw.get("source") or "recording"),
                )
                transitions.append(
                    TransitionSpec(
                        to_state=str(raw.get("to_state") or ""),
                        action=action,
                        min_delay_ms=int(raw.get("min_delay_ms", 0)),
                        max_delay_ms=int(raw.get("max_delay_ms", 5000)),
                        confidence=float(raw.get("confidence", 1.0)),
                    )
                )
            states[str(key)] = StateSpec(
                key=str(payload.get("key") or key),
                transitions=transitions,
                terminal=bool(payload.get("terminal", False)),
            )
        spec = cls(
            name=str(data.get("name") or "automaton"),
            initial_state=str(data.get("initial_state") or ""),
            states=states,
            schema_version=int(data.get("schema_version", 1)),
            source=str(data.get("source") or "recording"),
            metadata=dict(data.get("metadata") or {}),
        )
        spec.validate()
        return spec


class RecordingAutomatonGenerator:
    """Converts normalized recording observations into an editable automaton.

    Raw MP4 decoding intentionally lives outside this class. Android, desktop,
    or test analyzers only need to emit `RecordingEvent` objects. This keeps the
    learned state machine stable even if the video-decoding implementation is
    replaced later.
    """

    def __init__(self, *, min_confidence: float = 0.45, max_wait_ms: int = 15000):
        self.min_confidence = max(0.0, min(1.0, float(min_confidence)))
        self.max_wait_ms = max(250, int(max_wait_ms))

    @staticmethod
    def _action_from_event(event: RecordingEvent | None) -> ActionSpec:
        if event is None:
            return ActionSpec("noop", {}, 0.5, "recording-inferred")
        kind = event.kind if event.kind in SUPPORTED_ACTIONS else "noop"
        args: dict[str, Any] = {}
        if kind == "tap" and event.x is not None and event.y is not None:
            args = {"x": event.x, "y": event.y}
        elif kind == "swipe" and None not in (event.x, event.y, event.x2, event.y2):
            args = {
                "x1": event.x,
                "y1": event.y,
                "x2": event.x2,
                "y2": event.y2,
                "duration_ms": event.duration_ms,
            }
        elif kind == "wait":
            args = {"duration_ms": event.duration_ms}
        elif event.label:
            args = {"label": event.label}
        return ActionSpec(kind, args, event.confidence, "recording")

    def generate(self, events: Iterable[RecordingEvent], *, name: str = "recording_automaton") -> AutomatonSpec:
        normalized = sorted((event.normalized() for event in events), key=lambda event: event.timestamp_ms)
        if not normalized:
            raise ValueError("at least one recording event is required")

        states: dict[str, StateSpec] = {}
        for event in normalized:
            states.setdefault(event.state_key, StateSpec(event.state_key))

        current_state = normalized[0].state_key
        last_state_time = normalized[0].timestamp_ms
        pending_action: RecordingEvent | None = None
        transition_count = 0

        for event in normalized[1:]:
            if event.kind in SUPPORTED_ACTIONS and event.kind != "noop":
                pending_action = event
                continue
            if event.state_key == current_state:
                continue

            elapsed = max(0, event.timestamp_ms - last_state_time)
            action = self._action_from_event(pending_action)
            confidence = min(event.confidence, action.confidence)
            if confidence >= self.min_confidence:
                states[current_state].transitions.append(
                    TransitionSpec(
                        to_state=event.state_key,
                        action=action,
                        min_delay_ms=0,
                        max_delay_ms=min(self.max_wait_ms, max(500, elapsed + 1000)),
                        confidence=confidence,
                    )
                )
                transition_count += 1

            current_state = event.state_key
            last_state_time = event.timestamp_ms
            pending_action = None

        if transition_count == 0:
            states[current_state].terminal = True
        else:
            referenced = {t.to_state for state in states.values() for t in state.transitions}
            outgoing = {key for key, state in states.items() if state.transitions}
            for key in referenced - outgoing:
                states[key].terminal = True

        spec = AutomatonSpec(
            name=name,
            initial_state=normalized[0].state_key,
            states=states,
            metadata={
                "event_count": len(normalized),
                "transition_count": transition_count,
                "min_confidence": self.min_confidence,
                "coordinates": "normalized_0_to_1",
            },
        )
        spec.validate()
        return spec


class AutomatonRunner:
    """Small deterministic interpreter for generated automata.

    The runner does not know about ADB or Accessibility. An Android/desktop
    adapter provides the current visual `state_key` and executes the action.
    """

    def __init__(
        self,
        spec: AutomatonSpec,
        execute_action: Callable[[ActionSpec], Any],
        *,
        min_transition_confidence: float = 0.5,
    ):
        spec.validate()
        self.spec = spec
        self.execute_action = execute_action
        self.min_transition_confidence = max(0.0, min(1.0, float(min_transition_confidence)))
        self.current_state = spec.initial_state

    def reset(self) -> None:
        self.current_state = self.spec.initial_state

    def step(self, observed_state: str | None = None) -> str:
        if observed_state and observed_state in self.spec.states:
            self.current_state = observed_state
        state = self.spec.states[self.current_state]
        if state.terminal or not state.transitions:
            return self.current_state
        candidates = [
            transition for transition in state.transitions
            if transition.confidence >= self.min_transition_confidence
        ]
        if not candidates:
            return self.current_state
        transition = max(candidates, key=lambda item: item.confidence)
        self.execute_action(transition.action)
        self.current_state = transition.to_state
        return self.current_state
