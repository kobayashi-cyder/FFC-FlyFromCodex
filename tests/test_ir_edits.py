import pytest

from fly_agent import (
    Capability,
    EditScale,
    FlyMachineAgent,
    GoalStatus,
    IRConflictError,
    IRPatch,
    IRStore,
    IRTransaction,
    IRValidationError,
    ResultStatus,
    ToolBus,
    ToolContext,
    ToolPolicy,
    ToolResult,
    ToolSpec,
)
from fly_agent.ir import IRController


def transaction(document_id, patches, scale, version, transaction_id=None):
    return IRTransaction(
        document_id=document_id,
        patches=[IRPatch.from_dict(patch) for patch in patches],
        scale=EditScale.parse(scale),
        expected_version=version,
        id=transaction_id or f"txn-{version}-{scale}",
    )


def test_micro_local_structural_and_global_edits(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("scene", {"camera": {"fov": 60}, "objects": [{"id": "a"}], "mode": "draft"})

    store.apply(transaction(doc.id, [{"op": "replace", "path": "/camera/fov", "value": 65}], "MICRO", 0))
    assert store.get(doc.id).data["camera"]["fov"] == 65

    store.apply(transaction(doc.id, [{"op": "append", "path": "/objects", "value": {"id": "b"}}], "LOCAL", 1))
    assert [item["id"] for item in store.get(doc.id).data["objects"]] == ["a", "b"]

    store.apply(transaction(
        doc.id,
        [{"op": "replace", "path": "/objects", "value": [{"id": "x"}, {"id": "y"}]}],
        "STRUCTURAL",
        2,
    ))
    assert store.get(doc.id).data["objects"][0]["id"] == "x"

    store.apply(transaction(
        doc.id,
        [{"op": "replace", "path": "", "value": {"camera": {"fov": 80}, "objects": [], "mode": "final"}}],
        "GLOBAL",
        3,
    ))
    final = store.get(doc.id)
    assert final.version == 4
    assert final.data["mode"] == "final"


def test_declared_scale_cannot_hide_larger_change(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("scene", {"objects": [{"id": "a"}]})
    with pytest.raises(IRValidationError, match="too small"):
        store.apply(transaction(
            doc.id,
            [{"op": "replace", "path": "/objects", "value": [{"id": "x"}]}],
            "MICRO",
            0,
        ))


def test_multi_patch_transaction_is_atomic(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("state", {"a": 1, "b": 2})
    with pytest.raises(IRValidationError):
        store.apply(transaction(
            doc.id,
            [
                {"op": "replace", "path": "/a", "value": 9},
                {"op": "delete", "path": "/missing"},
            ],
            "LOCAL",
            0,
        ))
    unchanged = store.get(doc.id)
    assert unchanged.version == 0
    assert unchanged.data == {"a": 1, "b": 2}


def test_protected_paths_block_direct_and_ancestor_edits(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create(
        "plan",
        {"safe": {"policy": {"device": False}}, "steps": []},
        protected_paths=["/safe/policy"],
    )
    with pytest.raises(IRValidationError, match="protected"):
        store.apply(transaction(
            doc.id,
            [{"op": "replace", "path": "/safe/policy/device", "value": True}],
            "MICRO",
            0,
        ))
    with pytest.raises(IRValidationError, match="protected"):
        store.apply(transaction(
            doc.id,
            [{"op": "replace", "path": "/safe", "value": {}}],
            "STRUCTURAL",
            0,
        ))


def test_version_conflict_is_detected(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("state", {"x": 1})
    store.apply(transaction(doc.id, [{"op": "replace", "path": "/x", "value": 2}], "MICRO", 0))
    with pytest.raises(IRConflictError, match="version conflict"):
        store.apply(transaction(doc.id, [{"op": "replace", "path": "/x", "value": 3}], "MICRO", 0, "stale"))


def test_global_edit_requires_explicit_version(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("state", {"x": 1})
    txn = IRTransaction(
        document_id=doc.id,
        patches=[IRPatch.from_dict({"op": "replace", "path": "", "value": {"x": 2}})],
        scale=EditScale.GLOBAL,
        expected_version=None,
    )
    with pytest.raises(IRValidationError, match="expected_version"):
        store.apply(txn)


def test_preview_is_non_mutating_and_reports_required_scale(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("state", {"items": [1]})
    preview = store.preview(transaction(
        doc.id,
        [{"op": "append", "path": "/items", "value": 2}],
        "LOCAL",
        0,
    ))
    assert preview["changes_state"] is True
    assert preview["required_scale"] == "LOCAL"
    assert store.get(doc.id).version == 0
    assert store.get(doc.id).data == {"items": [1]}


def test_undo_survives_restart(tmp_path):
    path = tmp_path / "ir.json"
    store = IRStore(path)
    doc = store.create("state", {"x": 1})
    store.apply(transaction(doc.id, [{"op": "replace", "path": "/x", "value": 2}], "MICRO", 0))
    reopened = IRStore(path)
    result = reopened.undo(doc.id, expected_version=1, thread_id="A")
    assert result["new_version"] == 2
    assert reopened.get(doc.id).data["x"] == 1


def test_transaction_id_is_idempotent(tmp_path):
    store = IRStore(tmp_path / "ir.json")
    doc = store.create("state", {"x": 1})
    txn = transaction(doc.id, [{"op": "replace", "path": "/x", "value": 2}], "MICRO", 0, "same-id")
    first = store.apply(txn)
    second = store.apply(txn)
    assert first.transaction_id == second.transaction_id
    assert store.get(doc.id).version == 1


def test_ir_tool_records_actual_thread_context(tmp_path):
    bus = ToolBus(ToolPolicy({Capability.IR}))
    controller = IRController(tmp_path / "ir.json")
    controller.install(bus)
    created = bus.execute(
        "ir.create",
        {"kind": "state", "data": {"x": 1}, "document_id": "doc"},
        ToolContext(thread_id="thread-a"),
    )
    assert created.ok
    edited = bus.execute(
        "ir.patch",
        {
            "document_id": "doc",
            "patches": [{"op": "replace", "path": "/x", "value": 2}],
            "scale": "MICRO",
            "expected_version": 0,
            "transaction_id": "ctx-edit",
        },
        ToolContext(thread_id="thread-b"),
    )
    assert edited.ok
    history = bus.execute("ir.history", {"document_id": "doc"})
    assert history.output[-1]["thread_id"] == "thread-b"


def test_runtime_exposes_fly_ir_editing(tmp_path):
    agent = FlyMachineAgent(state_dir=tmp_path, output=lambda _: None)
    created = agent.create_ir("scene", {"camera": {"fov": 60}}, document_id="scene-1")
    assert created.ok
    preview = agent.preview_ir_edit(
        "scene-1",
        [{"op": "replace", "path": "/camera/fov", "value": 70}],
        scale="MICRO",
        expected_version=0,
    )
    assert preview.ok
    changed = agent.edit_ir(
        "scene-1",
        [{"op": "replace", "path": "/camera/fov", "value": 70}],
        scale="MICRO",
        expected_version=0,
    )
    assert changed.ok
    assert agent.ir.store.get("scene-1").data["camera"]["fov"] == 70
    assert agent.status()["ir_documents"][0]["id"] == "scene-1"


def test_machine_planner_can_only_modify_ir_through_executive_and_toolbus(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE, Capability.IR})
    bus = ToolBus(policy)
    holder = {"document_id": None}

    def propose(_):
        return ToolResult.success({
            "steps": [{
                "tool": "ir.patch",
                "args": {
                    "document_id": holder["document_id"],
                    "patches": [{"op": "replace", "path": "/score", "value": 9}],
                    "scale": "MICRO",
                    "expected_version": 0,
                    "transaction_id": "planner-ir-1",
                },
            }]
        })

    bus.register(ToolSpec("planner.propose", propose, Capability.COMPUTE))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    created = agent.create_ir("candidate", {"score": 1}, document_id="candidate")
    assert created.ok
    holder["document_id"] = "candidate"

    goal = agent.submit_goal("improve the candidate")
    assert agent.run() == 1
    assert goal.status == GoalStatus.DONE
    assert agent.ir.store.get("candidate").data["score"] == 9


def test_planner_cannot_disguise_global_change_as_micro(tmp_path):
    policy = ToolPolicy({Capability.COMPUTE, Capability.IR})
    bus = ToolBus(policy)

    def propose(_):
        return ToolResult.success({
            "steps": [{
                "tool": "ir.patch",
                "args": {
                    "document_id": "candidate",
                    "patches": [{"op": "replace", "path": "", "value": {"score": 100}}],
                    "scale": "MICRO",
                    "expected_version": 0,
                },
            }]
        })

    bus.register(ToolSpec("planner.propose", propose, Capability.COMPUTE))
    agent = FlyMachineAgent(state_dir=tmp_path, tools=bus, output=lambda _: None)
    assert agent.create_ir("candidate", {"score": 1}, document_id="candidate").ok
    goal = agent.submit_goal("rewrite everything")
    assert agent.run() == 1
    assert goal.status == GoalStatus.BLOCKED
    assert agent.ir.store.get("candidate").data["score"] == 1


def test_ir_capability_can_be_revoked(tmp_path):
    bus = ToolBus(ToolPolicy({Capability.COMPUTE}))
    controller = IRController(tmp_path / "ir.json")
    controller.install(bus)
    result = bus.execute("ir.create", {"kind": "state", "data": {"x": 1}})
    assert result.status == ResultStatus.BLOCKED
