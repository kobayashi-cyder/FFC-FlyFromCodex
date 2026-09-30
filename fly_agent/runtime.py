from __future__ import annotations

from collections import deque
import json
from pathlib import Path
import re
from typing import Callable, Iterable

from .connectome import GraphConnectomeKernel, default_connectome
from .memory import EpisodeMemory
from .models import AgentEvent, Goal, GoalStatus, PlanStep, Stimulus
from .tools import ToolRegistry, default_tools


class RequirementContract:
    """Stable user requirements that remain valid across connectome backends."""

    def __init__(
        self,
        *,
        explain_actions: bool = True,
        persistent_memory: bool = True,
        stop_resume: bool = True,
        continue_until_terminal: bool = True,
        max_goal_attempts: int = 3,
        reflex_threshold: float = 0.82,
    ):
        self.explain_actions = explain_actions
        self.persistent_memory = persistent_memory
        self.stop_resume = stop_resume
        self.continue_until_terminal = continue_until_terminal
        self.max_goal_attempts = max_goal_attempts
        self.reflex_threshold = reflex_threshold


class FlyMachineAgent:
    """Hybrid agent: connectome-speed reflexes plus a machine/tool prosthetic layer."""

    def __init__(
        self,
        state_dir: str | Path = ".fly_agent_state",
        connectome: GraphConnectomeKernel | None = None,
        tools: ToolRegistry | None = None,
        output: Callable[[str], None] = print,
        contract: RequirementContract | None = None,
    ):
        self.state_dir = Path(state_dir)
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.checkpoint = self.state_dir / "checkpoint.json"
        self.memory = EpisodeMemory(self.state_dir / "episodes.jsonl")
        self.connectome = connectome or default_connectome()
        self.output = output
        self.tools = tools or default_tools(output=output)
        self.contract = contract or RequirementContract()
        self.goals: deque[Goal] = deque()
        self.paused = False
        self.stopped = False
        self._restore()

    def emit(self, kind: str, message: str, **data) -> None:
        event = AgentEvent(kind, message, data)
        self.memory.append(event)
        if self.contract.explain_actions:
            self.output(f"[{kind}] {message}")

    def submit_goal(self, text: str, priority: int = 50) -> Goal:
        goal = Goal(text=text.strip(), priority=priority)
        self.goals.append(goal)
        self.goals = deque(sorted(self.goals, key=lambda g: (-g.priority, g.created_at)))
        self.emit("goal", f"accepted: {goal.text}", goal_id=goal.id, priority=priority)
        self._save()
        return goal

    def pause(self) -> None:
        self.paused = True
        self.emit("control", "paused")
        self._save()

    def resume(self) -> None:
        self.paused = False
        self.stopped = False
        self.emit("control", "resumed")
        self._save()

    def stop(self) -> None:
        self.stopped = True
        self.emit("control", "stopped; checkpoint preserved")
        self._save()

    def perceive(self, stimuli: Iterable[Stimulus]) -> None:
        stimuli = list(stimuli)
        intent = self.connectome.route(stimuli)
        if intent and intent.confidence >= self.contract.reflex_threshold and intent.action != "delegate":
            self.emit("reflex", f"{intent.action} ({intent.confidence:.2f})", action=intent.action)
            device_tool = f"device.{intent.action}"
            if device_tool in self.tools.names():
                result = self.tools.execute(device_tool, {"intent": intent.payload})
                self.emit("tool", f"{device_tool}: {'ok' if result.ok else 'failed'}", error=result.error)

    def step(self) -> bool:
        if self.paused or self.stopped or not self.goals:
            return False
        goal = self.goals[0]
        goal.status = GoalStatus.RUNNING
        goal.attempts += 1
        plan = self._plan(goal)
        self.emit("plan", plan.description or plan.tool, goal_id=goal.id, tool=plan.tool)
        result = self.tools.execute(plan.tool, plan.args)
        if result.ok:
            goal.status = GoalStatus.DONE
            self.emit("done", f"goal completed: {goal.text}", goal_id=goal.id, output=result.output)
            self.goals.popleft()
            self.connectome.reinforce("delegate", reward=0.2)
        else:
            self.emit("error", f"tool failed: {plan.tool}", goal_id=goal.id, error=result.error)
            if goal.attempts >= self.contract.max_goal_attempts:
                goal.status = GoalStatus.FAILED
                self.goals.popleft()
                self.emit("failed", f"goal failed after {goal.attempts} attempts: {goal.text}", goal_id=goal.id)
            else:
                goal.status = GoalStatus.QUEUED
        self._save()
        return True

    def run(self, max_steps: int = 100) -> int:
        count = 0
        while count < max_steps and not self.paused and not self.stopped and self.goals:
            if not self.step():
                break
            count += 1
            if not self.contract.continue_until_terminal:
                break
        return count

    def _plan(self, goal: Goal) -> PlanStep:
        text = goal.text.strip()
        m = re.fullmatch(r"(?:calc|calculate|計算)\s*[:：]?\s*(.+)", text, flags=re.IGNORECASE)
        if m:
            return PlanStep("calculate", {"expression": m.group(1)}, "delegate arithmetic to machine calculator")
        m = re.fullmatch(r"(?:say|話す|表示)\s*[:：]?\s*(.+)", text, flags=re.IGNORECASE)
        if m:
            return PlanStep("human.say", {"text": m.group(1)}, "send output to the human-facing channel")
        if "planner.solve" in self.tools.names():
            return PlanStep(
                "planner.solve",
                {"goal": goal.text, "memory": self.memory.recall(goal.text)},
                "delegate abstract planning to machine planner",
            )
        return PlanStep(
            "human.say",
            {"text": f"未接続の高次要求: {goal.text}"},
            "surface unsupported high-level goal instead of pretending it was solved",
        )

    def status(self) -> dict:
        return {
            "paused": self.paused,
            "stopped": self.stopped,
            "queued_goals": [g.to_dict() for g in self.goals],
            "tools": self.tools.names(),
        }

    def _save(self) -> None:
        payload = {
            "paused": self.paused,
            "stopped": self.stopped,
            "goals": [g.to_dict() for g in self.goals],
        }
        self.checkpoint.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _restore(self) -> None:
        if not self.checkpoint.exists():
            return
        try:
            data = json.loads(self.checkpoint.read_text(encoding="utf-8"))
            self.paused = bool(data.get("paused", False))
            self.stopped = bool(data.get("stopped", False))
            self.goals = deque(Goal.from_dict(g) for g in data.get("goals", []))
        except (json.JSONDecodeError, TypeError, ValueError):
            self.goals = deque()
            self.paused = False
            self.stopped = False
