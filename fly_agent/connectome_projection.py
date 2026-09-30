from __future__ import annotations

from collections import defaultdict, deque
import csv
import json
import math
from pathlib import Path
from typing import Any, Iterable


class ConnectomeProjectionError(ValueError):
    pass


def load_role_map(path: str | Path) -> dict[str, dict[str, Any]]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    roles: dict[str, dict[str, Any]] = {}
    for kind in ("sensory", "motor", "interneuron"):
        for item in data.get(kind, []):
            if not isinstance(item, dict) or "id" not in item:
                raise ConnectomeProjectionError(f"{kind} role requires id")
            node_id = str(item["id"])
            role = dict(item)
            role["kind"] = kind
            roles[node_id] = role
    if not any(role["kind"] == "sensory" for role in roles.values()):
        raise ConnectomeProjectionError("role map needs at least one sensory node")
    if not any(role["kind"] == "motor" for role in roles.values()):
        raise ConnectomeProjectionError("role map needs at least one motor node")
    return roles


def read_edge_csv(path: str | Path) -> list[tuple[str, str, float]]:
    edges: list[tuple[str, str, float]] = []
    with Path(path).open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        required = {"src", "dst", "weight"}
        if not required.issubset(reader.fieldnames or []):
            raise ConnectomeProjectionError("edge CSV requires src,dst,weight columns")
        for row in reader:
            src, dst = str(row["src"]).strip(), str(row["dst"]).strip()
            if not src or not dst:
                continue
            try:
                weight = float(row["weight"])
            except (TypeError, ValueError) as exc:
                raise ConnectomeProjectionError(f"invalid weight for {src}->{dst}") from exc
            edges.append((src, dst, weight))
    return edges


def _normalize_weight(raw: float, scale: float) -> float:
    if scale <= 0:
        raise ConnectomeProjectionError("weight_scale must be > 0")
    sign = -1.0 if raw < 0 else 1.0
    return sign * min(4.0, math.log1p(abs(raw)) / math.log1p(scale) * 2.0)


def compile_projection(
    edges: Iterable[tuple[str, str, float]],
    roles: dict[str, dict[str, Any]],
    *,
    max_hops: int = 3,
    weight_scale: float = 100.0,
    ticks: int = 6,
    leak: float = 0.30,
) -> dict[str, Any]:
    if max_hops < 0:
        raise ConnectomeProjectionError("max_hops must be >= 0")
    edge_list = list(edges)
    adjacency: dict[str, set[str]] = defaultdict(set)
    for src, dst, _ in edge_list:
        adjacency[src].add(dst)
        adjacency[dst].add(src)

    seeds = set(roles)
    included = set(seeds)
    queue = deque((node_id, 0) for node_id in seeds)
    while queue:
        node_id, depth = queue.popleft()
        if depth >= max_hops:
            continue
        for neighbor in adjacency.get(node_id, ()):
            if neighbor not in included:
                included.add(neighbor)
                queue.append((neighbor, depth + 1))

    nodes: list[dict[str, Any]] = []
    for node_id in sorted(included):
        role = roles.get(node_id, {})
        node = {
            "id": node_id,
            "kind": role.get("kind", "interneuron"),
            "bias": float(role.get("bias", 0.0)),
        }
        if node["kind"] == "sensory":
            channel = role.get("channel")
            if not channel:
                raise ConnectomeProjectionError(f"sensory node {node_id} requires channel")
            node["channel"] = str(channel)
        if node["kind"] == "motor":
            action = role.get("action")
            if not action:
                raise ConnectomeProjectionError(f"motor node {node_id} requires action")
            node["action"] = str(action)
        nodes.append(node)

    projected_edges = [
        {"src": src, "dst": dst, "weight": _normalize_weight(weight, weight_scale)}
        for src, dst, weight in edge_list
        if src in included and dst in included
    ]
    if not projected_edges:
        raise ConnectomeProjectionError("projection contains no edges")
    return {
        "format": "fly-agent-connectome-projection-v1",
        "ticks": int(ticks),
        "leak": float(leak),
        "metadata": {
            "node_count": len(nodes),
            "edge_count": len(projected_edges),
            "max_hops": max_hops,
            "weight_scale": weight_scale,
            "note": "Role IDs must come from a verified biological/source mapping; this compiler does not infer biological semantics.",
        },
        "nodes": nodes,
        "edges": projected_edges,
    }
