from __future__ import annotations

from dataclasses import dataclass
import time
import uuid

from .mec_compute import MecNode


@dataclass(slots=True)
class ComputeLease:
    lease_id: str
    node_id: str
    granted_hps: float
    expires_at: float
    max_hashes: int
    used_hashes: int = 0
    revoked: bool = False

    @property
    def active(self) -> bool:
        return (not self.revoked) and time.time() < self.expires_at and self.used_hashes < self.max_hashes

    @property
    def remaining_hashes(self) -> int:
        return max(0, self.max_hashes - self.used_hashes)


class MecLeaseBroker:
    """Borrow only explicitly advertised idle MEC compute.

    This models a carrier/MEC platform granting temporary use of spare capacity.
    It does not assume arbitrary access to base-station hardware.
    """

    def __init__(self, nodes: dict[str, MecNode]):
        self.nodes = nodes
        self.leases: dict[str, ComputeLease] = {}

    def request_lease(
        self,
        node_id: str,
        *,
        requested_hps: float,
        duration_s: float,
        max_hashes: int,
    ) -> ComputeLease | None:
        node = self.nodes.get(node_id)
        if node is None or not node.authorized or not node.enabled:
            return None

        available_hps = node.usable_hps
        if available_hps <= 0.0:
            return None

        granted_hps = min(max(0.0, float(requested_hps)), available_hps)
        if granted_hps <= 0.0:
            return None

        duration_s = max(0.1, float(duration_s))
        max_hashes = max(1, int(max_hashes))
        lease = ComputeLease(
            lease_id=uuid.uuid4().hex[:16],
            node_id=node_id,
            granted_hps=granted_hps,
            expires_at=time.time() + duration_s,
            max_hashes=max_hashes,
        )
        self.leases[lease.lease_id] = lease
        return lease

    def consume(self, lease_id: str, hashes: int = 1) -> bool:
        lease = self.leases.get(lease_id)
        if lease is None or not lease.active:
            return False
        hashes = max(1, int(hashes))
        if hashes > lease.remaining_hashes:
            return False
        lease.used_hashes += hashes
        return True

    def revoke(self, lease_id: str) -> bool:
        lease = self.leases.get(lease_id)
        if lease is None:
            return False
        lease.revoked = True
        return True
