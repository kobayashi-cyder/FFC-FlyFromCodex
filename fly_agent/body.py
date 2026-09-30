from __future__ import annotations

from dataclasses import dataclass
import threading
import time
import uuid


@dataclass(slots=True)
class BodyLease:
    resource: str
    owner_thread_id: str
    lease_id: str
    acquired_at: float


@dataclass(slots=True)
class _LeaseState:
    lease: BodyLease
    depth: int = 1


class BodyArbiter:
    """Serializes shared physical/UI resources across logical agent threads."""

    def __init__(self):
        self._condition = threading.Condition(threading.RLock())
        self._leases: dict[str, _LeaseState] = {}

    def acquire(self, resource: str, owner_thread_id: str, timeout: float = 0.0) -> BodyLease | None:
        if not resource:
            raise ValueError("resource is required")
        if not owner_thread_id:
            owner_thread_id = "__system__"
        deadline = time.monotonic() + max(0.0, timeout)
        with self._condition:
            while True:
                current = self._leases.get(resource)
                if current is None:
                    lease = BodyLease(resource, owner_thread_id, uuid.uuid4().hex[:12], time.time())
                    self._leases[resource] = _LeaseState(lease)
                    return lease
                if current.lease.owner_thread_id == owner_thread_id:
                    current.depth += 1
                    return current.lease
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return None
                self._condition.wait(timeout=remaining)

    def release(self, lease: BodyLease) -> None:
        with self._condition:
            current = self._leases.get(lease.resource)
            if current is None:
                return
            if current.lease.lease_id != lease.lease_id or current.lease.owner_thread_id != lease.owner_thread_id:
                raise RuntimeError("lease ownership mismatch")
            current.depth -= 1
            if current.depth <= 0:
                del self._leases[lease.resource]
                self._condition.notify_all()

    def snapshot(self) -> dict[str, dict[str, str | float | int]]:
        with self._condition:
            return {
                resource: {
                    "owner_thread_id": state.lease.owner_thread_id,
                    "lease_id": state.lease.lease_id,
                    "acquired_at": state.lease.acquired_at,
                    "depth": state.depth,
                }
                for resource, state in self._leases.items()
            }
