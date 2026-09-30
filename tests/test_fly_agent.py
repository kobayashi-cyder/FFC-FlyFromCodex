from pathlib import Path
import json
import subprocess
import sys

from fly_agent import (
    AtomicCheckpointStore,
    BodyArbiter,
    Capability,
    FlyMachineAgent,
    GoalStatus,
    GraphConnectomeKernel,
    PlanProposal,
    PlanStep,
    ResultStatus,
    Stimulus,
    ThreadRouter,
    ToolBus,
    ToolPolicy,
    ToolSpec,
    ToolResult,
    excel_label,
)
from fly_agent.connectome import default_connectome
from fly_agent.models import ToolContext


def test_danger_prefers_evade():
    intent = default_connectome().route([Stimulus("danger", 1.0, 1.0)])
    assert intent is not None
    assert intent.action == "evade"
    assert intent.confidence > 0.8


def test_human_command_delegates():
    intent = default_connectome().route([Stimulus("human_command", 1.0, 1.0)])
    assert intent is not None
    assert intent.action == "delegate"


def test_connectome_json_roundtrip_schema():
    graph = GraphConnectomeKernel.from_json(Path("config/connectome_minimal.json"))
    intent = graph.route([Stimulus("novelty", 1.0, 1.0)])
    assert intent is not None
    assert intent.action == "inspect"


def test_excel_thread_labels():
    assert [excel_label(i) for i in (0, 25, 26, 27, 51, 52)] == ["A", "Z", "AA", "AB", "AZ", "BA"]


def test_thread_router_explicit_and_context_routes():
    router = ThreadRouter()
    a = router.create("Android APK")
    b = router.create("Image generation")
    router.record(a.id, "APK Android accessibility")
    router.record(b.id, "画像 生成 BANC")
    explicit = router.route("B: 画像を続ける")
    assert explicit.thread_id == b.id
    assert explicit.text == "画像を続ける"
    routed = router.route("BANC 画像")
    assert routed.thread_id == b.id


def test_planner_command_prefix_is_not_misread_as_thread_label():
    router = ThreadRouter()
    a = router.create("Main")
    for command in ("calc: 1+1", "say: hello", "tool: echo"):
        routed = router.route(command)
        assert routed.thread_id == a.id
        assert routed.text == command
        assert routed.explicit is False


def test_voice_router_respects_listener_disable(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    b = agent.new_thread("Images")
    agent.set_thread_listener("A", False)
    goal = agent.voice_input("画像 表示: hello")
    assert goal.thread_id == b["id"]


def test_machine_calculator_and_checkpoint(tmp_path):
    out = []
    agent = FlyMachineAgent(state_dir=tmp_path, output=out.append)
    agent.submit_goal("計算: 2 + 3 * 4")
    assert agent.run() == 1
    assert not agent.status()["queued_goals"]
    assert len(agent.status()["completed_goals"]) == 1
    restored = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    assert len(restored.status()["completed_goals"]) == 1


def test_pause_resume_preserves_goal(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    agent.submit_goal("表示: hello")
    agent.pause()
    assert agent.run() == 0
    restored = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    assert restored.status()["paused"] is True
    assert len(restored.status()["queued_goals"]) == 1
    restored.resume()
    assert restored.run() == 1


def test_unsupported_goal_is_blocked_not_completed(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    goal = agent.submit_goal("book a flight")
    assert agent.run() == 1
    status = agent.status()
    assert not status["completed_goals"]
    assert status["blocked_goals"][0]["id"] == goal.id
    assert status["blocked_goals"][0]["status"] == GoalStatus.BLOCKED.value


def test_explicit_empty_capability_policy_denies_all():
    bus = ToolBus(ToolPolicy(set()))
    bus.register(ToolSpec("compute.x", lambda _: ToolResult.success(1), Capability.COMPUTE))
    result = bus.execute("compute.x")
    assert result.status == ResultStatus.BLOCKED


def test_capability_gate_blocks_device():
    policy = ToolPolicy({Capability.COMPUTE})
    bus = ToolBus(policy)
    bus.register(ToolSpec("device.move", lambda _: ToolResult.success("moved"), Capability.DEVICE))
    result = bus.execute("device.move")
    assert result.status == ResultStatus.BLOCKED


def test_body_arbiter_serializes_shared_resource():
    arbiter = BodyArbiter()
    first = arbiter.acquire("android", "thread-a")
    assert first is not None
    assert arbiter.acquire("android", "thread-b", timeout=0) is None
    same_owner = arbiter.acquire("android", "thread-a")
    assert same_owner is not None and same_owner.lease_id == first.lease_id
    arbiter.release(same_owner)
    assert arbiter.acquire("android", "thread-b", timeout=0) is None
    arbiter.release(first)
    second = arbiter.acquire("android", "thread-b", timeout=0)
    assert second is not None
    arbiter.release(second)


def test_tool_bus_returns_retry_when_body_is_busy():
    arbiter = BodyArbiter()
    bus = ToolBus(ToolPolicy({Capability.DEVICE}), arbiter=arbiter)
    bus.register(ToolSpec("device.tap", lambda _: ToolResult.success("tap"), Capability.DEVICE, resource="android"))
    lease = arbiter.acquire("android", "A")
    assert lease is not None
    result = bus.execute("device.tap", {}, ToolContext(thread_id="B"))
    assert result.status == ResultStatus.RETRY
    arbiter.release(lease)


def test_atomic_checkpoint_recovers_backup(tmp_path):
    store = AtomicCheckpointStore(tmp_path / "checkpoint.json")
    store.save({"value": 1})
    store.save({"value": 2})
    (tmp_path / "checkpoint.json").write_text("{broken", encoding="utf-8")
    recovered = store.load()
    assert recovered == {"value": 1}
    assert store.recovered_from_backup is True


def test_running_goal_recovers_as_queued(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    goal = agent.submit_goal("表示: hello")
    goal.status = GoalStatus.RUNNING
    agent._save()
    restored = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    restored_goal = next(g for g in restored.goals if g.id == goal.id)
    assert restored_goal.status == GoalStatus.QUEUED
    assert "interrupted" in (restored_goal.last_error or "")


def test_interrupted_side_effect_is_blocked_instead_of_replayed(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    agent.tools.register(ToolSpec("device.tap", lambda _: ToolResult.success("tap"), Capability.IR, side_effect=True))
    goal = agent.submit_goal("placeholder")
    goal.status = GoalStatus.RUNNING
    goal.metadata[agent.EXECUTION_KEY] = {
        "proposal": PlanProposal([PlanStep("device.tap")], source="test").to_dict(),
        "next_step_index": 0,
        "in_flight_step": 0,
        "in_flight_side_effect": True,
        "in_flight_tool": "device.tap",
    }
    agent._save()
    restored = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    restored_goal = next(g for g in restored.goals if g.id == goal.id)
    assert restored_goal.status == GoalStatus.BLOCKED
    assert "duplicate side effects" in (restored_goal.last_error or "")


def test_running_state_is_persisted_before_side_effect_tool(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE})
    bus = ToolBus(policy)
    seen = {}

    def inspect_checkpoint(_):
        payload = json.loads((tmp_path / "checkpoint.json").read_text(encoding="utf-8"))
        current = payload["goals"][0]
        seen["status"] = current["status"]
        seen["in_flight"] = current["metadata"]["_execution"]["in_flight_step"]
        return ToolResult.success("ok")

    bus.register(ToolSpec("planner.propose", lambda _: ToolResult.success({"steps": [{"tool": "effect"}]}), Capability.COMPUTE))
    bus.register(ToolSpec("effect", inspect_checkpoint, Capability.COMPUTE, side_effect=True))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    agent.submit_goal("abstract")
    agent.run()
    assert seen == {"status": "running", "in_flight": 0}


def test_retry_resumes_at_failed_step_without_replaying_prior_side_effect(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE})
    bus = ToolBus(policy)
    calls = {"planner": 0, "first": 0, "second": 0}

    def propose(_):
        calls["planner"] += 1
        return ToolResult.success({"steps": [{"tool": "first"}, {"tool": "second"}]})

    def first(_):
        calls["first"] += 1
        return ToolResult.success("first")

    def second(_):
        calls["second"] += 1
        if calls["second"] == 1:
            return ToolResult.retry("temporary")
        return ToolResult.success("second")

    bus.register(ToolSpec("planner.propose", propose, Capability.COMPUTE))
    bus.register(ToolSpec("first", first, Capability.COMPUTE, side_effect=True))
    bus.register(ToolSpec("second", second, Capability.COMPUTE))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    goal = agent.submit_goal("abstract")
    assert agent.step() is True
    assert goal.status == GoalStatus.QUEUED
    assert agent.step() is True
    assert goal.status == GoalStatus.DONE
    assert calls == {"planner": 1, "first": 1, "second": 2}


def test_observer_normalizes_arbitrary_tool_output(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE})
    bus = ToolBus(policy)

    class Strange:
        def __repr__(self):
            return "<strange>"

    bus.register(
        ToolSpec(
            "weird",
            lambda _: ToolResult.success({"bytes": b"abc", "path": Path("x/y"), "object": Strange()}),
            Capability.COMPUTE,
        )
    )
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    goal = agent.submit_goal("tool: weird")
    agent.run()
    assert goal.status == GoalStatus.DONE
    lines = (tmp_path / "episodes.jsonl").read_text(encoding="utf-8").splitlines()
    parsed = [json.loads(line) for line in lines]
    observation = next(row for row in parsed if row["kind"] == "observation" and row["data"]["tool"] == "weird")
    assert observation["data"]["output"]["bytes"]["__type__"] == "bytes"
    assert observation["data"]["output"]["path"] == "x/y"


def test_equal_priority_scheduling_rotates_threads(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    b = agent.new_thread("B")
    a_id = agent.threads.get_by_label("A").id
    agent.submit_goal("表示: A1", thread_id=a_id)
    agent.submit_goal("表示: B1", thread_id=b["id"])
    agent.submit_goal("表示: A2", thread_id=a_id)
    assert agent.step() is True
    first_done = [g for g in agent.goals if g.status == GoalStatus.DONE][0]
    assert first_done.thread_id == a_id
    assert agent.step() is True
    done = [g for g in agent.goals if g.status == GoalStatus.DONE]
    assert done[-1].thread_id == b["id"]


def test_external_planner_proposal_is_executed_only_after_validation(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE, Capability.HUMAN_OUTPUT})
    bus = ToolBus(policy)
    output = []
    bus.register(ToolSpec("human.say", lambda a: (output.append(a["text"]), ToolResult.success(a["text"]))[1], Capability.HUMAN_OUTPUT))
    bus.register(ToolSpec("planner.propose", lambda _: ToolResult.success({"steps": [{"tool": "human.say", "args": {"text": "planned"}}]}), Capability.COMPUTE))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    agent.submit_goal("do something abstract")
    agent.run()
    assert output == ["planned"]
    assert len(agent.status()["completed_goals"]) == 1


def test_external_planner_cannot_smuggle_blocked_device_tool(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE, Capability.HUMAN_OUTPUT})
    bus = ToolBus(policy)
    bus.register(ToolSpec("planner.propose", lambda _: ToolResult.success({"steps": [{"tool": "device.tap", "args": {}}]}), Capability.COMPUTE))
    bus.register(ToolSpec("device.tap", lambda _: ToolResult.success("tap"), Capability.DEVICE, resource="android"))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    agent.submit_goal("tap it")
    agent.run()
    assert len(agent.status()["blocked_goals"]) == 1
    assert not agent.status()["completed_goals"]


def test_connectome_projection_keeps_verified_roles_and_neighborhood():
    from fly_agent.connectome_projection import compile_projection
    roles = {
        "s1": {"id": "s1", "kind": "sensory", "channel": "danger"},
        "m1": {"id": "m1", "kind": "motor", "action": "evade"},
    }
    graph = compile_projection([
        ("s1", "i1", 20),
        ("i1", "m1", 40),
        ("far", "away", 99),
    ], roles, max_hops=2, weight_scale=100)
    ids = {node["id"] for node in graph["nodes"]}
    assert {"s1", "i1", "m1"}.issubset(ids)
    assert "far" not in ids
    assert next(node for node in graph["nodes"] if node["id"] == "s1")["channel"] == "danger"
    assert next(node for node in graph["nodes"] if node["id"] == "m1")["action"] == "evade"


def test_connectome_projection_cli_is_importable_from_repo_root():
    result = subprocess.run(
        [sys.executable, "tools/connectome_project.py", "--help"],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
    assert "Project a verified connectome" in result.stdout
