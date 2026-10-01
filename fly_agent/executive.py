from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable

from .connectome import GraphConnectomeKernel
from .feedback import FeedbackEvent
from .models import Goal, Intent, PlanProposal, Stimulus
from .tools import ToolBus


class DecisionType(str, Enum):
    REFLEX = "reflex"
    DELEGATE = "delegate"
    IGNORE = "ignore"


@dataclass(slots=True)
class ExecutiveDecision:
    type: DecisionType
    intent: Intent | None = None
    reason: str = ""


@dataclass(slots=True)
class ProposalVerdict:
    accepted: bool
    reason: str = ""


class ConnectomeExecutive:
    """Keeps the connectome in charge of attention/action selection."""

    def __init__(self, kernel: GraphConnectomeKernel, reflex_threshold: float = 0.82, max_plan_steps: int = 16):
        self.kernel = kernel
        self.reflex_threshold = reflex_threshold
        self.max_plan_steps = max_plan_steps

    def perceive(self, stimuli: Iterable[Stimulus]) -> ExecutiveDecision:
        intent = self.kernel.route(stimuli)
        if intent is None:
            return ExecutiveDecision(DecisionType.IGNORE, reason="no connectome motor intent")
        if intent.action == "delegate":
            return ExecutiveDecision(DecisionType.DELEGATE, intent, "connectome delegated high-level work")
        if intent.confidence >= self.reflex_threshold:
            return ExecutiveDecision(DecisionType.REFLEX, intent, "high-confidence connectome reflex")
        return ExecutiveDecision(DecisionType.DELEGATE, intent, "low-confidence motor intent delegated for machine assistance")

    def evaluate_proposal(self, goal: Goal, proposal: PlanProposal | None, tools: ToolBus) -> ProposalVerdict:
        if proposal is None:
            return ProposalVerdict(False, "planner produced no proposal")
        if not proposal.steps:
            return ProposalVerdict(False, "empty proposal")
        if len(proposal.steps) > self.max_plan_steps:
            return ProposalVerdict(False, f"proposal exceeds {self.max_plan_steps} steps")
        if not 0.0 <= proposal.confidence <= 1.0:
            return ProposalVerdict(False, "proposal confidence must be within 0..1")
        for index, step in enumerate(proposal.steps):
            spec = tools.spec(step.tool)
            if spec is None:
                return ProposalVerdict(False, f"step {index}: unknown tool {step.tool}")
            if not tools.policy.allows(spec.capability):
                return ProposalVerdict(False, f"step {index}: capability blocked for {step.tool}")
        return ProposalVerdict(True, "proposal accepted by executive")

    def reinforce(self, action: str, reward: float) -> None:
        self.kernel.reinforce(action, reward=reward)

    def reinforce_feedback(self, event: FeedbackEvent) -> float:
        if not event.learnable:
            return 0.0
        return self.kernel.reinforce_feedback(
            event.action,
            event.vector,
            learning_scale=event.learning_scale,
        )

    def consume_feedback(self, event: FeedbackEvent) -> tuple[float, ExecutiveDecision]:
        """Make feedback both a learning signal and a sensory event."""
        applied = self.reinforce_feedback(event)
        decision = self.perceive(event.vector.to_stimuli())
        return applied, decision
