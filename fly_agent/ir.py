from __future__ import annotations

from copy import deepcopy
from dataclasses import asdict, dataclass, field
from enum import IntEnum
import hashlib
import json
from pathlib import Path
from typing import Any, Callable, Iterable
import time
import uuid

from .checkpoint import AtomicCheckpointStore
from .models import ToolContext, ToolResult
from .tools import Capability, ToolBus, ToolSpec


class IRError(Exception):
    pass


class IRNotFoundError(IRError):
    pass


class IRConflictError(IRError):
    pass


class IRValidationError(IRError):
    pass


class EditScale(IntEnum):
    MICRO = 1
    LOCAL = 2
    STRUCTURAL = 3
    GLOBAL = 4

    @classmethod
    def parse(cls, value: "EditScale | str | int") -> "EditScale":
        if isinstance(value, cls):
            return value
        if isinstance(value, int):
            try:
                return cls(value)
            except ValueError as exc:
                raise IRValidationError(f"invalid edit scale: {value}") from exc
        if isinstance(value, str):
            try:
                return cls[value.strip().upper()]
            except KeyError as exc:
                raise IRValidationError(f"invalid edit scale: {value}") from exc
        raise IRValidationError(f"invalid edit scale type: {type(value).__name__}")


@dataclass(slots=True)
class IRPatch:
    op: str
    path: str
    value: Any = None

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "IRPatch":
        if not isinstance(raw, dict):
            raise IRValidationError("patch must be an object")
        op = str(raw.get("op", "")).strip().lower()
        if op not in {"set", "replace", "delete", "insert", "append", "merge"}:
            raise IRValidationError(f"unsupported patch op: {op or '<empty>'}")
        path = _canonical_pointer(raw.get("path", ""))
        return cls(op=op, path=path, value=deepcopy(raw.get("value")))


@dataclass(slots=True)
class IRTransaction:
    document_id: str
    patches: list[IRPatch]
    scale: EditScale
    expected_version: int | None = None
    thread_id: str | None = None
    reason: str = ""
    author: str = "fly-executive"
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:16])

    @classmethod
    def from_args(cls, args: dict[str, Any], context: ToolContext | None = None) -> "IRTransaction":
        document_id = str(args.get("document_id", "")).strip()
        if not document_id:
            raise IRValidationError("document_id is required")
        raw_patches = args.get("patches")
        if not isinstance(raw_patches, list) or not raw_patches:
            raise IRValidationError("patches must be a non-empty array")
        expected = args.get("expected_version")
        if expected is not None:
            if isinstance(expected, bool) or not isinstance(expected, int) or expected < 0:
                raise IRValidationError("expected_version must be a non-negative integer")
        transaction_id = str(args.get("transaction_id") or uuid.uuid4().hex[:16])
        return cls(
            document_id=document_id,
            patches=[IRPatch.from_dict(item) for item in raw_patches],
            scale=EditScale.parse(args.get("scale", "LOCAL")),
            expected_version=expected,
            thread_id=(context.thread_id if context else None),
            reason=str(args.get("reason", ""))[:1000],
            author="fly-executive",
            id=transaction_id,
        )


@dataclass(slots=True)
class IRDocument:
    id: str
    kind: str
    data: Any
    version: int = 0
    protected_paths: tuple[str, ...] = ()
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)

    def to_dict(self) -> dict[str, Any]:
        row = asdict(self)
        row["protected_paths"] = list(self.protected_paths)
        return row

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "IRDocument":
        copy = dict(raw)
        copy["protected_paths"] = tuple(copy.get("protected_paths", ()))
        return cls(**copy)


@dataclass(slots=True)
class IRRevision:
    transaction_id: str
    document_id: str
    from_version: int
    to_version: int
    declared_scale: EditScale
    required_scale: EditScale
    op_count: int
    before_data: Any
    thread_id: str | None
    author: str
    reason: str
    created_at: float = field(default_factory=time.time)

    def to_dict(self) -> dict[str, Any]:
        row = asdict(self)
        row["declared_scale"] = self.declared_scale.name
        row["required_scale"] = self.required_scale.name
        return row

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "IRRevision":
        copy = dict(raw)
        copy["declared_scale"] = EditScale.parse(copy["declared_scale"])
        copy["required_scale"] = EditScale.parse(copy["required_scale"])
        return cls(**copy)

    def summary(self) -> dict[str, Any]:
        return {
            "transaction_id": self.transaction_id,
            "document_id": self.document_id,
            "from_version": self.from_version,
            "to_version": self.to_version,
            "declared_scale": self.declared_scale.name,
            "required_scale": self.required_scale.name,
            "op_count": self.op_count,
            "thread_id": self.thread_id,
            "author": self.author,
            "reason": self.reason,
            "created_at": self.created_at,
        }


class IRStore:
    """Transactional, persistent intermediate-representation document store."""

    SCALE_LIMITS = {
        EditScale.MICRO: 2,
        EditScale.LOCAL: 16,
        EditScale.STRUCTURAL: 128,
        EditScale.GLOBAL: 1024,
    }

    def __init__(
        self,
        path: str | Path,
        *,
        max_document_bytes: int = 2_000_000,
        history_limit: int = 8,
    ):
        self.path = Path(path)
        self.checkpoint = AtomicCheckpointStore(self.path)
        self.max_document_bytes = int(max_document_bytes)
        self.history_limit = int(history_limit)
        self.documents: dict[str, IRDocument] = {}
        self.history: dict[str, list[IRRevision]] = {}
        self.validators: dict[str, list[Callable[[Any], None]]] = {}
        self._load()

    def register_validator(self, kind: str, validator: Callable[[Any], None]) -> None:
        self.validators.setdefault(kind, []).append(validator)

    def create(
        self,
        kind: str,
        data: Any,
        *,
        document_id: str | None = None,
        protected_paths: Iterable[str] = (),
    ) -> IRDocument:
        kind = str(kind).strip()
        if not kind:
            raise IRValidationError("kind is required")
        document_id = str(document_id or uuid.uuid4().hex[:16]).strip()
        if not document_id:
            raise IRValidationError("document_id cannot be empty")
        if document_id in self.documents:
            raise IRConflictError(f"IR document already exists: {document_id}")
        protected = tuple(sorted({_canonical_pointer(path) for path in protected_paths}))
        candidate = deepcopy(data)
        self._validate_document(kind, candidate)
        doc = IRDocument(document_id, kind, candidate, protected_paths=protected)
        self.documents[document_id] = doc
        self.history[document_id] = []
        try:
            self._persist()
        except Exception:
            self.documents.pop(document_id, None)
            self.history.pop(document_id, None)
            raise
        return deepcopy(doc)

    def get(self, document_id: str) -> IRDocument:
        doc = self.documents.get(document_id)
        if doc is None:
            raise IRNotFoundError(f"IR document not found: {document_id}")
        return deepcopy(doc)

    def list_metadata(self) -> list[dict[str, Any]]:
        return [
            {
                "id": doc.id,
                "kind": doc.kind,
                "version": doc.version,
                "protected_paths": list(doc.protected_paths),
                "updated_at": doc.updated_at,
                "size_bytes": _json_size(doc.data),
            }
            for doc in sorted(self.documents.values(), key=lambda item: item.created_at)
        ]

    def history_summaries(self, document_id: str, limit: int = 20) -> list[dict[str, Any]]:
        if document_id not in self.documents:
            raise IRNotFoundError(f"IR document not found: {document_id}")
        rows = self.history.get(document_id, [])
        return [revision.summary() for revision in rows[-max(0, int(limit)) :]]

    def preview(self, transaction: IRTransaction) -> dict[str, Any]:
        doc, candidate, required = self._prepare(transaction)
        return {
            "document_id": doc.id,
            "current_version": doc.version,
            "next_version": doc.version + 1,
            "declared_scale": transaction.scale.name,
            "required_scale": required.name,
            "op_count": len(transaction.patches),
            "before_digest": _digest(doc.data),
            "after_digest": _digest(candidate),
            "after_size_bytes": _json_size(candidate),
            "changes_state": candidate != doc.data,
        }

    def apply(self, transaction: IRTransaction) -> IRRevision:
        prior = self._find_transaction(transaction.id)
        if prior is not None:
            if prior.document_id != transaction.document_id:
                raise IRConflictError("transaction_id was already used for another document")
            return deepcopy(prior)

        doc, candidate, required = self._prepare(transaction)
        previous_data = deepcopy(doc.data)
        previous_version = doc.version
        previous_updated = doc.updated_at
        previous_history = list(self.history.get(doc.id, []))

        doc.data = candidate
        doc.version += 1
        doc.updated_at = time.time()
        revision = IRRevision(
            transaction_id=transaction.id,
            document_id=doc.id,
            from_version=previous_version,
            to_version=doc.version,
            declared_scale=transaction.scale,
            required_scale=required,
            op_count=len(transaction.patches),
            before_data=previous_data,
            thread_id=transaction.thread_id,
            author=transaction.author,
            reason=transaction.reason,
        )
        revisions = self.history.setdefault(doc.id, [])
        revisions.append(revision)
        if len(revisions) > self.history_limit:
            del revisions[:-self.history_limit]

        try:
            self._persist()
        except Exception:
            doc.data = previous_data
            doc.version = previous_version
            doc.updated_at = previous_updated
            self.history[doc.id] = previous_history
            raise
        return deepcopy(revision)

    def undo(
        self,
        document_id: str,
        *,
        expected_version: int | None = None,
        thread_id: str | None = None,
    ) -> dict[str, Any]:
        doc = self.documents.get(document_id)
        if doc is None:
            raise IRNotFoundError(f"IR document not found: {document_id}")
        if expected_version is not None and expected_version != doc.version:
            raise IRConflictError(f"version conflict: expected {expected_version}, current {doc.version}")
        revisions = self.history.get(document_id, [])
        if not revisions:
            raise IRConflictError("no revision available to undo")

        revision = revisions[-1]
        current_data = deepcopy(doc.data)
        current_version = doc.version
        current_updated = doc.updated_at
        revisions.pop()
        doc.data = deepcopy(revision.before_data)
        doc.version += 1
        doc.updated_at = time.time()
        try:
            self._persist()
        except Exception:
            doc.data = current_data
            doc.version = current_version
            doc.updated_at = current_updated
            revisions.append(revision)
            raise
        return {
            "document_id": document_id,
            "undone_transaction_id": revision.transaction_id,
            "new_version": doc.version,
            "thread_id": thread_id,
            "digest": _digest(doc.data),
        }

    def _prepare(self, transaction: IRTransaction) -> tuple[IRDocument, Any, EditScale]:
        doc = self.documents.get(transaction.document_id)
        if doc is None:
            raise IRNotFoundError(f"IR document not found: {transaction.document_id}")
        if transaction.expected_version is not None and transaction.expected_version != doc.version:
            raise IRConflictError(
                f"version conflict: expected {transaction.expected_version}, current {doc.version}"
            )
        if transaction.scale == EditScale.GLOBAL and transaction.expected_version is None:
            raise IRValidationError("GLOBAL edits require expected_version")

        required = _required_scale(transaction.patches)
        if transaction.scale < required:
            raise IRValidationError(
                f"declared scale {transaction.scale.name} is too small; required {required.name}"
            )
        max_ops = self.SCALE_LIMITS[transaction.scale]
        if len(transaction.patches) > max_ops:
            raise IRValidationError(
                f"{transaction.scale.name} edit allows at most {max_ops} operations"
            )
        if transaction.scale == EditScale.MICRO:
            for patch in transaction.patches:
                if patch.op not in {"set", "replace"} or isinstance(patch.value, (dict, list)):
                    raise IRValidationError("MICRO edits are limited to scalar set/replace operations")
                if patch.path == "":
                    raise IRValidationError("MICRO edits cannot replace the root")

        for patch in transaction.patches:
            for protected in doc.protected_paths:
                if _pointers_overlap(patch.path, protected):
                    raise IRValidationError(f"protected path overlap: {patch.path} vs {protected}")

        candidate = deepcopy(doc.data)
        for patch in transaction.patches:
            candidate = _apply_patch(candidate, patch)
        self._validate_document(doc.kind, candidate)
        return doc, candidate, required

    def _validate_document(self, kind: str, data: Any) -> None:
        size = _json_size(data)
        if size > self.max_document_bytes:
            raise IRValidationError(
                f"IR document exceeds max size: {size} > {self.max_document_bytes} bytes"
            )
        for validator in self.validators.get(kind, []):
            try:
                validator(deepcopy(data))
            except IRError:
                raise
            except Exception as exc:
                raise IRValidationError(f"validator rejected {kind}: {exc}") from exc

    def _find_transaction(self, transaction_id: str) -> IRRevision | None:
        for rows in self.history.values():
            for revision in rows:
                if revision.transaction_id == transaction_id:
                    return revision
        return None

    def _persist(self) -> None:
        payload = {
            "schema": 1,
            "documents": [doc.to_dict() for doc in self.documents.values()],
            "history": {
                document_id: [revision.to_dict() for revision in revisions]
                for document_id, revisions in self.history.items()
            },
        }
        self.checkpoint.save(payload)

    def _load(self) -> None:
        raw = self.checkpoint.load(default={})
        for item in raw.get("documents", []):
            try:
                doc = IRDocument.from_dict(item)
                self.documents[doc.id] = doc
            except (TypeError, ValueError):
                continue
        for document_id, rows in raw.get("history", {}).items():
            if document_id not in self.documents or not isinstance(rows, list):
                continue
            parsed: list[IRRevision] = []
            for row in rows[-self.history_limit :]:
                try:
                    parsed.append(IRRevision.from_dict(row))
                except (TypeError, ValueError, KeyError):
                    continue
            self.history[document_id] = parsed
        for document_id in self.documents:
            self.history.setdefault(document_id, [])


class IRController:
    """Tool surface used by the fly executive to inspect and revise intermediate state."""

    def __init__(self, path: str | Path):
        self.store = IRStore(path)

    def install(self, tools: ToolBus) -> None:
        tools.register(ToolSpec("ir.create", self._create, Capability.IR, "Create an IR document", contextual=True, side_effect=True))
        tools.register(ToolSpec("ir.get", self._get, Capability.IR, "Read an IR document"))
        tools.register(ToolSpec("ir.list", self._list, Capability.IR, "List IR documents"))
        tools.register(ToolSpec("ir.preview", self._preview, Capability.IR, "Validate an IR edit without committing", contextual=True))
        tools.register(ToolSpec("ir.patch", self._patch, Capability.IR, "Atomically patch an IR document", contextual=True, side_effect=True))
        tools.register(ToolSpec("ir.undo", self._undo, Capability.IR, "Undo the latest committed IR revision", contextual=True, side_effect=True))
        tools.register(ToolSpec("ir.history", self._history, Capability.IR, "Read IR revision metadata"))

    def _create(self, args: dict[str, Any], context: ToolContext) -> ToolResult:
        try:
            doc = self.store.create(
                str(args.get("kind", "")),
                args.get("data"),
                document_id=args.get("document_id"),
                protected_paths=args.get("protected_paths", ()),
            )
            return ToolResult.success(_document_metadata(doc))
        except IRError as exc:
            return _ir_error_result(exc)

    def _get(self, args: dict[str, Any]) -> ToolResult:
        try:
            doc = self.store.get(str(args.get("document_id", "")))
            return ToolResult.success(doc.to_dict())
        except IRError as exc:
            return _ir_error_result(exc)

    def _list(self, args: dict[str, Any]) -> ToolResult:
        return ToolResult.success(self.store.list_metadata())

    def _preview(self, args: dict[str, Any], context: ToolContext) -> ToolResult:
        try:
            transaction = IRTransaction.from_args(args, context)
            return ToolResult.success(self.store.preview(transaction))
        except IRError as exc:
            return _ir_error_result(exc)

    def _patch(self, args: dict[str, Any], context: ToolContext) -> ToolResult:
        try:
            transaction = IRTransaction.from_args(args, context)
            revision = self.store.apply(transaction)
            return ToolResult.success(revision.summary())
        except IRError as exc:
            return _ir_error_result(exc)

    def _undo(self, args: dict[str, Any], context: ToolContext) -> ToolResult:
        try:
            document_id = str(args.get("document_id", "")).strip()
            if not document_id:
                raise IRValidationError("document_id is required")
            expected = args.get("expected_version")
            if expected is not None and (isinstance(expected, bool) or not isinstance(expected, int) or expected < 0):
                raise IRValidationError("expected_version must be a non-negative integer")
            return ToolResult.success(
                self.store.undo(document_id, expected_version=expected, thread_id=context.thread_id)
            )
        except IRError as exc:
            return _ir_error_result(exc)

    def _history(self, args: dict[str, Any]) -> ToolResult:
        try:
            document_id = str(args.get("document_id", "")).strip()
            if not document_id:
                raise IRValidationError("document_id is required")
            return ToolResult.success(
                self.store.history_summaries(document_id, limit=int(args.get("limit", 20)))
            )
        except (IRError, TypeError, ValueError) as exc:
            if isinstance(exc, IRError):
                return _ir_error_result(exc)
            return ToolResult.blocked(f"invalid history request: {exc}")


def _ir_error_result(exc: IRError) -> ToolResult:
    if isinstance(exc, IRConflictError):
        return ToolResult.retry(str(exc))
    if isinstance(exc, IRValidationError):
        return ToolResult.blocked(str(exc))
    if isinstance(exc, IRNotFoundError):
        return ToolResult.failed(str(exc))
    return ToolResult.failed(str(exc))


def _document_metadata(doc: IRDocument) -> dict[str, Any]:
    return {
        "id": doc.id,
        "kind": doc.kind,
        "version": doc.version,
        "protected_paths": list(doc.protected_paths),
        "size_bytes": _json_size(doc.data),
        "digest": _digest(doc.data),
    }


def _required_scale(patches: list[IRPatch]) -> EditScale:
    if not patches:
        raise IRValidationError("empty patch list")
    required = EditScale.MICRO
    if len(patches) > 64:
        required = EditScale.GLOBAL
    elif len(patches) > 8:
        required = EditScale.STRUCTURAL
    elif len(patches) > 2:
        required = EditScale.LOCAL

    payload_bytes = 0
    for patch in patches:
        payload_bytes += _json_size(patch.value)
        depth = len(_pointer_tokens(patch.path))
        if patch.path == "":
            required = max(required, EditScale.GLOBAL)
        if patch.op in {"delete", "insert", "append", "merge"}:
            required = max(required, EditScale.LOCAL)
        if isinstance(patch.value, (dict, list)):
            required = max(required, EditScale.LOCAL)
            value_size = _json_size(patch.value)
            if depth <= 1 or value_size > 8_192:
                required = max(required, EditScale.STRUCTURAL)
    if payload_bytes > 65_536:
        required = max(required, EditScale.GLOBAL)
    elif payload_bytes > 8_192:
        required = max(required, EditScale.STRUCTURAL)
    return required


def _apply_patch(root: Any, patch: IRPatch) -> Any:
    tokens = _pointer_tokens(patch.path)
    if not tokens:
        if patch.op in {"set", "replace"}:
            return deepcopy(patch.value)
        if patch.op == "merge":
            if not isinstance(root, dict) or not isinstance(patch.value, dict):
                raise IRValidationError("root merge requires object target and object value")
            merged = deepcopy(root)
            merged.update(deepcopy(patch.value))
            return merged
        raise IRValidationError(f"{patch.op} cannot target the document root")

    parent, token = _parent(root, tokens)
    if patch.op == "set":
        _write(parent, token, patch.value, require_existing=False)
    elif patch.op == "replace":
        _write(parent, token, patch.value, require_existing=True)
    elif patch.op == "delete":
        _delete(parent, token)
    elif patch.op == "insert":
        if not isinstance(parent, list):
            raise IRValidationError("insert path must address a list index")
        index = _list_index(token, len(parent), allow_end=True)
        parent.insert(index, deepcopy(patch.value))
    elif patch.op == "append":
        target = _get(root, tokens)
        if not isinstance(target, list):
            raise IRValidationError("append path must address a list")
        target.append(deepcopy(patch.value))
    elif patch.op == "merge":
        target = _get(root, tokens)
        if not isinstance(target, dict) or not isinstance(patch.value, dict):
            raise IRValidationError("merge requires object target and object value")
        target.update(deepcopy(patch.value))
    else:
        raise IRValidationError(f"unsupported patch op: {patch.op}")
    return root


def _parent(root: Any, tokens: list[str]) -> tuple[Any, str]:
    if not tokens:
        raise IRValidationError("root has no parent")
    parent = root
    for token in tokens[:-1]:
        if isinstance(parent, dict):
            if token not in parent:
                raise IRValidationError(f"path does not exist: {token}")
            parent = parent[token]
        elif isinstance(parent, list):
            index = _list_index(token, len(parent))
            parent = parent[index]
        else:
            raise IRValidationError("path traverses a scalar value")
    return parent, tokens[-1]


def _get(root: Any, tokens: list[str]) -> Any:
    value = root
    for token in tokens:
        if isinstance(value, dict):
            if token not in value:
                raise IRValidationError(f"path does not exist: {token}")
            value = value[token]
        elif isinstance(value, list):
            value = value[_list_index(token, len(value))]
        else:
            raise IRValidationError("path traverses a scalar value")
    return value


def _write(parent: Any, token: str, value: Any, *, require_existing: bool) -> None:
    if isinstance(parent, dict):
        if require_existing and token not in parent:
            raise IRValidationError(f"replace target does not exist: {token}")
        parent[token] = deepcopy(value)
        return
    if isinstance(parent, list):
        index = _list_index(token, len(parent))
        parent[index] = deepcopy(value)
        return
    raise IRValidationError("write target parent is scalar")


def _delete(parent: Any, token: str) -> None:
    if isinstance(parent, dict):
        if token not in parent:
            raise IRValidationError(f"delete target does not exist: {token}")
        del parent[token]
        return
    if isinstance(parent, list):
        del parent[_list_index(token, len(parent))]
        return
    raise IRValidationError("delete target parent is scalar")


def _list_index(token: str, length: int, *, allow_end: bool = False) -> int:
    if not token.isdigit():
        raise IRValidationError(f"invalid list index: {token}")
    index = int(token)
    upper = length if allow_end else length - 1
    if index < 0 or index > upper:
        raise IRValidationError(f"list index out of range: {index}")
    return index


def _canonical_pointer(value: Any) -> str:
    if value is None:
        return ""
    path = str(value)
    if path == "":
        return ""
    if not path.startswith("/"):
        raise IRValidationError(f"IR path must be a JSON pointer: {path}")
    _pointer_tokens(path)
    return path


def _pointer_tokens(path: str) -> list[str]:
    if path == "":
        return []
    if not path.startswith("/"):
        raise IRValidationError(f"IR path must be a JSON pointer: {path}")
    tokens = path[1:].split("/")
    decoded: list[str] = []
    for token in tokens:
        i = 0
        out = ""
        while i < len(token):
            if token[i] != "~":
                out += token[i]
                i += 1
                continue
            if i + 1 >= len(token) or token[i + 1] not in {"0", "1"}:
                raise IRValidationError(f"invalid JSON pointer escape in {path}")
            out += "~" if token[i + 1] == "0" else "/"
            i += 2
        decoded.append(out)
    return decoded


def _pointers_overlap(left: str, right: str) -> bool:
    if left == "" or right == "":
        return True
    return left == right or left.startswith(right + "/") or right.startswith(left + "/")


def _json_bytes(data: Any) -> bytes:
    try:
        return json.dumps(
            data,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise IRValidationError(f"IR data is not JSON-serializable: {exc}") from exc


def _json_size(data: Any) -> int:
    return len(_json_bytes(data))


def _digest(data: Any) -> str:
    return hashlib.sha256(_json_bytes(data)).hexdigest()
