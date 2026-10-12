import unittest

from fly_agent.automaton import (
    AutomatonRunner,
    AutomatonSpec,
    RecordingAutomatonGenerator,
    RecordingEvent,
)


class RecordingAutomatonTests(unittest.TestCase):
    def test_recording_generates_tap_transition(self):
        generator = RecordingAutomatonGenerator(min_confidence=0.4)
        spec = generator.generate(
            [
                RecordingEvent(0, "home"),
                RecordingEvent(120, "home", kind="tap", x=0.5, y=0.75, confidence=0.95),
                RecordingEvent(420, "menu", confidence=0.9),
            ],
            name="menu_open",
        )

        self.assertEqual(spec.initial_state, "home")
        self.assertIn("menu", spec.states)
        self.assertEqual(len(spec.states["home"].transitions), 1)
        transition = spec.states["home"].transitions[0]
        self.assertEqual(transition.to_state, "menu")
        self.assertEqual(transition.action.kind, "tap")
        self.assertEqual(transition.action.args, {"x": 0.5, "y": 0.75})
        self.assertAlmostEqual(transition.confidence, 0.9)

    def test_coordinates_are_normalized(self):
        event = RecordingEvent(1, "home", kind="tap", x=2.0, y=-1.0).normalized()
        self.assertEqual(event.x, 1.0)
        self.assertEqual(event.y, 0.0)

    def test_round_trip_definition(self):
        spec = RecordingAutomatonGenerator().generate(
            [
                RecordingEvent(0, "a"),
                RecordingEvent(10, "a", kind="tap", x=0.1, y=0.2),
                RecordingEvent(100, "b"),
            ]
        )
        restored = AutomatonSpec.from_dict(spec.to_dict())
        self.assertEqual(restored.to_dict(), spec.to_dict())

    def test_runner_interprets_generated_automaton(self):
        spec = RecordingAutomatonGenerator().generate(
            [
                RecordingEvent(0, "a"),
                RecordingEvent(10, "a", kind="tap", x=0.1, y=0.2),
                RecordingEvent(100, "b"),
            ]
        )
        executed = []
        runner = AutomatonRunner(spec, executed.append)
        state = runner.step("a")
        self.assertEqual(state, "b")
        self.assertEqual(len(executed), 1)
        self.assertEqual(executed[0].kind, "tap")

    def test_low_confidence_transition_is_filtered(self):
        spec = RecordingAutomatonGenerator(min_confidence=0.8).generate(
            [
                RecordingEvent(0, "a"),
                RecordingEvent(10, "a", kind="tap", x=0.1, y=0.2, confidence=0.3),
                RecordingEvent(100, "b", confidence=0.9),
            ]
        )
        self.assertEqual(spec.metadata["transition_count"], 0)


if __name__ == "__main__":
    unittest.main()
