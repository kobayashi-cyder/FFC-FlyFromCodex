import importlib.util
from pathlib import Path
import unittest
import sys

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("fly_agent_toolbus", ROOT / "tools" / "fly_agent_toolbus.py")
mod = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = mod
SPEC.loader.exec_module(mod)


class FlyAgentToolBusTests(unittest.TestCase):
    def test_connectome_candidate_wins_by_activation(self):
        bus = mod.FlyAgentToolBus()
        d = bus.choose_candidate([
            {"tool": "memory.get", "args": {"key": "x"}, "excitation": 0.7, "inhibition": 0.6},
            {"tool": "compute.math", "args": {"expression": "1+1"}, "excitation": 0.6, "inhibition": 0.0},
        ])
        self.assertEqual(d["selected"]["tool"], "compute.math")

    def test_unknown_and_below_threshold_are_rejected(self):
        bus = mod.FlyAgentToolBus(min_activation=0.2)
        d = bus.choose_candidate([
            {"tool": "does.not.exist", "args": {}, "excitation": 1.0},
            {"tool": "compute.math", "args": {"expression": "1"}, "excitation": 0.1},
        ])
        self.assertEqual(d["status"], "NO_ACTION")
        self.assertEqual({x["reason"] for x in d["rejected"]}, {"unknown_tool", "below_threshold"})

    def test_safe_math(self):
        bus = mod.FlyAgentToolBus()
        self.assertEqual(bus.execute("compute.math", {"expression": "2+3*4"})["value"], 14)
        self.assertAlmostEqual(bus.execute("compute.math", {"expression": "sqrt(81)"})["value"], 9.0)
        with self.assertRaises(mod.InvalidToolRequest):
            bus.execute("compute.math", {"expression": "__import__('os').system('x')"})

    def test_memory_round_trip_and_digest(self):
        bus = mod.FlyAgentToolBus()
        put = bus.execute("memory.put", {"key": "goal", "value": {"task": "inspect"}})
        self.assertEqual(len(put["value"]["digest"]), 64)
        got = bus.execute("memory.get", {"key": "goal"})
        self.assertEqual(got["value"], {"task": "inspect"})

    def test_capability_gate_blocks(self):
        bus = mod.FlyAgentToolBus(capabilities={"compute"})
        with self.assertRaises(mod.CapabilityDenied):
            bus.execute("memory.put", {"key": "x", "value": 1})
        packet = bus.step("remember", [
            {"tool": "memory.put", "args": {"key": "x", "value": 1}, "excitation": 1.0}
        ])
        self.assertEqual(packet["feedback"]["state"], "blocked")

    def test_dry_run_prevents_local_write(self):
        bus = mod.FlyAgentToolBus()
        result = bus.execute("memory.put", {"key": "x", "value": 1}, dry_run=True)
        self.assertEqual(result["status"], "DRY_RUN")
        self.assertNotIn("x", bus.context.memory)

    def test_idempotency_replays(self):
        bus = mod.FlyAgentToolBus()
        first = bus.execute("memory.put", {"key": "x", "value": 1}, idempotency_key="a")
        second = bus.execute("memory.put", {"key": "x", "value": 999}, idempotency_key="a")
        self.assertFalse(first.get("replayed", False))
        self.assertTrue(second["replayed"])
        self.assertEqual(bus.context.memory["x"], 1)

    def test_graph_hops_is_bounded(self):
        bus = mod.FlyAgentToolBus(graph={"a": ["b", "c"], "b": ["d"], "c": ["e"], "d": ["f"]})
        result = bus.execute("graph.hops", {"node": "a", "depth": 2})["value"]
        self.assertEqual(result["layers"], [["a"], ["b", "c"], ["d", "e"]])
        with self.assertRaises(mod.InvalidToolRequest):
            bus.execute("graph.hops", {"node": "a", "depth": 4})

    def test_bridge_is_proposal_only(self):
        bus = mod.FlyAgentToolBus()
        out = bus.execute("bridge.propose", {
            "action": "browser.click",
            "target": "#submit",
            "parameters": {"button": "left"},
            "reason": "human requested submit",
        })["value"]
        self.assertTrue(out["proposal_only"])
        self.assertEqual(out["execution"], "NOT_PERFORMED")

    def test_manifest_stable_and_descriptive(self):
        bus = mod.FlyAgentToolBus()
        names = [x["name"] for x in bus.manifest()]
        self.assertEqual(names, sorted(names))
        self.assertIn("compute.math", names)
        self.assertIn("bridge.propose", names)


if __name__ == "__main__":
    unittest.main(verbosity=2)
