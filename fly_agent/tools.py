from __future__ import annotations

import ast
import operator as op
from dataclasses import dataclass
from enum import Enum
from typing import Any, Callable

from .body import BodyArbiter
from .models import ToolContext, ToolResult


class Capability(str, Enum):
    COMPUTE = "compute"
    READ_STATE = "read_state"
    WRITE_STATE = "write_state"
    HUMAN_OUTPUT = "human_output"
    KNOWLEDGE = "knowledge"
    IR = "intermediate_representation"
    CODE = "code"
    GENERATION = "generation"
    VOICE = "voice"
    NETWORK = "network"
    DEVICE = "device"


@dataclass(slots=True)
class ToolSpec:
    name: str
    fn: Callable[..., ToolResult | Any]
    capability: Capability
    description: str = ""
    resource: str | None = None
    side_effect: bool = False
    contextual: bool = False


class ToolPolicy:
    DEFAULT_ALLOWED = {
        Capability.COMPUTE,
        Capability.READ_STATE,
        Capability.WRITE_STATE,
        Capability.HUMAN_OUTPUT,
        Capability.KNOWLEDGE,
        Capability.IR,
    }

    def __init__(self, allowed: set[Capability] | None = None):
        self.allowed = set(self.DEFAULT_ALLOWED if allowed is None else allowed)

    def allows(self, capability: Capability) -> bool:
        return capability in self.allowed

    def grant(self, *capabilities: Capability) -> None:
        self.allowed.update(capabilities)

    def revoke(self, *capabilities: Capability) -> None:
        for capability in capabilities:
            self.allowed.discard(capability)


class ToolBus:
    def __init__(self, policy: ToolPolicy | None = None, arbiter: BodyArbiter | None = None):
        self.policy = policy or ToolPolicy()
        self.arbiter = arbiter or BodyArbiter()
        self._tools: dict[str, ToolSpec] = {}

    def register(self, spec: ToolSpec) -> None:
        if not spec.name or any(ch.isspace() for ch in spec.name):
            raise ValueError("tool name must be non-empty and contain no whitespace")
        self._tools[spec.name] = spec

    def unregister(self, name: str) -> None:
        self._tools.pop(name, None)

    def names(self) -> list[str]:
        return sorted(self._tools)

    def spec(self, name: str) -> ToolSpec | None:
        return self._tools.get(name)

    def executable(self, name: str) -> bool:
        spec = self._tools.get(name)
        return bool(spec and self.policy.allows(spec.capability))

    def execute(
        self,
        name: str,
        args: dict[str, Any] | None = None,
        context: ToolContext | None = None,
        resource_timeout: float = 0.0,
    ) -> ToolResult:
        spec = self._tools.get(name)
        if not spec:
            return ToolResult.unsupported(f"unknown tool: {name}")
        if not self.policy.allows(spec.capability):
            return ToolResult.blocked(f"capability blocked: {spec.capability.value}")

        lease = None
        ctx = context or ToolContext()
        owner = ctx.thread_id or "__system__"
        if spec.resource:
            lease = self.arbiter.acquire(spec.resource, owner, timeout=resource_timeout)
            if lease is None:
                return ToolResult.retry(f"resource busy: {spec.resource}")
        try:
            raw = spec.fn(args or {}, ctx) if spec.contextual else spec.fn(args or {})
            if isinstance(raw, ToolResult):
                return raw
            return ToolResult.success(raw)
        except Exception as exc:
            return ToolResult.failed(f"{type(exc).__name__}: {exc}")
        finally:
            if lease is not None:
                self.arbiter.release(lease)


ToolRegistry = ToolBus

_BINOPS = {
    ast.Add: op.add,
    ast.Sub: op.sub,
    ast.Mult: op.mul,
    ast.Div: op.truediv,
    ast.FloorDiv: op.floordiv,
    ast.Mod: op.mod,
    ast.Pow: op.pow,
}
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
    if not expr.strip():
        return ToolResult.failed("empty expression")
    tree = ast.parse(expr, mode="eval")
    return ToolResult.success(_eval(tree))


def echo(args: dict[str, Any]) -> ToolResult:
    return ToolResult.success(str(args.get("text", "")))


def default_tools(
    output: Callable[[str], None] | None = None,
    policy: ToolPolicy | None = None,
    arbiter: BodyArbiter | None = None,
) -> ToolBus:
    bus = ToolBus(policy=policy, arbiter=arbiter)
    bus.register(ToolSpec("calculate", calculator, Capability.COMPUTE, "Safe arithmetic evaluator"))
    bus.register(ToolSpec("echo", echo, Capability.COMPUTE, "Echo data through the tool bus"))
    if output:
        def say(args: dict[str, Any]) -> ToolResult:
            text = str(args.get("text", ""))
            output(text)
            return ToolResult.success(text)
        bus.register(
            ToolSpec(
                "human.say",
                say,
                Capability.HUMAN_OUTPUT,
                "Human-facing screen/voice sink",
                resource="human_output",
            )
        )
    return bus
