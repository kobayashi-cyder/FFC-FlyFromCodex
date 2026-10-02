from __future__ import annotations

from .executive import ConnectomeExecutive
from .memory import MemoryFabric
from .models import AgentEvent, Observation, ResultStatus
from .serialization import json_safe


class Observer:
    """Turns tool outcomes into durable experience and local reinforcement."""

    def __init__(self, memory: MemoryFabric, executive: ConnectomeExecutive):
        self.memory = memory
        self.executive = executive

    def record(self, observation: Observation) -> None:
        result = observation.result
        self.memory.append(
            AgentEvent(
                "observation",
                f"{observation.tool}: {result.status.value}",
                thread_id=observation.thread_id,
                data={
                    "goal_id": observation.goal_id,
                    "step_index": observation.step_index,
                    "tool": observation.tool,
                    "status": result.status.value,
                    "error": result.error,
                    "output": json_safe(result.output),
                },
            )
        )
        if result.status == ResultStatus.SUCCESS:
            self.executive.reinforce("delegate", 0.1)
        elif result.status == ResultStatus.FAILED:
            self.executive.reinforce("delegate", -0.05)
