from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any

from .feedback import FeedbackEvent, TestTier
from .tools import ToolBus


@dataclass(slots=True)
class SkillProfile:
    tool: str
    attempts: int = 0
    successes: int = 0
    failures: int = 0
    reward_ema: float = 0.0
    training_passes: int = 0
    regression_passes: int = 0
    regression_failures: int = 0
    holdout_passes: int = 0
    holdout_failures: int = 0
    promoted: bool = False
    quarantined: bool = False
    last_source: str = ""
    last_reward: float = 0.0

    @property
    def success_rate(self) -> float:
        return self.successes / self.attempts if self.attempts else 0.0

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["success_rate"] = self.success_rate
        return data


class AutonomousSkillLearner:
    """Learns which existing tools are reliable and gates autonomous use.

    This does not invent or execute arbitrary code. It turns observed tool,
    quality, and test outcomes into persistent skill competence. Skills are
    promoted only after repeated success, and regression/holdout failures can
    quarantine them.
    """

    def __init__(
        self,
        *,
        min_attempts: int = 3,
        min_success_rate: float = 0.67,
        min_reward_ema: float = 0.05,
    ):
        self.min_attempts = max(1, int(min_attempts))
        self.min_success_rate = max(0.0, min(1.0, float(min_success_rate)))
        self.min_reward_ema = float(min_reward_ema)
        self._profiles: dict[str, SkillProfile] = {}

    def profile(self, tool: str) -> SkillProfile:
        key = str(tool or "delegate")
        if key not in self._profiles:
            self._profiles[key] = SkillProfile(tool=key)
        return self._profiles[key]

    def observe(self, tool: str, event: FeedbackEvent) -> SkillProfile:
        p = self.profile(tool)
        p.last_source = event.source
        p.last_reward = event.reward

        if event.source.startswith("test:"):
            passed = bool(event.metadata.get("passed"))
            if event.tier == TestTier.TRAINING:
                p.training_passes += int(passed)
            elif event.tier == TestTier.REGRESSION:
                p.regression_passes += int(passed)
                p.regression_failures += int(not passed)
            elif event.tier == TestTier.HOLDOUT:
                p.holdout_passes += int(passed)
                p.holdout_failures += int(not passed)
        else:
            p.attempts += 1
            if event.reward >= 0:
                p.successes += 1
            else:
                p.failures += 1

        alpha = 0.22
        p.reward_ema = (1.0 - alpha) * p.reward_ema + alpha * event.reward

        severe = event.vector.hard_failure >= 0.9
        regressed = p.regression_failures > 0
        holdout_failed = p.holdout_failures > 0
        p.quarantined = bool(severe or regressed or holdout_failed)

        eligible = (
            p.attempts >= self.min_attempts
            and p.success_rate >= self.min_success_rate
            and p.reward_ema >= self.min_reward_ema
            and not p.quarantined
        )
        p.promoted = bool(eligible)
        return p

    def autonomous_tools(self, tools: ToolBus) -> list[str]:
        return [
            name for name in tools.names()
            if tools.executable(name)
            and self.profile(name).promoted
            and not self.profile(name).quarantined
        ]

    def candidate_tools(self, tools: ToolBus) -> list[str]:
        return [
            name for name in tools.names()
            if tools.executable(name) and not self.profile(name).quarantined
        ]

    def snapshot(self) -> dict[str, Any]:
        return {
            "config": {
                "min_attempts": self.min_attempts,
                "min_success_rate": self.min_success_rate,
                "min_reward_ema": self.min_reward_ema,
            },
            "profiles": {name: p.to_dict() for name, p in self._profiles.items()},
        }

    def restore(self, data: dict[str, Any] | None) -> None:
        if not isinstance(data, dict):
            return
        raw = data.get("profiles")
        if not isinstance(raw, dict):
            return
        for name, payload in raw.items():
            if not isinstance(payload, dict):
                continue
            fields = {
                k: v for k, v in payload.items()
                if k in SkillProfile.__dataclass_fields__
            }
            fields["tool"] = str(fields.get("tool") or name)
            try:
                self._profiles[str(name)] = SkillProfile(**fields)
            except (TypeError, ValueError):
                continue

    def status(self, tools: ToolBus) -> dict[str, Any]:
        profiles = {name: self.profile(name).to_dict() for name in tools.names()}
        return {
            "autonomous_tools": self.autonomous_tools(tools),
            "candidate_tools": self.candidate_tools(tools),
            "profiles": profiles,
        }
