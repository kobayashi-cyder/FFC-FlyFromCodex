from __future__ import annotations

from dataclasses import asdict, dataclass, field
import hashlib
import json
import math
from pathlib import Path
import re
import time
import unicodedata
from typing import Any

from .feedback import FeedbackEvent
from .models import Goal, PlanProposal, PlanStep, PlannerResult, ResultStatus
from .tools import ToolBus


_TOKEN = re.compile(r"[\wぁ-んァ-ン一-龥]+", re.UNICODE)
_NUMBER = re.compile(r"(?<!\w)[+-]?(?:\d+(?:\.\d+)?|\.\d+)(?!\w)")
_WS = re.compile(r"\s+")

_STOP = {
    "を", "に", "へ", "で", "と", "が", "は", "の", "する", "して", "ください", "お願い",
    "the", "a", "an", "to", "for", "of", "and", "please",
}


def _clamp(value: float, low: float = -1.0, high: float = 1.0) -> float:
    return max(low, min(high, float(value)))


def _normalize_goal(text: str) -> str:
    s = unicodedata.normalize("NFKC", str(text or "")).strip().lower()
    s = _NUMBER.sub("#", s)
    return _WS.sub(" ", s)


def _tokens(text: str) -> tuple[str, ...]:
    values = []
    for token in _TOKEN.findall(_normalize_goal(text)):
        t = token.lower()
        if len(t) <= 1 or t in _STOP:
            continue
        values.append(t)
    return tuple(sorted(set(values)))


def _similarity(a: tuple[str, ...], b: tuple[str, ...]) -> float:
    aa, bb = set(a), set(b)
    if not aa and not bb:
        return 1.0
    if not aa or not bb:
        return 0.0
    inter = len(aa & bb)
    union = len(aa | bb)
    containment = inter / max(1, min(len(aa), len(bb)))
    jaccard = inter / max(1, union)
    return 0.62 * containment + 0.38 * jaccard


def _template_value(value: Any, goal: str) -> Any:
    if isinstance(value, str):
        if value == goal:
            return {"__goal__": True}
        if goal and goal in value:
            return value.replace(goal, "{{goal}}")
        return value
    if isinstance(value, list):
        return [_template_value(v, goal) for v in value]
    if isinstance(value, dict):
        return {str(k): _template_value(v, goal) for k, v in value.items()}
    return value


def _instantiate_value(value: Any, goal: str) -> Any:
    if isinstance(value, dict):
        if value.get("__goal__") is True and len(value) == 1:
            return goal
        return {str(k): _instantiate_value(v, goal) for k, v in value.items()}
    if isinstance(value, list):
        return [_instantiate_value(v, goal) for v in value]
    if isinstance(value, str):
        return value.replace("{{goal}}", goal)
    return value


@dataclass(slots=True)
class LearnedSkill:
    id: str
    label: str
    goal_pattern: str
    signature: tuple[str, ...]
    steps: list[dict[str, Any]]
    attempts: int = 0
    successes: int = 0
    failures: int = 0
    reward_sum: float = 0.0
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)

    @property
    def average_reward(self) -> float:
        return self.reward_sum / max(1, self.attempts)

    @property
    def reliability(self) -> float:
        return self.successes / max(1, self.attempts)

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["signature"] = list(self.signature)
        data["average_reward"] = self.average_reward
        data["reliability"] = self.reliability
        return data

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "LearnedSkill":
        data = dict(raw)
        data.pop("average_reward", None)
        data.pop("reliability", None)
        data["signature"] = tuple(str(x) for x in data.get("signature", []))
        return cls(**data)


class SkillFabric:
    """Persistent capability discovery + compositional skill acquisition.

    The fabric never executes arbitrary learned source code. It promotes successful,
    already-authorized ToolBus plans into reusable declarative skills. Every learned
    step is re-validated by ConnectomeExecutive before execution.
    """

    SCHEMA = 1

    def __init__(
        self,
        path: str | Path | None = None,
        *,
        min_successes: int = 2,
        min_reward: float = 0.05,
        min_reliability: float = 0.66,
        match_threshold: float = 0.58,
        max_skills: int = 128,
    ):
        self.path = Path(path) if path else None
        self.min_successes = max(1, int(min_successes))
        self.min_reward = float(min_reward)
        self.min_reliability = float(min_reliability)
        self.match_threshold = float(match_threshold)
        self.max_skills = max(8, int(max_skills))
        self.tools: dict[str, dict[str, Any]] = {}
        self.candidates: dict[str, dict[str, Any]] = {}
        self.skills: dict[str, LearnedSkill] = {}
        self.episodes: list[dict[str, Any]] = []
        self._load()

    def sync_tools(self, tools: ToolBus) -> None:
        now = time.time()
        for item in tools.manifest():
            name = str(item["name"])
            row = self.tools.setdefault(
                name,
                {
                    "name": name,
                    "attempts": 0,
                    "successes": 0,
                    "failures": 0,
                    "reward_sum": 0.0,
                    "last_reward": 0.0,
                },
            )
            row.update(
                {
                    "capability": item["capability"],
                    "description": item.get("description", ""),
                    "resource": item.get("resource"),
                    "side_effect": bool(item.get("side_effect", False)),
                    "executable": bool(item.get("executable", False)),
                    "updated_at": now,
                }
            )
        self._save()

    def observe_tool(
        self,
        name: str,
        *,
        success: bool,
        reward: float,
        learnable: bool = True,
    ) -> None:
        if not learnable:
            return
        row = self.tools.setdefault(
            str(name),
            {
                "name": str(name),
                "attempts": 0,
                "successes": 0,
                "failures": 0,
                "reward_sum": 0.0,
                "last_reward": 0.0,
                "capability": "unknown",
                "description": "",
                "resource": None,
                "side_effect": False,
                "executable": False,
            },
        )
        row["attempts"] = int(row.get("attempts", 0)) + 1
        if success:
            row["successes"] = int(row.get("successes", 0)) + 1
        else:
            row["failures"] = int(row.get("failures", 0)) + 1
        bounded = _clamp(reward)
        row["reward_sum"] = float(row.get("reward_sum", 0.0)) + bounded
        row["last_reward"] = bounded
        row["updated_at"] = time.time()
        self._save()

    def consume_feedback(self, target_tool: str, event: FeedbackEvent) -> None:
        self.observe_tool(
            target_tool,
            success=event.vector.success > 0.0,
            reward=event.reward * event.learning_scale,
            learnable=event.learnable,
        )

    def observe_plan(
        self,
        goal: Goal,
        proposal: PlanProposal,
        *,
        success: bool,
        reward: float,
        learnable: bool = True,
    ) -> LearnedSkill | None:
        if not learnable or not proposal.steps:
            return None
        if proposal.source.startswith("learned-skill:"):
            skill_id = proposal.source.split(":", 1)[1]
            skill = self.skills.get(skill_id)
            if skill:
                skill.attempts += 1
                if success:
                    skill.successes += 1
                else:
                    skill.failures += 1
                skill.reward_sum += _clamp(reward)
                skill.updated_at = time.time()
                self._append_episode(goal, proposal, success, reward, skill.id)
                self._save()
            return None

        plan_tools = [step.tool for step in proposal.steps]
        canonical = _normalize_goal(goal.text)
        fingerprint = hashlib.sha256(
            (canonical + "\n" + "\n".join(plan_tools)).encode("utf-8")
        ).hexdigest()[:16]
        row = self.candidates.setdefault(
            fingerprint,
            {
                "id": fingerprint,
                "goal_pattern": canonical,
                "signature": list(_tokens(goal.text)),
                "steps": [
                    {
                        "tool": step.tool,
                        "args": _template_value(step.args, goal.text),
                        "description": step.description,
                        "max_retries": int(step.max_retries),
                        "expected_effect": step.expected_effect,
                    }
                    for step in proposal.steps
                ],
                "attempts": 0,
                "successes": 0,
                "failures": 0,
                "reward_sum": 0.0,
                "created_at": time.time(),
                "updated_at": time.time(),
            },
        )
        row["attempts"] += 1
        row["successes" if success else "failures"] += 1
        row["reward_sum"] += _clamp(reward)
        row["updated_at"] = time.time()
        self._append_episode(goal, proposal, success, reward, None)

        attempts = max(1, int(row["attempts"]))
        avg = float(row["reward_sum"]) / attempts
        reliability = int(row["successes"]) / attempts
        promoted: LearnedSkill | None = None
        if (
            int(row["successes"]) >= self.min_successes
            and reliability >= self.min_reliability
            and avg >= self.min_reward
        ):
            skill_id = "skill-" + hashlib.sha256(
                (row["goal_pattern"] + "|" + "|".join(plan_tools)).encode("utf-8")
            ).hexdigest()[:10]
            skill = self.skills.get(skill_id)
            if skill is None:
                skill = LearnedSkill(
                    id=skill_id,
                    label=" → ".join(plan_tools),
                    goal_pattern=str(row["goal_pattern"]),
                    signature=tuple(str(x) for x in row["signature"]),
                    steps=list(row["steps"]),
                    attempts=attempts,
                    successes=int(row["successes"]),
                    failures=int(row["failures"]),
                    reward_sum=float(row["reward_sum"]),
                    created_at=float(row["created_at"]),
                    updated_at=time.time(),
                )
                self.skills[skill_id] = skill
                promoted = skill
                self._trim_skills()
            else:
                skill.attempts = attempts
                skill.successes = int(row["successes"])
                skill.failures = int(row["failures"])
                skill.reward_sum = float(row["reward_sum"])
                skill.updated_at = time.time()
        self._save()
        return promoted

    def proposal_for(self, goal: Goal, tools: ToolBus) -> PlanProposal | None:
        self.sync_tools(tools)
        goal_sig = _tokens(goal.text)
        goal_norm = _normalize_goal(goal.text)
        ranked: list[tuple[float, LearnedSkill]] = []
        for skill in self.skills.values():
            if not all(tools.executable(str(step.get("tool", ""))) for step in skill.steps):
                continue
            similarity = 1.0 if skill.goal_pattern == goal_norm else _similarity(goal_sig, skill.signature)
            reliability = max(0.15, skill.reliability)
            reward_term = max(0.15, (skill.average_reward + 1.0) / 2.0)
            score = similarity * (0.72 + 0.18 * reliability + 0.10 * reward_term)
            if score >= self.match_threshold:
                ranked.append((score, skill))
        if not ranked:
            return None
        ranked.sort(key=lambda item: (item[0], item[1].reliability, item[1].average_reward), reverse=True)
        score, skill = ranked[0]
        steps = [
            PlanStep(
                tool=str(raw["tool"]),
                args=_instantiate_value(raw.get("args", {}), goal.text),
                description=str(raw.get("description", "")) or f"learned skill {skill.id}",
                max_retries=int(raw.get("max_retries", 2)),
                expected_effect=str(raw.get("expected_effect", "")),
            )
            for raw in skill.steps
        ]
        return PlanProposal(
            steps=steps,
            rationale=f"reused acquired skill {skill.id} (match={score:.3f})",
            confidence=_clamp(0.58 + score * 0.38, 0.0, 0.99),
            source=f"learned-skill:{skill.id}",
        )

    def planner_context(self, limit: int = 24) -> dict[str, Any]:
        tools = []
        for row in self.tools.values():
            attempts = max(1, int(row.get("attempts", 0)))
            tools.append(
                {
                    "name": row["name"],
                    "capability": row.get("capability"),
                    "description": row.get("description", ""),
                    "executable": bool(row.get("executable", False)),
                    "attempts": int(row.get("attempts", 0)),
                    "success_rate": int(row.get("successes", 0)) / attempts,
                    "average_reward": float(row.get("reward_sum", 0.0)) / attempts,
                }
            )
        tools.sort(key=lambda x: (x["executable"], x["average_reward"], x["success_rate"]), reverse=True)
        skills = sorted(
            (s.to_dict() for s in self.skills.values()),
            key=lambda x: (x["reliability"], x["average_reward"], x["updated_at"]),
            reverse=True,
        )
        return {
            "schema": self.SCHEMA,
            "discovered_tools": len(self.tools),
            "learned_skills": len(self.skills),
            "tools": tools[:limit],
            "skills": skills[:limit],
        }

    def status(self) -> dict[str, Any]:
        ctx = self.planner_context(limit=12)
        ctx.update(
            {
                "candidate_skills": len(self.candidates),
                "episodes": len(self.episodes),
                "promotion": {
                    "min_successes": self.min_successes,
                    "min_reward": self.min_reward,
                    "min_reliability": self.min_reliability,
                    "match_threshold": self.match_threshold,
                },
            }
        )
        return ctx

    def _append_episode(
        self,
        goal: Goal,
        proposal: PlanProposal,
        success: bool,
        reward: float,
        skill_id: str | None,
    ) -> None:
        self.episodes.append(
            {
                "time": time.time(),
                "goal": _normalize_goal(goal.text),
                "tools": [step.tool for step in proposal.steps],
                "success": bool(success),
                "reward": _clamp(reward),
                "source": proposal.source,
                "skill_id": skill_id,
            }
        )
        if len(self.episodes) > 256:
            self.episodes = self.episodes[-256:]

    def _trim_skills(self) -> None:
        if len(self.skills) <= self.max_skills:
            return
        ordered = sorted(
            self.skills.values(),
            key=lambda s: (s.reliability, s.average_reward, s.updated_at),
            reverse=True,
        )
        self.skills = {s.id: s for s in ordered[: self.max_skills]}

    def _load(self) -> None:
        if not self.path or not self.path.exists():
            return
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        if not isinstance(raw, dict):
            return
        self.tools = raw.get("tools") if isinstance(raw.get("tools"), dict) else {}
        self.candidates = raw.get("candidates") if isinstance(raw.get("candidates"), dict) else {}
        self.episodes = raw.get("episodes") if isinstance(raw.get("episodes"), list) else []
        for item in raw.get("skills", []):
            try:
                skill = LearnedSkill.from_dict(item)
            except (TypeError, ValueError):
                continue
            self.skills[skill.id] = skill

    def _save(self) -> None:
        if not self.path:
            return
        payload = {
            "schema": self.SCHEMA,
            "tools": self.tools,
            "candidates": self.candidates,
            "skills": [s.to_dict() for s in self.skills.values()],
            "episodes": self.episodes[-256:],
        }
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.path.with_suffix(self.path.suffix + ".tmp")
        temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        temp.replace(self.path)


class LearnedSkillPlanner:
    """Planner adapter that reuses skills acquired from previously successful plans."""

    def __init__(self, fabric: SkillFabric):
        self.fabric = fabric

    def propose(self, goal: Goal, memory: list[dict[str, Any]], tools: ToolBus) -> PlannerResult:
        proposal = self.fabric.proposal_for(goal, tools)
        if proposal is None:
            return PlannerResult(ResultStatus.UNSUPPORTED, error="no acquired skill matched goal")
        return PlannerResult(ResultStatus.SUCCESS, proposal=proposal)
