from __future__ import annotations

import ast
import operator as op
from dataclasses import dataclass
from enum import Enum
from typing import Any, Callable

from .models import ToolResult


class Capability(str, Enum):
    COMPUTE = "compute"
    READ_STATE = "read_state"
    WRITE_STATE = "write_state"
    HUMAN_OUTPUT = "human_output"
    NETWORK = "network"
    DEVICE = "device"


@dataclass(slots=True)
class ToolSpec:
    name: str
    fn: Callable[[dict[str, Any]], ToolResult]
    capability: Capability
    description: str = ""


class ToolPolicy:
    def __init__(self, allowed: set[Capability] | None = None):
        self.allowed = allowed or {
            Capability.COMPUTE,
            Capability.READ_STATE,
            Capability.WRITE_STATE,
            Capability.HUMAN_OUTPUT,
        }

    def allows(self, capability: Capability) -> bool:
        return capability in self.allowed


class ToolRegistry:
    def __init__(self, policy: ToolPolicy | None = None):
        self.policy = policy or ToolPolicy()
        self._tools: dict[str, ToolSpec] = {}

    def register(self, spec: ToolSpec) -> None:
        self._tools[spec.name] = spec

    def names(self) -> list[str]:
        return sorted(self._tools)

    def execute(self, name: str, args: dict[str, Any] | None = None) -> ToolResult:
        spec = self._tools.get(name)
        if not spec:
            return ToolResult(False, error=f"unknown tool: {name}")
        if not self.policy.allows(spec.capability):
            return ToolResult(False, error=f"capability blocked: {spec.capability.value}")
        try:
            return spec.fn(args or {})
        except Exception as exc:
            return ToolResult(False, error=f"{type(exc).__name__}: {exc}")


_BINOPS = {ast.Add: op.add, ast.Sub: op.sub, ast.Mult: op.mul, ast.Div: op.truediv, ast.FloorDiv: op.floordiv, ast.Mod: op.mod, ast.Pow: op.pow}
_UNARY = {ast.UAdd: op.pos, ast.USub: op.neg}


def _eval(node: ast.AST) -> float:
    if isinstance(node, ast.Expression):
        return _eval(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
        left, right = _eval(node.left), _eval(node.right)
        if isinstance(node.op, ast.Pow) and abs(right) > 12:
            raise ValueError("exponent too large")
        return float(_BINOPS[type(node.op)](left, right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARY:
        return float(_UNARY[type(node.op)](_eval(node.operand)))
    raise ValueError("unsupported expression")


def calculator(args: dict[str, Any]) -> ToolResult:
    expr = str(args.get("expression", ""))[:256]
    tree = ast.parse(expr, mode="eval")
    return ToolResult(True, _eval(tree))


def echo(args: dict[str, Any]) -> ToolResult:
    return ToolResult(True, str(args.get("text", "")))


def default_tools(output: Callable[[str], None] | None = None, policy: ToolPolicy | None = None) -> ToolRegistry:
    registry = ToolRegistry(policy)
    registry.register(ToolSpec("calculate", calculator, Capability.COMPUTE, "Safe arithmetic evaluator"))
    registry.register(ToolSpec("echo", echo, Capability.COMPUTE, "Echo data through the tool bus"))

    if output:
        def say(args: dict[str, Any]) -> ToolResult:
            text = str(args.get("text", ""))
            output(text)
            return ToolResult(True, text)
        registry.register(ToolSpec("human.say", say, Capability.HUMAN_OUTPUT, "Human-facing screen/voice adapter sink"))
    return registry
