from __future__ import annotations

from dataclasses import dataclass
import json
import math
from pathlib import Path
from typing import Iterable

from .models import Intent, Stimulus


@dataclass(frozen=True, slots=True)
class Node:
    id: str
    kind: str
    channel: str | None = None
    action: str | None = None
    bias: float = 0.0


@dataclass(slots=True)
class Edge:
    src: str
    dst: str
    weight: float


class GraphConnectomeKernel:
    """Deterministic graph executor for connectome-shaped control.

    A real connectome export can replace the bundled starter graph without changing
    the machine/tool layer, as long as it uses the same node/edge schema.
    """

    def __init__(self, nodes: Iterable[Node], edges: Iterable[Edge], ticks: int = 4, leak: float = 0.35):
        self.nodes = {n.id: n for n in nodes}
        self.edges = list(edges)
        self.ticks = ticks
        self.leak = leak
        self._incoming: dict[str, list[Edge]] = {node_id: [] for node_id in self.nodes}
        for edge in self.edges:
            if edge.src not in self.nodes or edge.dst not in self.nodes:
                raise ValueError(f"edge references missing node: {edge}")
            self._incoming[edge.dst].append(edge)

    @classmethod
    def from_dict(cls, data: dict) -> "GraphConnectomeKernel":
        return cls(
            nodes=[Node(**n) for n in data["nodes"]],
            edges=[Edge(**e) for e in data["edges"]],
            ticks=int(data.get("ticks", 4)),
            leak=float(data.get("leak", 0.35)),
        )

    @classmethod
    def from_json(cls, path: str | Path) -> "GraphConnectomeKernel":
        return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))

    def route(self, stimuli: Iterable[Stimulus]) -> Intent | None:
        activation = {node_id: 0.0 for node_id in self.nodes}
        channel_drive: dict[str, float] = {}
        for s in stimuli:
            channel_drive[s.channel] = channel_drive.get(s.channel, 0.0) + float(s.value) * float(s.salience)

        for node in self.nodes.values():
            if node.kind == "sensory" and node.channel:
                activation[node.id] = math.tanh(channel_drive.get(node.channel, 0.0) + node.bias)

        for _ in range(self.ticks):
            nxt = dict(activation)
            for node_id, node in self.nodes.items():
                if node.kind == "sensory":
                    continue
                total = node.bias + self.leak * activation[node_id]
                for edge in self._incoming[node_id]:
                    total += activation[edge.src] * edge.weight
                nxt[node_id] = math.tanh(total)
            activation = nxt

        candidates: list[tuple[float, Node]] = []
        for node in self.nodes.values():
            if node.kind == "motor" and node.action:
                candidates.append((activation[node.id], node))
        if not candidates:
            return None
        score, node = max(candidates, key=lambda item: item[0])
        confidence = max(0.0, min(1.0, (score + 1.0) / 2.0))
        if confidence < 0.55:
            return None
        return Intent(action=node.action or "observe", confidence=confidence, payload={"motor_node": node.id})

    def reinforce(self, action: str, reward: float, learning_rate: float = 0.01) -> None:
        motors = {n.id for n in self.nodes.values() if n.kind == "motor" and n.action == action}
        if not motors:
            return
        delta = max(-1.0, min(1.0, reward)) * learning_rate
        for edge in self.edges:
            if edge.dst in motors:
                edge.weight = max(-4.0, min(4.0, edge.weight + delta))


def default_connectome() -> GraphConnectomeKernel:
    data = {
        "ticks": 4,
        "leak": 0.30,
        "nodes": [
            {"id": "s_danger", "kind": "sensory", "channel": "danger"},
            {"id": "s_novelty", "kind": "sensory", "channel": "novelty"},
            {"id": "s_command", "kind": "sensory", "channel": "human_command"},
            {"id": "i_escape", "kind": "interneuron", "bias": -0.05},
            {"id": "i_orient", "kind": "interneuron", "bias": 0.05},
            {"id": "m_evade", "kind": "motor", "action": "evade", "bias": -0.10},
            {"id": "m_inspect", "kind": "motor", "action": "inspect", "bias": -0.05},
            {"id": "m_delegate", "kind": "motor", "action": "delegate", "bias": 0.00},
        ],
        "edges": [
            {"src": "s_danger", "dst": "i_escape", "weight": 2.4},
            {"src": "i_escape", "dst": "m_evade", "weight": 2.2},
            {"src": "s_novelty", "dst": "i_orient", "weight": 1.4},
            {"src": "i_orient", "dst": "m_inspect", "weight": 1.6},
            {"src": "s_command", "dst": "m_delegate", "weight": 2.3},
            {"src": "s_danger", "dst": "m_delegate", "weight": -1.2},
        ],
    }
    return GraphConnectomeKernel.from_dict(data)
