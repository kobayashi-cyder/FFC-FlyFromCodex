from pathlib import Path

from fly_agent import FlyMachineAgent, GraphConnectomeKernel, Stimulus
from fly_agent.connectome import default_connectome
from fly_agent.tools import Capability, ToolPolicy, ToolRegistry, ToolSpec
from fly_agent.models import ToolResult


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


def test_machine_calculator_and_checkpoint(tmp_path):
    out = []
    agent = FlyMachineAgent(state_dir=tmp_path, output=out.append)
    agent.submit_goal("計算: 2 + 3 * 4")
    assert agent.run() == 1
    assert not agent.status()["queued_goals"]
    assert any("goal completed" in line for line in out)
    restored = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    assert restored.status()["queued_goals"] == []


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


def test_capability_gate_blocks_device():
    policy = ToolPolicy({Capability.COMPUTE})
    tools = ToolRegistry(policy)
    tools.register(ToolSpec("device.move", lambda _: ToolResult(True, "moved"), Capability.DEVICE))
    result = tools.execute("device.move", {})
    assert result.ok is False
    assert "blocked" in (result.error or "")
