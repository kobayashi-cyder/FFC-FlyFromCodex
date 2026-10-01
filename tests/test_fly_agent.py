from pathlib import Path

from fly_agent import (
    AtomicCheckpointStore,
    BodyArbiter,
    Capability,
    FlyMachineAgent,
    FeedbackEncoder,
    GoalStatus,
    GraphConnectomeKernel,
    ResultStatus,
    SkillFabric,
    Stimulus,
    TestTier,
    ThreadRouter,
    ToolBus,
    ToolPolicy,
    ToolSpec,
    ToolResult,
    excel_label,
)
from fly_agent.connectome import default_connectome
from fly_agent.models import PlanProposal, PlannerResult, PlanStep, ToolContext


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


def test_feedback_encoder_quality_and_holdout_behavior():
    encoder = FeedbackEncoder()
    good = encoder.from_quality(
        "delegate",
        {
            "pass": True,
            "score": 88,
            "evolutionGain": 12,
            "hardIssues": [],
            "metrics": {
                "subjectCoverage": 1.0,
                "composition": 0.9,
                "categoryIntegrity": 1.0,
                "render": 0.8,
            },
        },
        tier=TestTier.TRAINING,
    )
    assert good.learnable is True
    assert good.reward > 0

    holdout = encoder.from_test("secret_eval", False, tier=TestTier.HOLDOUT)
    assert holdout.learnable is False
    assert holdout.learning_scale == 0.0


def test_regression_feedback_is_weaker_but_learnable():
    encoder = FeedbackEncoder()
    event = encoder.from_test("router_regression", False, tier=TestTier.REGRESSION)
    assert event.learnable is True
    assert event.learning_scale == 0.35
    assert event.vector.regression == 1.0


def test_connectome_feedback_updates_only_active_causal_path():
    graph = default_connectome()
    graph.route([Stimulus("human_command", 1.0, 1.0)])
    before = {(e.src, e.dst): e.weight for e in graph.edges}

    encoder = FeedbackEncoder()
    event = encoder.from_test("training_pass", True, tier=TestTier.TRAINING)
    applied = graph.reinforce_feedback("delegate", event.vector, learning_scale=event.learning_scale)
    after = {(e.src, e.dst): e.weight for e in graph.edges}

    assert applied > 0
    assert after[("s_command", "m_delegate")] != before[("s_command", "m_delegate")]
    assert after[("i_escape", "m_evade")] == before[("i_escape", "m_evade")]
    assert after[("s_danger", "m_delegate")] == before[("s_danger", "m_delegate")]


def test_observer_records_structured_feedback_event(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    agent.observer.record_test("training_pass", True, tier=TestTier.TRAINING)
    rows = list(agent.memory.tail(4))
    feedback = [row for row in rows if row.get("kind") == "feedback"]
    assert feedback
    assert feedback[-1]["data"]["tier"] == TestTier.TRAINING.value
    assert feedback[-1]["data"]["learnable"] is True


def test_skill_fabric_discovers_registered_tools_and_policy(tmp_path):
    bus = ToolBus(ToolPolicy({Capability.COMPUTE}))
    bus.register(ToolSpec("compute.alpha", lambda _: ToolResult.success("a"), Capability.COMPUTE, "alpha"))
    bus.register(ToolSpec("network.beta", lambda _: ToolResult.success("b"), Capability.NETWORK, "beta"))
    fabric = SkillFabric(tmp_path / "skills.json")
    fabric.sync_tools(bus)

    assert fabric.status()["discovered_tools"] == 2
    assert fabric.tools["compute.alpha"]["executable"] is True
    assert fabric.tools["network.beta"]["executable"] is False


def test_successful_plan_is_promoted_and_reused_as_skill(tmp_path):
    bus = ToolBus(ToolPolicy({Capability.COMPUTE}))
    bus.register(ToolSpec("echo", lambda a: ToolResult.success(a.get("text")), Capability.COMPUTE))
    fabric = SkillFabric(tmp_path / "skills.json", min_successes=2, min_reward=0.0)
    goal = Goal("repeat this goal")
    proposal = PlanProposal(
        [PlanStep("echo", {"text": goal.text}, "echo learned goal")],
        source="test-planner",
    )

    assert fabric.observe_plan(goal, proposal, success=True, reward=0.4) is None
    promoted = fabric.observe_plan(goal, proposal, success=True, reward=0.5)
    assert promoted is not None
    assert promoted.successes == 2

    learned = fabric.proposal_for(Goal("repeat this goal"), bus)
    assert learned is not None
    assert learned.source.startswith("learned-skill:")
    assert learned.steps[0].tool == "echo"
    assert learned.steps[0].args["text"] == "repeat this goal"


def test_holdout_feedback_is_not_consumed_by_skill_fabric(tmp_path):
    bus = ToolBus(ToolPolicy({Capability.COMPUTE}))
    bus.register(ToolSpec("echo", lambda _: ToolResult.success("ok"), Capability.COMPUTE))
    fabric = SkillFabric(tmp_path / "skills.json")
    fabric.sync_tools(bus)
    encoder = FeedbackEncoder()
    event = encoder.from_test("secret", True, tier=TestTier.HOLDOUT, action="echo")
    fabric.consume_feedback("echo", event)
    assert fabric.tools["echo"]["attempts"] == 0


def test_runtime_autonomously_acquires_repeated_successful_plan(tmp_path):
    class EchoPlanner:
        def propose(self, goal, memory, tools):
            return PlannerResult(
                ResultStatus.SUCCESS,
                PlanProposal(
                    [PlanStep("echo", {"text": goal.text}, "learnable echo")],
                    source="echo-planner",
                ),
            )

    bus = ToolBus(ToolPolicy({Capability.COMPUTE}))
    bus.register(ToolSpec("echo", lambda a: ToolResult.success(a.get("text")), Capability.COMPUTE))
    agent = FlyMachineAgent(
        state_dir=tmp_path,
        tools=bus,
        output=lambda _: None,
        planner=EchoPlanner(),
    )
    agent.submit_goal("autonomy target")
    agent.submit_goal("autonomy target")
    assert agent.run() == 2
    status = agent.status()["autonomy"]
    assert status["learned_skills"] >= 1

    restored = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    restored.submit_goal("autonomy target")
    assert restored.run() == 1
    done = restored.status()["completed_goals"]
    assert any(g["text"] == "autonomy target" for g in done)
