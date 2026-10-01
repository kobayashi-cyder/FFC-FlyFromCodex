from __future__ import annotations

import json
import re
from typing import Any, Callable, Protocol

from .models import Goal, PlanProposal, PlannerResult, PlanStep, ResultStatus, ToolContext
from .tools import ToolBus


class PlannerAdapter(Protocol):
    def propose(self, goal: Goal, memory: list[dict[str, Any]], tools: ToolBus) -> PlannerResult: ...


class RulePlanner:
    """Small deterministic planner for commands the runtime can verify locally."""

    _CALC = re.compile(r"(?:calc|calculate|計算)\s*[:：]?\s*(.+)", re.IGNORECASE | re.DOTALL)
    _SAY = re.compile(r"(?:say|話す|表示)\s*[:：]?\s*(.+)", re.IGNORECASE | re.DOTALL)
    _TOOL = re.compile(r"(?:tool|ツール)\s*[:：]?\s*([\w.\-]+)(?:\s+(.+))?", re.IGNORECASE | re.DOTALL)

    def propose(self, goal: Goal, memory: list[dict[str, Any]], tools: ToolBus) -> PlannerResult:
        text = goal.text.strip()
        match = self._CALC.fullmatch(text)
        if match:
            return PlannerResult(
                ResultStatus.SUCCESS,
                PlanProposal([PlanStep("calculate", {"expression": match.group(1)}, "machine arithmetic")], source="rule"),
            )
        match = self._SAY.fullmatch(text)
        if match:
            return PlannerResult(
                ResultStatus.SUCCESS,
                PlanProposal([PlanStep("human.say", {"text": match.group(1)}, "human-facing output")], source="rule"),
            )
        match = self._TOOL.fullmatch(text)
        if match:
            name, raw_args = match.group(1), match.group(2)
            if raw_args:
                try:
                    args = json.loads(raw_args)
                except json.JSONDecodeError as exc:
                    return PlannerResult(ResultStatus.FAILED, error=f"invalid tool JSON: {exc}")
                if not isinstance(args, dict):
                    return PlannerResult(ResultStatus.FAILED, error="tool arguments must be a JSON object")
            else:
                args = {}
            return PlannerResult(
                ResultStatus.SUCCESS,
                PlanProposal([PlanStep(name, args, f"explicit tool request: {name}")], source="rule"),
            )
        return PlannerResult(ResultStatus.UNSUPPORTED, error="no deterministic plan for goal")


class MachinePlannerAdapter:
    """Calls a planner tool to obtain a proposal; the executive still validates it."""

    def __init__(
        self,
        tool_name: str = "planner.propose",
        context_provider: Callable[[], dict[str, Any]] | None = None,
    ):
        self.tool_name = tool_name
        self.context_provider = context_provider

    def propose(self, goal: Goal, memory: list[dict[str, Any]], tools: ToolBus) -> PlannerResult:
        if tools.spec(self.tool_name) is None:
            return PlannerResult(ResultStatus.UNSUPPORTED, error="planner tool is not connected")
        payload = {
            "goal": goal.text,
            "memory": memory,
            "available_tools": tools.names(),
            "tool_catalog": tools.manifest(),
        }
        if self.context_provider is not None:
            try:
                payload["autonomy"] = self.context_provider()
            except Exception as exc:
                payload["autonomy"] = {"error": f"{type(exc).__name__}: {exc}"}
        result = tools.execute(
            self.tool_name,
            payload,
            ToolContext(thread_id=goal.thread_id, goal_id=goal.id),
        )
        if not result.ok:
            return PlannerResult(result.status, error=result.error)
        try:
            proposal = proposal_from_data(result.output, source=self.tool_name)
        except (TypeError, ValueError, KeyError) as exc:
            return PlannerResult(ResultStatus.FAILED, error=f"invalid planner proposal: {exc}")
        return PlannerResult(ResultStatus.SUCCESS, proposal=proposal)


class CompositePlanner:
    def __init__(self, *planners: PlannerAdapter):
        self.planners = planners

    def propose(self, goal: Goal, memory: list[dict[str, Any]], tools: ToolBus) -> PlannerResult:
        last = PlannerResult(ResultStatus.UNSUPPORTED, error="no planner accepted goal")
        for planner in self.planners:
            result = planner.propose(goal, memory, tools)
            if result.status == ResultStatus.UNSUPPORTED:
                last = result
                continue
            return result
        return last


def proposal_from_data(data: Any, source: str = "external") -> PlanProposal:
    if not isinstance(data, dict):
        raise TypeError("proposal must be an object")
    raw_steps = data.get("steps")
    if not isinstance(raw_steps, list) or not raw_steps:
        raise ValueError("proposal requires non-empty steps")
    steps: list[PlanStep] = []
    for raw in raw_steps:
        if not isinstance(raw, dict) or not isinstance(raw.get("tool"), str):
            raise ValueError("each step requires a tool")
        args = raw.get("args", {})
        if not isinstance(args, dict):
            raise ValueError("step args must be an object")
        steps.append(
            PlanStep(
                tool=raw["tool"],
                args=args,
                description=str(raw.get("description", "")),
                max_retries=int(raw.get("max_retries", 2)),
                expected_effect=str(raw.get("expected_effect", "")),
            )
        )
    return PlanProposal(
        steps=steps,
        rationale=str(data.get("rationale", "")),
        confidence=float(data.get("confidence", 1.0)),
        source=source,
    )
