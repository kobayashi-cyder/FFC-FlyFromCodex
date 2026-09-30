from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import time
from typing import Any, Callable, Iterable

from .body import BodyArbiter
from .checkpoint import AtomicCheckpointStore
from .connectome import GraphConnectomeKernel, default_connectome
from .executive import ConnectomeExecutive, DecisionType
from .ir import IRController
from .memory import MemoryFabric
from .models import (
    AgentEvent,
    Goal,
    GoalStatus,
    Observation,
    PlanProposal,
    PlannerResult,
    ResultStatus,
    Stimulus,
    ToolContext,
    ToolResult,
)
from .observer import Observer
from .planner import CompositePlanner, MachinePlannerAdapter, PlannerAdapter, RulePlanner
from .threads import ThreadRouter
from .tools import ToolBus, ToolPolicy, default_tools
from .voice import VoiceRouter


@dataclass(slots=True)
class RequirementContract:
    explain_actions: bool = True
    persistent_memory: bool = True
    stop_resume: bool = True
    continue_until_terminal: bool = True
    max_goal_attempts: int = 3
    reflex_threshold: float = 0.82
    max_plan_steps: int = 16
    resource_timeout: float = 0.0


class FlyMachineAgent:
    """Fly-led proxy agent with machine cognition and tool prostheses."""

    STATE_SCHEMA = 3
    EXECUTION_KEY = "_execution"

    def __init__(
        self,
        state_dir: str | Path = ".fly_agent_state",
        connectome: GraphConnectomeKernel | None = None,
        tools: ToolBus | None = None,
        output: Callable[[str], None] = print,
        contract: RequirementContract | None = None,
        planner: PlannerAdapter | None = None,
        policy: ToolPolicy | None = None,
    ):
        self.state_dir = Path(state_dir)
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.output = output
        self.contract = contract or RequirementContract()
        self.checkpoints = AtomicCheckpointStore(self.state_dir / "checkpoint.json")
        self.memory = MemoryFabric(self.state_dir / "episodes.jsonl")
        self.threads = ThreadRouter()
        self.body = tools.arbiter if tools is not None else BodyArbiter()
        self.tools = tools or default_tools(output=output, policy=policy, arbiter=self.body)
        self.ir = IRController(self.state_dir / "ir_store.json")
        self.ir.install(self.tools)
        self.executive = ConnectomeExecutive(
            connectome or default_connectome(),
            reflex_threshold=self.contract.reflex_threshold,
            max_plan_steps=self.contract.max_plan_steps,
        )
        self.planner = planner or CompositePlanner(RulePlanner(), MachinePlannerAdapter())
        self.voice = VoiceRouter(self.threads)
        self.observer = Observer(self.memory, self.executive)
        self.goals: list[Goal] = []
        self.paused = False
        self.stopped = False
        self._last_thread_id: str | None = None
        self._restore()
        if not self.threads.threads():
            self.threads.create("Main", listener_enabled=True)
            self._save()
        if self.checkpoints.recovered_from_backup:
            self.emit("recovery", "primary checkpoint was invalid; recovered last-known-good backup")

    def emit(self, kind: str, message: str, thread_id: str | None = None, **data) -> None:
        event = AgentEvent(kind, message, thread_id=thread_id, data=data)
        self.memory.append(event)
        if self.contract.explain_actions:
            label = self.threads.get(thread_id).label if thread_id and self.threads.get(thread_id) else None
            prefix = f"[thread {label}]" if label else ""
            self.output(f"{prefix}[{kind}] {message}")

    def new_thread(self, title: str | None = None, listener_enabled: bool = True) -> dict:
        thread = self.threads.create(title=title, listener_enabled=listener_enabled)
        self.emit("thread", f"created {thread.label}: {thread.title}", thread_id=thread.id)
        self._save()
        return thread.to_dict()

    def set_thread_listener(self, thread_ref: str, enabled: bool) -> None:
        thread = self.threads.get(thread_ref) or self.threads.get_by_label(thread_ref)
        if thread is None:
            raise KeyError(thread_ref)
        self.voice.set_listener(thread.id, enabled)
        self.emit("voice", f"listener {'enabled' if enabled else 'disabled'}", thread_id=thread.id)
        self._save()

    def submit_goal(self, text: str, priority: int = 50, thread_id: str | None = None) -> Goal:
        text = text.strip()
        if not text:
            raise ValueError("goal text cannot be empty")
        routed = self.threads.route(text, explicit_thread=thread_id)
        self.threads.record(routed.thread_id, f"USER {routed.text}")
        goal = Goal(text=routed.text, priority=int(priority), thread_id=routed.thread_id)
        self.goals.append(goal)
        self.emit("goal", f"accepted: {goal.text}", thread_id=goal.thread_id, goal_id=goal.id, priority=goal.priority)
        self._save()
        return goal

    def voice_input(self, transcript: str, priority: int = 50, explicit_thread: str | None = None) -> Goal:
        routed = self.voice.route_transcript(transcript, explicit_thread=explicit_thread)
        self.perceive(
            [Stimulus("human_command", 1.0, 1.0, {"text": routed.text})],
            thread_id=routed.thread_id,
        )
        return self.submit_goal(routed.text, priority=priority, thread_id=routed.thread_id)

    def create_ir(
        self,
        kind: str,
        data: Any,
        *,
        document_id: str | None = None,
        protected_paths: Iterable[str] = (),
        thread_ref: str | None = None,
    ) -> ToolResult:
        thread_id = self._resolve_thread_ref(thread_ref)
        result = self.tools.execute(
            "ir.create",
            {
                "kind": kind,
                "data": data,
                "document_id": document_id,
                "protected_paths": list(protected_paths),
            },
            ToolContext(thread_id=thread_id),
        )
        self._record_ir_result("ir.create", result, thread_id)
        return result

    def preview_ir_edit(
        self,
        document_id: str,
        patches: list[dict[str, Any]],
        *,
        scale: str = "LOCAL",
        expected_version: int | None = None,
        reason: str = "",
        thread_ref: str | None = None,
        transaction_id: str | None = None,
    ) -> ToolResult:
        thread_id = self._resolve_thread_ref(thread_ref)
        args = {
            "document_id": document_id,
            "patches": patches,
            "scale": scale,
            "expected_version": expected_version,
            "reason": reason,
        }
        if transaction_id:
            args["transaction_id"] = transaction_id
        result = self.tools.execute("ir.preview", args, ToolContext(thread_id=thread_id))
        self._record_ir_result("ir.preview", result, thread_id)
        return result

    def edit_ir(
        self,
        document_id: str,
        patches: list[dict[str, Any]],
        *,
        scale: str = "LOCAL",
        expected_version: int | None = None,
        reason: str = "",
        thread_ref: str | None = None,
        transaction_id: str | None = None,
    ) -> ToolResult:
        thread_id = self._resolve_thread_ref(thread_ref)
        args = {
            "document_id": document_id,
            "patches": patches,
            "scale": scale,
            "expected_version": expected_version,
            "reason": reason,
        }
        if transaction_id:
            args["transaction_id"] = transaction_id
        result = self.tools.execute("ir.patch", args, ToolContext(thread_id=thread_id))
        self._record_ir_result("ir.patch", result, thread_id)
        return result

    def undo_ir(
        self,
        document_id: str,
        *,
        expected_version: int | None = None,
        thread_ref: str | None = None,
    ) -> ToolResult:
        thread_id = self._resolve_thread_ref(thread_ref)
        result = self.tools.execute(
            "ir.undo",
            {"document_id": document_id, "expected_version": expected_version},
            ToolContext(thread_id=thread_id),
        )
        self._record_ir_result("ir.undo", result, thread_id)
        return result

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

    def cancel_goal(self, goal_id: str) -> bool:
        goal = self._goal(goal_id)
        if goal is None or goal.status in (GoalStatus.DONE, GoalStatus.CANCELLED):
            return False
        goal.status = GoalStatus.CANCELLED
        goal.updated_at = time.time()
        self.emit("cancel", f"goal cancelled: {goal.text}", thread_id=goal.thread_id, goal_id=goal.id)
        self._save()
        return True

    def retry_goal(self, goal_id: str) -> bool:
        goal = self._goal(goal_id)
        if goal is None or goal.status not in (GoalStatus.BLOCKED, GoalStatus.FAILED, GoalStatus.WAITING):
            return False
        execution = self._execution_state(goal)
        if execution is not None:
            execution["in_flight_step"] = None
            execution["in_flight_side_effect"] = False
            execution["in_flight_tool"] = None
        goal.status = GoalStatus.QUEUED
        goal.last_error = None
        goal.updated_at = time.time()
        self.emit("retry", f"goal re-queued: {goal.text}", thread_id=goal.thread_id, goal_id=goal.id)
        self._save()
        return True

    def perceive(self, stimuli: Iterable[Stimulus], thread_id: str | None = None) -> None:
        decision = self.executive.perceive(list(stimuli))
        if decision.type == DecisionType.IGNORE:
            self.emit("perception", "no action selected", thread_id=thread_id)
            return
        intent = decision.intent
        if decision.type == DecisionType.DELEGATE:
            self.emit("delegate", decision.reason, thread_id=thread_id, action=intent.action if intent else None)
            return
        assert intent is not None
        self.emit("reflex", f"{intent.action} ({intent.confidence:.2f})", thread_id=thread_id, action=intent.action)
        device_tool = f"device.{intent.action}"
        if self.tools.spec(device_tool) is None:
            self.emit("reflex-unbound", f"no device adapter registered for {device_tool}", thread_id=thread_id)
            return
        result = self.tools.execute(
            device_tool,
            {"intent": intent.payload},
            ToolContext(thread_id=thread_id),
            resource_timeout=self.contract.resource_timeout,
        )
        self.observer.record(Observation(None, thread_id, device_tool, result))
        self.emit("tool", f"{device_tool}: {result.status.value}", thread_id=thread_id, error=result.error)

    def step(self) -> bool:
        if self.paused or self.stopped:
            return False
        goal = self._next_goal()
        if goal is None:
            return False

        goal.status = GoalStatus.RUNNING
        goal.attempts += 1
        goal.updated_at = time.time()
        self._last_thread_id = goal.thread_id
        self._save()

        proposal = self._restore_proposal(goal)
        execution = self._execution_state(goal)
        if goal.status == GoalStatus.BLOCKED:
            self._save()
            return True
        if proposal is None:
            memory = self.memory.recall(goal.text, thread_id=goal.thread_id)
            planning = self.planner.propose(goal, memory, self.tools)
            if not planning.ok:
                self._apply_planning_failure(goal, planning)
                self._save()
                return True

            proposal = planning.proposal
            verdict = self.executive.evaluate_proposal(goal, proposal, self.tools)
            if not verdict.accepted:
                goal.status = GoalStatus.BLOCKED
                goal.last_error = verdict.reason
                goal.updated_at = time.time()
                self.emit("blocked", verdict.reason, thread_id=goal.thread_id, goal_id=goal.id)
                self._save()
                return True

            assert proposal is not None
            execution = {
                "proposal": proposal.to_dict(),
                "next_step_index": 0,
                "in_flight_step": None,
                "in_flight_side_effect": False,
                "in_flight_tool": None,
            }
            goal.metadata[self.EXECUTION_KEY] = execution
            self.emit(
                "plan",
                f"accepted {len(proposal.steps)} step proposal from {proposal.source}",
                thread_id=goal.thread_id,
                goal_id=goal.id,
                proposal_id=proposal.id,
                rationale=proposal.rationale,
            )
            self._save()
        else:
            assert execution is not None
            self.emit(
                "resume-plan",
                f"resume proposal from step {int(execution.get('next_step_index', 0))}",
                thread_id=goal.thread_id,
                goal_id=goal.id,
                proposal_id=proposal.id,
            )

        assert proposal is not None and execution is not None
        start = int(execution.get("next_step_index", 0))
        if start < 0 or start > len(proposal.steps):
            goal.status = GoalStatus.BLOCKED
            goal.last_error = "invalid persisted plan progress"
            self.emit("blocked", goal.last_error, thread_id=goal.thread_id, goal_id=goal.id)
            self._save()
            return True

        for index in range(start, len(proposal.steps)):
            plan_step = proposal.steps[index]
            spec = self.tools.spec(plan_step.tool)
            if spec is None:
                goal.status = GoalStatus.BLOCKED
                goal.last_error = f"tool disappeared before execution: {plan_step.tool}"
                self.emit("blocked", goal.last_error, thread_id=goal.thread_id, goal_id=goal.id)
                self._save()
                return True

            self.emit(
                "act",
                plan_step.description or plan_step.tool,
                thread_id=goal.thread_id,
                goal_id=goal.id,
                tool=plan_step.tool,
                step_index=index,
            )
            execution["in_flight_step"] = index
            execution["in_flight_side_effect"] = bool(spec.side_effect)
            execution["in_flight_tool"] = plan_step.tool
            self._save()

            result = self.tools.execute(
                plan_step.tool,
                plan_step.args,
                ToolContext(thread_id=goal.thread_id, goal_id=goal.id),
                resource_timeout=self.contract.resource_timeout,
            )
            self.observer.record(Observation(goal.id, goal.thread_id, plan_step.tool, result, index))

            execution["in_flight_step"] = None
            execution["in_flight_side_effect"] = False
            execution["in_flight_tool"] = None
            if not result.ok:
                execution["next_step_index"] = index
                self._apply_tool_failure(goal, result, plan_step.max_retries)
                self._save()
                return True

            execution["next_step_index"] = index + 1
            self._save()

        goal.status = GoalStatus.DONE
        goal.last_error = None
        goal.updated_at = time.time()
        goal.metadata.pop(self.EXECUTION_KEY, None)
        self.threads.record(goal.thread_id or self.threads.active_thread_id or "", f"DONE {goal.text}")
        self.executive.reinforce("delegate", 0.2)
        self.emit("done", f"goal completed: {goal.text}", thread_id=goal.thread_id, goal_id=goal.id)
        self._save()
        return True

    def run(self, max_steps: int = 100) -> int:
        count = 0
        while count < max_steps and not self.paused and not self.stopped:
            if not self.step():
                break
            count += 1
            if not self.contract.continue_until_terminal:
                break
        return count

    def status(self) -> dict:
        def goals_with(*statuses: GoalStatus) -> list[dict]:
            wanted = set(statuses)
            return [goal.to_dict() for goal in self.goals if goal.status in wanted]

        return {
            "schema": self.STATE_SCHEMA,
            "paused": self.paused,
            "stopped": self.stopped,
            "threads": self.threads.to_state(),
            "queued_goals": goals_with(GoalStatus.QUEUED, GoalStatus.RUNNING, GoalStatus.WAITING),
            "blocked_goals": goals_with(GoalStatus.BLOCKED),
            "failed_goals": goals_with(GoalStatus.FAILED),
            "completed_goals": goals_with(GoalStatus.DONE),
            "tools": self.tools.names(),
            "capabilities": sorted(cap.value for cap in self.tools.policy.allowed),
            "body": self.body.snapshot(),
            "ir_documents": self.ir.store.list_metadata(),
        }

    def _record_ir_result(self, tool: str, result: ToolResult, thread_id: str | None) -> None:
        self.observer.record(Observation(None, thread_id, tool, result))
        self.emit(
            "ir",
            f"{tool}: {result.status.value}",
            thread_id=thread_id,
            error=result.error,
            output=result.output,
        )

    def _resolve_thread_ref(self, thread_ref: str | None) -> str | None:
        if thread_ref is None:
            return self.threads.active_thread_id
        thread = self.threads.get(thread_ref) or self.threads.get_by_label(thread_ref)
        if thread is None:
            raise KeyError(thread_ref)
        return thread.id

    def _execution_state(self, goal: Goal) -> dict[str, Any] | None:
        raw = goal.metadata.get(self.EXECUTION_KEY)
        return raw if isinstance(raw, dict) else None

    def _restore_proposal(self, goal: Goal) -> PlanProposal | None:
        execution = self._execution_state(goal)
        if execution is None:
            return None
        raw = execution.get("proposal")
        if not isinstance(raw, dict):
            return None
        try:
            proposal = PlanProposal.from_dict(raw)
        except (TypeError, ValueError):
            goal.metadata.pop(self.EXECUTION_KEY, None)
            return None
        verdict = self.executive.evaluate_proposal(goal, proposal, self.tools)
        if not verdict.accepted:
            goal.status = GoalStatus.BLOCKED
            goal.last_error = f"persisted proposal no longer valid: {verdict.reason}"
            return None
        return proposal

    def _apply_planning_failure(self, goal: Goal, planning: PlannerResult) -> None:
        goal.last_error = planning.error
        goal.updated_at = time.time()
        if planning.status in (ResultStatus.UNSUPPORTED, ResultStatus.BLOCKED):
            goal.status = GoalStatus.BLOCKED
            self.emit(
                "blocked",
                planning.error or "goal is unsupported by connected planners",
                thread_id=goal.thread_id,
                goal_id=goal.id,
            )
            return
        if planning.status == ResultStatus.RETRY and goal.attempts < self.contract.max_goal_attempts:
            goal.status = GoalStatus.QUEUED
            self.emit("retry", planning.error or "planner requested retry", thread_id=goal.thread_id, goal_id=goal.id)
            return
        goal.status = GoalStatus.FAILED
        self.emit("failed", planning.error or "planning failed", thread_id=goal.thread_id, goal_id=goal.id)

    def _apply_tool_failure(self, goal: Goal, result, step_max_retries: int) -> None:
        goal.last_error = result.error
        goal.updated_at = time.time()
        allowed_attempts = min(self.contract.max_goal_attempts, max(1, step_max_retries + 1))
        if result.status in (ResultStatus.BLOCKED, ResultStatus.UNSUPPORTED):
            goal.status = GoalStatus.BLOCKED
            self.emit("blocked", result.error or result.status.value, thread_id=goal.thread_id, goal_id=goal.id)
        elif result.status == ResultStatus.RETRY and goal.attempts < allowed_attempts:
            goal.status = GoalStatus.QUEUED
            self.emit("retry", result.error or "tool requested retry", thread_id=goal.thread_id, goal_id=goal.id)
        elif goal.attempts < allowed_attempts:
            goal.status = GoalStatus.QUEUED
            self.emit("retry", result.error or "tool failed", thread_id=goal.thread_id, goal_id=goal.id)
        else:
            goal.status = GoalStatus.FAILED
            self.emit("failed", result.error or "tool failed", thread_id=goal.thread_id, goal_id=goal.id)

    def _next_goal(self) -> Goal | None:
        queued = [goal for goal in self.goals if goal.status == GoalStatus.QUEUED]
        if not queued:
            return None
        highest = max(goal.priority for goal in queued)
        eligible = [goal for goal in queued if goal.priority == highest]
        thread_order = [thread.id for thread in self.threads.threads()]
        if self._last_thread_id in thread_order and len(thread_order) > 1:
            idx = thread_order.index(self._last_thread_id)
            thread_order = thread_order[idx + 1 :] + thread_order[: idx + 1]
        for thread_id in thread_order:
            candidates = [goal for goal in eligible if goal.thread_id == thread_id]
            if candidates:
                return min(candidates, key=lambda goal: goal.created_at)
        return min(eligible, key=lambda goal: goal.created_at)

    def _goal(self, goal_id: str) -> Goal | None:
        return next((goal for goal in self.goals if goal.id == goal_id), None)

    def _save(self) -> None:
        payload = {
            "schema": self.STATE_SCHEMA,
            "paused": self.paused,
            "stopped": self.stopped,
            "last_thread_id": self._last_thread_id,
            "threads": self.threads.to_state(),
            "goals": [goal.to_dict() for goal in self.goals],
        }
        self.checkpoints.save(payload)

    def _restore(self) -> None:
        data = self.checkpoints.load(default={})
        self.paused = bool(data.get("paused", False))
        self.stopped = bool(data.get("stopped", False))
        self._last_thread_id = data.get("last_thread_id")
        self.threads.restore(data.get("threads"))
        if not self.threads.threads():
            self.threads.create("Main", listener_enabled=True)
        restored: list[Goal] = []
        for raw in data.get("goals", []):
            try:
                goal = Goal.from_dict(raw)
            except (TypeError, ValueError):
                continue
            if goal.thread_id is None or self.threads.get(goal.thread_id) is None:
                goal.thread_id = self.threads.active_thread_id
            if goal.status == GoalStatus.RUNNING:
                execution = self._execution_state(goal)
                if execution and execution.get("in_flight_step") is not None and execution.get("in_flight_side_effect"):
                    tool = execution.get("in_flight_tool") or "side-effecting tool"
                    goal.status = GoalStatus.BLOCKED
                    goal.last_error = (
                        f"uncertain outcome after interruption during {tool}; "
                        "explicit reconciliation/retry required to avoid duplicate side effects"
                    )
                else:
                    goal.status = GoalStatus.QUEUED
                    goal.last_error = "recovered after interrupted execution"
            restored.append(goal)
        self.goals = restored
