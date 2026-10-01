from __future__ import annotations

from .executive import ConnectomeExecutive
from .feedback import FeedbackEncoder, FeedbackEvent, TestTier
from .memory import MemoryFabric
from .models import AgentEvent, Observation, ResultStatus


class Observer:
    """Turns tool outcomes into durable experience and local reinforcement."""

    def __init__(self, memory: MemoryFabric, executive: ConnectomeExecutive):
        self.memory = memory
        self.executive = executive
        self.feedback = FeedbackEncoder()

    def record(self, observation: Observation) -> FeedbackEvent:
        result = observation.result
        self.memory.append(
            AgentEvent(
                "observation",
                f"{observation.tool}: {result.status.value}",
                thread_id=observation.thread_id,
                data={
                    "goal_id": observation.goal_id,
                    "step_index": observation.step_index,
                    "tool": observation.tool,
                    "status": result.status.value,
                    "error": result.error,
                    "output": result.output,
                },
            )
        )
        event = self.feedback.from_tool_result("delegate", observation.tool, result)
        self._record_feedback(event, observation.thread_id, observation.goal_id)
        return event

    def record_test(
        self,
        name: str,
        passed: bool,
        *,
        tier: TestTier,
        thread_id: str | None = None,
        goal_id: str | None = None,
        failure: str | None = None,
        action: str = "delegate",
    ) -> FeedbackEvent:
        event = self.feedback.from_test(name, passed, tier=tier, failure=failure, action=action)
        self._record_feedback(event, thread_id, goal_id)
        return event

    def record_quality(
        self,
        action: str,
        quality: dict,
        *,
        previous_score: float | None = None,
        tier: TestTier = TestTier.TRAINING,
        thread_id: str | None = None,
        goal_id: str | None = None,
        source: str = "quality",
    ) -> FeedbackEvent:
        event = self.feedback.from_quality(
            action,
            quality,
            previous_score=previous_score,
            tier=tier,
            source=source,
        )
        self._record_feedback(event, thread_id, goal_id)
        return event

    def _record_feedback(
        self,
        event: FeedbackEvent,
        thread_id: str | None,
        goal_id: str | None,
    ) -> None:
        applied = self.executive.reinforce_feedback(event)
        self.memory.append(
            AgentEvent(
                "feedback",
                f"{event.source}: reward={event.reward:.3f} applied={applied:.3f}",
                thread_id=thread_id,
                data={"goal_id": goal_id, **event.to_dict(), "applied_reward": applied},
            )
        )
