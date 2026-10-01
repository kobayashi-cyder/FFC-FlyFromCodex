from __future__ import annotations

from .autonomy import AutonomousSkillLearner
from .executive import ConnectomeExecutive
from .feedback import FeedbackEncoder, FeedbackEvent, TestTier
from .memory import MemoryFabric
from .models import AgentEvent, Observation, ResultStatus


class Observer:
    """Turns tool outcomes into durable experience and local reinforcement."""

    def __init__(
        self,
        memory: MemoryFabric,
        executive: ConnectomeExecutive,
        learner: AutonomousSkillLearner | None = None,
    ):
        self.memory = memory
        self.executive = executive
        self.feedback = FeedbackEncoder()
        self.learner = learner

    def record(self, observation: Observation) -> None:
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

    def record_test(
        self,
        name: str,
        passed: bool,
        *,
        tier: TestTier,
        thread_id: str | None = None,
        goal_id: str | None = None,
        failure: str | None = None,
    ) -> FeedbackEvent:
        event = self.feedback.from_test(name, passed, tier=tier, failure=failure)
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
        applied, decision = self.executive.consume_feedback(event)
        profile = self.learner.observe(event.source, event) if self.learner is not None else None
        self.memory.append(
            AgentEvent(
                "feedback",
                f"{event.source}: reward={event.reward:.3f} applied={applied:.3f}",
                thread_id=thread_id,
                data={
                    "goal_id": goal_id,
                    **event.to_dict(),
                    "applied_reward": applied,
                    "feedback_decision": decision.type.value,
                    "feedback_action": decision.intent.action if decision.intent else None,
                    "skill": profile.to_dict() if profile is not None else None,
                },
            )
        )
