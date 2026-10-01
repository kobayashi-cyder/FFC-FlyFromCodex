from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any

from .models import ResultStatus, Stimulus, ToolResult


def _clamp(value: float, low: float = -1.0, high: float = 1.0) -> float:
    return max(low, min(high, float(value)))


class TestTier(str, Enum):
    TRAINING = "training"
    REGRESSION = "regression"
    HOLDOUT = "holdout"


@dataclass(slots=True)
class RewardVector:
    success: float = 0.0
    quality: float = 0.0
    quality_gain: float = 0.0
    novelty: float = 0.0
    regression: float = 0.0
    hard_failure: float = 0.0
    fidelity: float = 0.0
    composition: float = 0.0
    structure: float = 0.0
    render: float = 0.0
    details: dict[str, Any] = field(default_factory=dict)

    def normalized(self) -> "RewardVector":
        return RewardVector(
            success=_clamp(self.success),
            quality=_clamp(self.quality),
            quality_gain=_clamp(self.quality_gain),
            novelty=_clamp(self.novelty),
            regression=_clamp(self.regression, 0.0, 1.0),
            hard_failure=_clamp(self.hard_failure, 0.0, 1.0),
            fidelity=_clamp(self.fidelity),
            composition=_clamp(self.composition),
            structure=_clamp(self.structure),
            render=_clamp(self.render),
            details=dict(self.details),
        )

    def scalar(self) -> float:
        v = self.normalized()
        score = (
            0.18 * v.success
            + 0.20 * v.quality
            + 0.12 * v.quality_gain
            + 0.05 * v.novelty
            + 0.12 * v.fidelity
            + 0.08 * v.composition
            + 0.12 * v.structure
            + 0.05 * v.render
            - 0.18 * v.regression
            - 0.35 * v.hard_failure
        )
        return _clamp(score)

    def to_stimuli(self) -> list[Stimulus]:
        v = self.normalized()
        positive = max(0.0, v.scalar())
        error = max(0.0, -v.scalar())
        return [
            Stimulus("feedback_quality", positive, 1.0, {"vector": self.to_dict()}),
            Stimulus("feedback_error", error, 1.0, {"vector": self.to_dict()}),
            Stimulus("novelty", max(0.0, v.novelty), 0.6, {"source": "feedback"}),
            Stimulus("feedback_regression", v.regression, 1.0, {"source": "feedback"}),
            Stimulus("feedback_hard_failure", v.hard_failure, 1.0, {"source": "feedback"}),
        ]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(slots=True)
class FeedbackEvent:
    action: str
    source: str
    tier: TestTier
    vector: RewardVector
    learnable: bool
    learning_scale: float
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def reward(self) -> float:
        return self.vector.scalar()

    def to_dict(self) -> dict[str, Any]:
        return {
            "action": self.action,
            "source": self.source,
            "tier": self.tier.value,
            "vector": self.vector.to_dict(),
            "reward": self.reward,
            "learnable": self.learnable,
            "learning_scale": self.learning_scale,
            "metadata": dict(self.metadata),
        }


class FeedbackEncoder:
    """Converts tool/quality/test outcomes into bounded connectome feedback."""

    _SCALE = {
        TestTier.TRAINING: 1.0,
        TestTier.REGRESSION: 0.35,
        TestTier.HOLDOUT: 0.0,
    }

    @staticmethod
    def _metric(value: Any) -> float:
        try:
            return _clamp(float(value) * 2.0 - 1.0)
        except (TypeError, ValueError):
            return 0.0

    @staticmethod
    def _quality_score(score: Any) -> float:
        try:
            value = float(score)
        except (TypeError, ValueError):
            return 0.0
        return _clamp((value - 72.0) / 28.0)

    @staticmethod
    def _gain(value: Any) -> float:
        try:
            return _clamp(float(value) / 30.0)
        except (TypeError, ValueError):
            return 0.0

    def from_quality(
        self,
        action: str,
        quality: dict[str, Any] | None,
        *,
        previous_score: float | None = None,
        tier: TestTier = TestTier.TRAINING,
        source: str = "quality",
    ) -> FeedbackEvent:
        q = quality or {}
        metrics = q.get("metrics") if isinstance(q.get("metrics"), dict) else {}
        hard = list(q.get("hardIssues") or [])
        score = q.get("score")
        gain = q.get("evolutionGain")
        if gain is None and previous_score is not None:
            try:
                gain = float(score) - float(previous_score)
            except (TypeError, ValueError):
                gain = 0.0
        passed = bool(q.get("pass", False))
        vector = RewardVector(
            success=1.0 if passed else -0.65,
            quality=self._quality_score(score),
            quality_gain=self._gain(gain),
            novelty=_clamp(float(q.get("novelty", 0.0) or 0.0)),
            regression=1.0 if q.get("regression") else 0.0,
            hard_failure=1.0 if hard else 0.0,
            fidelity=self._metric(metrics.get("subjectCoverage")),
            composition=self._metric(metrics.get("composition")),
            structure=self._metric(metrics.get("categoryIntegrity")),
            render=self._metric(metrics.get("render")),
            details={"score": score, "hardIssues": hard, "issues": list(q.get("issues") or [])},
        ).normalized()
        return self._event(action, source, tier, vector, {"quality": q})

    def from_test(
        self,
        name: str,
        passed: bool,
        *,
        tier: TestTier,
        action: str = "delegate",
        failure: str | None = None,
        novelty: float = 0.0,
    ) -> FeedbackEvent:
        vector = RewardVector(
            success=1.0 if passed else -1.0,
            quality=0.25 if passed else -0.45,
            novelty=_clamp(novelty, 0.0, 1.0),
            regression=1.0 if (tier == TestTier.REGRESSION and not passed) else 0.0,
            hard_failure=0.85 if not passed else 0.0,
            details={"test": name, "failure": failure},
        ).normalized()
        return self._event(action, f"test:{name}", tier, vector, {"passed": bool(passed), "failure": failure})

    def from_tool_result(
        self,
        action: str,
        tool: str,
        result: ToolResult,
        *,
        tier: TestTier = TestTier.TRAINING,
    ) -> FeedbackEvent:
        status_reward = {
            ResultStatus.SUCCESS: 1.0,
            ResultStatus.RETRY: -0.25,
            ResultStatus.FAILED: -0.85,
            ResultStatus.BLOCKED: -1.0,
            ResultStatus.UNSUPPORTED: -0.8,
        }.get(result.status, -0.5)

        payload = result.output if isinstance(result.output, dict) else {}
        quality = payload.get("validation") if isinstance(payload.get("validation"), dict) else None
        if quality is None and any(k in payload for k in ("score", "hardIssues", "metrics", "pass")):
            quality = payload

        if quality is not None:
            event = self.from_quality(action, quality, tier=tier, source=tool)
            event.vector.success = status_reward
            if result.status != ResultStatus.SUCCESS:
                event.vector.hard_failure = max(event.vector.hard_failure, 0.65)
            event.metadata.update({"status": result.status.value, "error": result.error})
            return event

        vector = RewardVector(
            success=status_reward,
            quality=0.15 if result.status == ResultStatus.SUCCESS else -0.25,
            hard_failure=1.0 if result.status in (ResultStatus.FAILED, ResultStatus.BLOCKED, ResultStatus.UNSUPPORTED) else 0.0,
            details={"status": result.status.value, "error": result.error},
        ).normalized()
        return self._event(action, tool, tier, vector, {"status": result.status.value, "error": result.error})

    def _event(
        self,
        action: str,
        source: str,
        tier: TestTier,
        vector: RewardVector,
        metadata: dict[str, Any],
    ) -> FeedbackEvent:
        scale = self._SCALE[tier]
        return FeedbackEvent(
            action=action,
            source=source,
            tier=tier,
            vector=vector.normalized(),
            learnable=scale > 0.0,
            learning_scale=scale,
            metadata=metadata,
        )
