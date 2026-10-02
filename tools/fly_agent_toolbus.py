#!/usr/bin/env python3
"""Deterministic offline tool bus for a connectome-first agent.

The connectome/fly remains the decision source. This module only turns a fly
candidate action into a capability-gated tool call and returns an observation.
No network, subprocess, filesystem mutation, or arbitrary code execution is
performed by the default tool set.
"""
from __future__ import annotations

import ast
import hashlib
import json
import math
import operator
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Iterable, List, Mapping, MutableMapping, Optional, Sequence, Tuple

JSON = Dict[str, Any]
Handler = Callable[[Mapping[str, Any], "ExecutionContext"], Any]


class ToolBusError(Exception):
    """Base error for deterministic tool-bus failures."""


class CapabilityDenied(ToolBusError):
    pass


class InvalidToolRequest(ToolBusError):
    pass


class UnknownTool(ToolBusError):
    pass


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    capability: str
    side_effect: str
    required_args: Tuple[str, ...] = ()
    optional_args: Tuple[str, ...] = ()
    handler: Handler = field(compare=False, repr=False, default=lambda _a, _c: None)

    def manifest(self) -> JSON:
        return {
            "name": self.name,
            "description": self.description,
            "capability": self.capability,
            "side_effect": self.side_effect,
            "required_args": list(self.required_args),
            "optional_args": list(self.optional_args),
        }


@dataclass
class ExecutionContext:
    memory: MutableMapping[str, Any]
    graph: Mapping[str, Sequence[str]]
    capabilities: set[str]


@dataclass(frozen=True)
class Candidate:
    tool: str
    args: Mapping[str, Any]
    excitation: float
    inhibition: float = 0.0
    confidence: float = 1.0
    source: str = "connectome"

    @property
    def activation(self) -> float:
        return (self.excitation - self.inhibition) * self.confidence

    @classmethod
    def from_mapping(cls, data: Mapping[str, Any]) -> "Candidate":
        return cls(
            tool=str(data.get("tool", "")),
            args=dict(data.get("args", {})),
            excitation=float(data.get("excitation", 0.0)),
            inhibition=float(data.get("inhibition", 0.0)),
            confidence=float(data.get("confidence", 1.0)),
            source=str(data.get("source", "connectome")),
        )


class SafeMath:
    _binary = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.FloorDiv: operator.floordiv,
        ast.Mod: operator.mod,
        ast.Pow: operator.pow,
    }
    _unary = {ast.UAdd: operator.pos, ast.USub: operator.neg}
    _functions = {
        "abs": abs, "round": round, "sqrt": math.sqrt, "log": math.log,
        "exp": math.exp, "sin": math.sin, "cos": math.cos, "tan": math.tan,
        "min": min, "max": max,
    }
    _constants = {"pi": math.pi, "e": math.e}

    @classmethod
    def evaluate(cls, expression: str) -> float | int:
        tree = ast.parse(expression, mode="eval")
        return cls._eval(tree.body)

    @classmethod
    def _eval(cls, node: ast.AST) -> float | int:
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.Name) and node.id in cls._constants:
            return cls._constants[node.id]
        if isinstance(node, ast.BinOp) and type(node.op) in cls._binary:
            left, right = cls._eval(node.left), cls._eval(node.right)
            if isinstance(node.op, ast.Pow) and abs(float(right)) > 32:
                raise InvalidToolRequest("power exponent exceeds safe bound")
            return cls._binary[type(node.op)](left, right)
        if isinstance(node, ast.UnaryOp) and type(node.op) in cls._unary:
            return cls._unary[type(node.op)](cls._eval(node.operand))
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id in cls._functions:
            if node.keywords:
                raise InvalidToolRequest("keyword arguments are not supported")
            return cls._functions[node.func.id](*[cls._eval(arg) for arg in node.args])
        raise InvalidToolRequest(f"unsupported math syntax: {type(node).__name__}")


class FlyAgentToolBus:
    """Capability-gated deterministic executor for connectome-selected actions."""

    def __init__(
        self,
        capabilities: Optional[Iterable[str]] = None,
        *,
        min_activation: float = 0.05,
        graph: Optional[Mapping[str, Sequence[str]]] = None,
    ) -> None:
        self.context = ExecutionContext(
            memory={},
            graph=graph or {},
            capabilities=set(capabilities or {
                "compute", "memory.read", "memory.write", "graph.read", "bridge.propose"
            }),
        )
        self.min_activation = float(min_activation)
        self._tools: Dict[str, ToolSpec] = {}
        self._idempotency: Dict[str, JSON] = {}
        self.trace: List[JSON] = []
        self._register_defaults()

    def register(self, spec: ToolSpec) -> None:
        if not spec.name or spec.name in self._tools:
            raise ValueError(f"duplicate/invalid tool name: {spec.name!r}")
        self._tools[spec.name] = spec

    def manifest(self) -> List[JSON]:
        return [self._tools[name].manifest() for name in sorted(self._tools)]

    def choose_candidate(self, candidates: Iterable[Mapping[str, Any] | Candidate]) -> JSON:
        parsed = [c if isinstance(c, Candidate) else Candidate.from_mapping(c) for c in candidates]
        rejected: List[JSON] = []
        eligible: List[Candidate] = []
        for c in parsed:
            if c.tool not in self._tools:
                rejected.append({"tool": c.tool, "reason": "unknown_tool", "activation": c.activation})
                continue
            if c.activation < self.min_activation:
                rejected.append({"tool": c.tool, "reason": "below_threshold", "activation": c.activation})
                continue
            eligible.append(c)
        if not eligible:
            return {"status": "NO_ACTION", "selected": None, "rejected": rejected}
        selected = sorted(eligible, key=lambda c: (-c.activation, -c.confidence, c.tool))[0]
        return {
            "status": "SELECTED",
            "selected": {
                "tool": selected.tool,
                "args": dict(selected.args),
                "activation": selected.activation,
                "excitation": selected.excitation,
                "inhibition": selected.inhibition,
                "confidence": selected.confidence,
                "source": selected.source,
            },
            "rejected": rejected,
        }

    def execute(
        self,
        tool: str,
        args: Optional[Mapping[str, Any]] = None,
        *,
        dry_run: bool = False,
        idempotency_key: Optional[str] = None,
    ) -> JSON:
        args = dict(args or {})
        spec = self._tools.get(tool)
        if spec is None:
            raise UnknownTool(tool)
        if spec.capability not in self.context.capabilities:
            raise CapabilityDenied(f"missing capability: {spec.capability}")
        missing = [name for name in spec.required_args if name not in args]
        if missing:
            raise InvalidToolRequest(f"missing args: {', '.join(missing)}")
        allowed = set(spec.required_args) | set(spec.optional_args)
        unexpected = sorted(set(args) - allowed)
        if unexpected:
            raise InvalidToolRequest(f"unexpected args: {', '.join(unexpected)}")

        cache_key = None
        if idempotency_key:
            cache_key = f"{tool}:{idempotency_key}"
            if cache_key in self._idempotency:
                return {**self._idempotency[cache_key], "replayed": True}

        envelope = {"tool": tool, "args": args, "side_effect": spec.side_effect, "dry_run": bool(dry_run)}
        if dry_run and spec.side_effect != "none":
            result: JSON = {"ok": True, "status": "DRY_RUN", "request": envelope}
        else:
            try:
                value = spec.handler(args, self.context)
                result = {"ok": True, "status": "OK", "tool": tool, "value": value}
            except ToolBusError:
                raise
            except Exception as exc:
                result = {"ok": False, "status": "ERROR", "tool": tool, "error": f"{type(exc).__name__}: {exc}"}

        self.trace.append({"index": len(self.trace), **envelope, "result": result})
        if cache_key:
            self._idempotency[cache_key] = dict(result)
        return result

    def step(
        self,
        human_input: str,
        candidates: Iterable[Mapping[str, Any] | Candidate],
        *,
        dry_run: bool = False,
        idempotency_key: Optional[str] = None,
    ) -> JSON:
        decision = self.choose_candidate(candidates)
        packet: JSON = {
            "human_input": human_input,
            "decision": decision,
            "observation": None,
            "feedback": {"state": "no_action"},
        }
        if decision["selected"] is None:
            return packet
        selected = decision["selected"]
        try:
            observation = self.execute(
                selected["tool"], selected["args"], dry_run=dry_run, idempotency_key=idempotency_key
            )
            feedback_state = "success" if observation.get("ok") else "error"
        except CapabilityDenied as exc:
            observation = {"ok": False, "status": "BLOCKED", "error": str(exc), "tool": selected["tool"]}
            feedback_state = "blocked"
        except ToolBusError as exc:
            observation = {"ok": False, "status": "INVALID", "error": str(exc), "tool": selected["tool"]}
            feedback_state = "invalid"
        packet["observation"] = observation
        packet["feedback"] = {
            "state": feedback_state,
            "selected_activation": selected["activation"],
            "tool": selected["tool"],
        }
        return packet

    def _register_defaults(self) -> None:
        self.register(ToolSpec(
            "compute.math", "Evaluate bounded arithmetic without eval().", "compute", "none",
            ("expression",), (), self._tool_math,
        ))
        self.register(ToolSpec(
            "memory.put", "Store JSON-safe local session memory.", "memory.write", "local",
            ("key", "value"), (), self._tool_memory_put,
        ))
        self.register(ToolSpec(
            "memory.get", "Read local session memory.", "memory.read", "none",
            ("key",), ("default",), self._tool_memory_get,
        ))
        self.register(ToolSpec(
            "memory.delete", "Delete a local session-memory key.", "memory.write", "local",
            ("key",), (), self._tool_memory_delete,
        ))
        self.register(ToolSpec(
            "graph.neighbors", "Read 1-hop neighbors from the supplied connectome view.", "graph.read", "none",
            ("node",), ("limit",), self._tool_graph_neighbors,
        ))
        self.register(ToolSpec(
            "graph.hops", "Breadth-first traversal over a supplied coarse connectome view.", "graph.read", "none",
            ("node", "depth"), ("limit",), self._tool_graph_hops,
        ))
        self.register(ToolSpec(
            "bridge.propose", "Create a non-executing proposal for browser/OS/native bridges.", "bridge.propose", "external-proposal",
            ("action",), ("target", "parameters", "reason"), self._tool_bridge_propose,
        ))
        self.register(ToolSpec(
            "system.manifest", "Return available tool contracts.", "compute", "none",
            (), (), self._tool_manifest,
        ))

    @staticmethod
    def _tool_math(args: Mapping[str, Any], _ctx: ExecutionContext) -> Any:
        expr = str(args["expression"])
        if len(expr) > 256:
            raise InvalidToolRequest("expression too long")
        value = SafeMath.evaluate(expr)
        if isinstance(value, (int, float)) and not math.isfinite(float(value)):
            raise InvalidToolRequest("non-finite result")
        return value

    @staticmethod
    def _assert_json_safe(value: Any) -> None:
        try:
            json.dumps(value, ensure_ascii=False, allow_nan=False)
        except (TypeError, ValueError) as exc:
            raise InvalidToolRequest(f"value is not JSON-safe: {exc}") from exc

    def _tool_memory_put(self, args: Mapping[str, Any], ctx: ExecutionContext) -> Any:
        key = str(args["key"])
        if not key or len(key) > 128:
            raise InvalidToolRequest("invalid memory key")
        self._assert_json_safe(args["value"])
        ctx.memory[key] = args["value"]
        return {"stored": key, "digest": self._digest(args["value"])}

    @staticmethod
    def _tool_memory_get(args: Mapping[str, Any], ctx: ExecutionContext) -> Any:
        return ctx.memory.get(str(args["key"]), args.get("default"))

    @staticmethod
    def _tool_memory_delete(args: Mapping[str, Any], ctx: ExecutionContext) -> Any:
        key = str(args["key"])
        existed = key in ctx.memory
        ctx.memory.pop(key, None)
        return {"deleted": key, "existed": existed}

    @staticmethod
    def _tool_graph_neighbors(args: Mapping[str, Any], ctx: ExecutionContext) -> Any:
        node = str(args["node"])
        limit = max(0, min(int(args.get("limit", 64)), 512))
        return list(ctx.graph.get(node, ()))[:limit]

    @staticmethod
    def _tool_graph_hops(args: Mapping[str, Any], ctx: ExecutionContext) -> Any:
        start = str(args["node"])
        depth = int(args["depth"])
        if depth < 0 or depth > 3:
            raise InvalidToolRequest("depth must be in 0..3")
        limit = max(1, min(int(args.get("limit", 128)), 512))
        visited = {start}
        frontier = [start]
        layers: List[List[str]] = [[start]]
        for _ in range(depth):
            nxt: List[str] = []
            for node in frontier:
                for neighbor in ctx.graph.get(node, ()):
                    s = str(neighbor)
                    if s not in visited:
                        visited.add(s)
                        nxt.append(s)
                        if len(visited) >= limit:
                            break
                if len(visited) >= limit:
                    break
            layers.append(nxt)
            frontier = nxt
            if not frontier or len(visited) >= limit:
                break
        return {"start": start, "layers": layers, "visited": len(visited), "truncated": len(visited) >= limit}

    @staticmethod
    def _tool_bridge_propose(args: Mapping[str, Any], _ctx: ExecutionContext) -> Any:
        parameters = dict(args.get("parameters", {}))
        FlyAgentToolBus._assert_json_safe(parameters)
        return {
            "proposal_only": True,
            "action": str(args["action"]),
            "target": args.get("target"),
            "parameters": parameters,
            "reason": args.get("reason"),
            "execution": "NOT_PERFORMED",
        }

    def _tool_manifest(self, _args: Mapping[str, Any], _ctx: ExecutionContext) -> Any:
        return self.manifest()

    @staticmethod
    def _digest(value: Any) -> str:
        data = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(data).hexdigest()


if __name__ == "__main__":
    bus = FlyAgentToolBus(graph={"MBON-A": ["DAN-1", "KC-1"], "KC-1": ["MBON-B"]})
    demo = bus.step(
        "2+3*4を計算して",
        [
            {"tool": "memory.get", "args": {"key": "x"}, "excitation": 0.2},
            {"tool": "compute.math", "args": {"expression": "2+3*4"}, "excitation": 0.9},
        ],
    )
    print(json.dumps(demo, ensure_ascii=False, indent=2))
