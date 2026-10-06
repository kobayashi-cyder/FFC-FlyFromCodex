from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import math
import time
from typing import Iterable

from .connectome import GraphConnectomeKernel, default_connectome
from .models import Stimulus


@dataclass(frozen=True, slots=True)
class GeoPoint:
    """Coarse location used for MEC selection.

    Keep this at cell/region precision in production unless exact coordinates are
    truly required. The dispatcher only needs relative proximity.
    """

    lat: float
    lon: float


@dataclass(slots=True)
class MecNode:
    id: str
    location: GeoPoint
    compute_hps: float
    free_fraction: float = 1.0
    latency_ms: float = 20.0
    authorized: bool = False
    enabled: bool = True
    labels: set[str] = field(default_factory=set)

    @property
    def usable_hps(self) -> float:
        if not self.enabled or not self.authorized:
            return 0.0
        return max(0.0, self.compute_hps) * max(0.0, min(1.0, self.free_fraction))


@dataclass(frozen=True, slots=True)
class PowJob:
    """Bitcoin-style 80-byte header search job.

    header_prefix_hex is exactly the first 76 bytes of a block header. Workers
    append the 4-byte nonce in little-endian order, run double SHA-256, and only
    return hashes that satisfy target.
    """

    job_id: str
    header_prefix_hex: str
    target: int
    nonce_start: int = 0
    nonce_end: int = 0xFFFFFFFF
    expires_at: float | None = None

    def validate(self) -> None:
        prefix = bytes.fromhex(self.header_prefix_hex)
        if len(prefix) != 76:
            raise ValueError("header_prefix_hex must encode exactly 76 bytes")
        if not (0 < int(self.target) < (1 << 256)):
            raise ValueError("target must be in (0, 2^256)")
        if not (0 <= self.nonce_start <= self.nonce_end <= 0xFFFFFFFF):
            raise ValueError("invalid nonce range")


@dataclass(frozen=True, slots=True)
class PowAssignment:
    job_id: str
    node_id: str
    nonce_start: int
    nonce_end: int


@dataclass(frozen=True, slots=True)
class PowSolution:
    job_id: str
    node_id: str
    nonce: int
    hash_hex: str
    header_hex: str


class MecPowWorker:
    """Authorized MEC worker. Failed attempts are never returned."""

    def __init__(self, node: MecNode):
        self.node = node

    def search(self, job: PowJob, assignment: PowAssignment) -> PowSolution | None:
        job.validate()
        if not self.node.authorized or not self.node.enabled:
            raise PermissionError(f"MEC node {self.node.id} is not authorized for compute")
        if assignment.node_id != self.node.id or assignment.job_id != job.job_id:
            raise ValueError("assignment does not match node/job")
        if assignment.nonce_start < job.nonce_start or assignment.nonce_end > job.nonce_end:
            raise ValueError("assignment is outside job nonce range")
        if job.expires_at is not None and time.time() >= job.expires_at:
            return None

        prefix = bytes.fromhex(job.header_prefix_hex)
        for nonce in range(assignment.nonce_start, assignment.nonce_end + 1):
            if job.expires_at is not None and time.time() >= job.expires_at:
                return None
            header = prefix + int(nonce).to_bytes(4, "little", signed=False)
            digest = hashlib.sha256(hashlib.sha256(header).digest()).digest()
            # Bitcoin's uint256 comparison treats the digest as a little-endian integer.
            value = int.from_bytes(digest, "little", signed=False)
            if value <= job.target:
                # Only a successful result leaves the MEC worker.
                return PowSolution(
                    job_id=job.job_id,
                    node_id=self.node.id,
                    nonce=nonce,
                    hash_hex=digest[::-1].hex(),
                    header_hex=header.hex(),
                )
        return None


def haversine_km(a: GeoPoint, b: GeoPoint) -> float:
    radius_km = 6371.0088
    lat1 = math.radians(a.lat)
    lat2 = math.radians(b.lat)
    dlat = lat2 - lat1
    dlon = math.radians(b.lon - a.lon)
    h = math.sin(dlat / 2.0) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0) ** 2
    return 2.0 * radius_km * math.asin(min(1.0, math.sqrt(h)))


class ConnectomeMecDispatcher:
    """Fly-connectome-gated dispatcher for authorized location-aware MEC compute.

    The connectome decides whether delegation is appropriate. Among authorized
    nodes, a deterministic score favors proximity, low latency, and spare compute.
    Nonce ranges are disjoint so workers do not repeat each other's search.
    """

    def __init__(
        self,
        nodes: Iterable[MecNode],
        connectome: GraphConnectomeKernel | None = None,
    ):
        self.nodes = {node.id: node for node in nodes}
        self.connectome = connectome or default_connectome()

    def _delegation_allowed(self) -> bool:
        intent = self.connectome.route(
            [
                Stimulus("human_command", 1.0, 1.0, {"task": "mec_pow"}),
                Stimulus("feedback_quality", 0.25, 0.5, {"task": "mec_pow"}),
            ]
        )
        return intent is not None and intent.action == "delegate"

    def rank_nodes(self, requester: GeoPoint) -> list[MecNode]:
        candidates = [node for node in self.nodes.values() if node.usable_hps > 0.0]

        def score(node: MecNode) -> float:
            distance = haversine_km(requester, node.location)
            locality = 1.0 / (1.0 + distance)
            latency = 1.0 / (1.0 + max(0.0, node.latency_ms) / 10.0)
            capacity = math.log2(1.0 + node.usable_hps)
            return capacity * (0.55 + 0.30 * locality + 0.15 * latency)

        return sorted(candidates, key=score, reverse=True)

    def assign(
        self,
        job: PowJob,
        requester: GeoPoint,
        *,
        max_nodes: int = 4,
        chunk_size: int = 1_000_000,
    ) -> list[PowAssignment]:
        job.validate()
        if not self._delegation_allowed():
            return []
        ranked = self.rank_nodes(requester)[: max(0, int(max_nodes))]
        if not ranked:
            return []

        assignments: list[PowAssignment] = []
        cursor = job.nonce_start
        for node in ranked:
            if cursor > job.nonce_end:
                break
            end = min(job.nonce_end, cursor + max(1, int(chunk_size)) - 1)
            assignments.append(PowAssignment(job.job_id, node.id, cursor, end))
            cursor = end + 1
        return assignments

    def execute_first_success(
        self,
        job: PowJob,
        requester: GeoPoint,
        *,
        max_nodes: int = 4,
        chunk_size: int = 1_000_000,
    ) -> PowSolution | None:
        """Run assigned workers and expose only the first valid solution.

        This is a synchronous simulator. A production MEC adapter can execute the
        same assignments concurrently and stream only verified solutions back.
        """

        for assignment in self.assign(job, requester, max_nodes=max_nodes, chunk_size=chunk_size):
            solution = MecPowWorker(self.nodes[assignment.node_id]).search(job, assignment)
            if solution is not None and verify_solution(job, solution):
                return solution
        return None


def verify_solution(job: PowJob, solution: PowSolution) -> bool:
    """Verify a returned solution locally before submission to a pool/node."""

    try:
        job.validate()
        header = bytes.fromhex(solution.header_hex)
        if len(header) != 80:
            return False
        if header[:76].hex() != job.header_prefix_hex.lower():
            return False
        nonce = int.from_bytes(header[76:80], "little", signed=False)
        if nonce != solution.nonce or not (job.nonce_start <= nonce <= job.nonce_end):
            return False
        digest = hashlib.sha256(hashlib.sha256(header).digest()).digest()
        if digest[::-1].hex() != solution.hash_hex.lower():
            return False
        return int.from_bytes(digest, "little", signed=False) <= job.target
    except (TypeError, ValueError):
        return False
