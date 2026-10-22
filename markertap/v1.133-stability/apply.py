#!/usr/bin/env python3
"""Fail-closed v1.133 forward port; preserve the exact v1.132 source lineage."""
from pathlib import Path


def replace_once(path, before, after):
    src = Path(path).read_text(encoding="utf-8")
    if src.count(before) != 1:
        raise RuntimeError(f"{path}: expected one unchanged anchor, got {src.count(before)}")
    Path(path).write_text(src.replace(before, after), encoding="utf-8")


def apply(project):
    # The v1.130 source bundle deliberately omits newer runtime sources.
    # Restore v1.132 first, then overlay the current branch's exact Java sources.
    # Never pretend a non-existent archived file has been patched.
    root = Path(__file__).resolve().parents[2]
    for relative in [
        # VirtualWindows from the current branch is not source-compatible with
        # the archived 1.130 foundation. Port the full, mutually compatible
        # safety/preview dependency closure instead of stubbing features away.
        "src/jp/tapmarker/VirtualWindows.java",
        "src/jp/tapmarker/BackendRecoveryBudget.java",
        "src/jp/tapmarker/PreviewConnectome.java",
        "src/jp/tapmarker/RegionPreviewData.java",
        "src/jp/tapmarker/RegionPreviewView.java",
        "src/jp/tapmarker/TapService.java",
        "src/jp/tapmarker/VirtualBridgeProvider.java",
        "src/jp/tapmarker/VirtualProtocol.java",
        "src/jp/tapmarker/VirtualShell.java",
        "src/jp/tapmarker/VirtualResolutionLab.java",
        "src/jp/tapmarker/VirtualResolutionPolicy.java",
        "tests/FrameCircuitTest.java",
        "tests/PreviewConnectomeTest.java",
    ]:
        source = root / "markertap/code/TapMarker" / relative
        target = project / relative
        if not source.is_file():
            raise RuntimeError("missing canonical source: " + str(source))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
    src = project / "src/jp/tapmarker/VirtualWindows.java"
    runtime = src.read_text(encoding="utf-8")
    test_source = (project / "tests/FrameCircuitTest.java").read_text(encoding="utf-8")
    for expected in [
        "new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS",
        "w.frameCircuit.run(request)",
        "final FrameCircuit frameCircuit=new FrameCircuit(SystemClock::uptimeMillis)",
        "analysisPending.compareAndSet(false,true)",
    ]:
        if expected not in runtime:
            raise RuntimeError("unverified critical implementation: " + expected)
    if "independentWindows();" not in test_source:
        raise RuntimeError("missing independent display regression test")
    replace_once(project / "tools/package_apk.py",
        'def manifest_version(data, code=135, version="1.132"):',
        'def manifest_version(data, code=136, version="1.133"):')
    replace_once(project / "tools/PatchRuntime.java",
        'if(text.equals("v1.123"))return "v1.132";',
        'if(text.equals("v1.123"))return "v1.133";')
    for name in ["build.sh", "run-host-tests.sh"]:
        path = project / name
        source = path.read_text(encoding="utf-8")
        if "1.132" not in source:
            raise RuntimeError(name + ": unexpected version lineage")
        path.write_text(source.replace("1.132", "1.133").replace("versionCode135", "versionCode136"), encoding="utf-8")
    # Base 1.132 ships FrameLockBridge compiled against a *different*
    # VirtualWindows synthetic lambda constructor. The complete current
    # VirtualWindows now handles bounded frame copy/preview internally; the
    # unreferenced stale bridge must not survive in the merged DEX.
    # DexAudit will fail closed if anything still calls the removed bridge.
    replace_once(project / "tools/PatchRuntime.java",
        'owners.add("Ljp/tapmarker/FlyCircuit");',
        'owners.add("Ljp/tapmarker/FlyCircuit");\n'
        '  owners.add("Ljp/tapmarker/FrameLockBridge"); // obsolete 1.132 compiled lambda bridge')
    # 1.130 archive still compiles a legacy FrameLockBridge using
    # VirtualWindows$ExternalSyntheticLambda29's obsolete constructor.
    # Do not package that class alongside the canonical v1.133 window owner.
    stale_bridge = project / "src/jp/tapmarker/FrameLockBridge.java"
    if not stale_bridge.is_file():
        raise RuntimeError("expected archived FrameLockBridge source missing")
    bridge_source = stale_bridge.read_text(encoding="utf-8")
    if "class FrameLockBridge" not in bridge_source:
        raise RuntimeError("archived FrameLockBridge no longer matches verified legacy source")
    if "FrameLockBridge" in runtime:
        raise RuntimeError("v1.133 VirtualWindows unexpectedly depends on legacy bridge")
    stale_bridge.unlink()
    # Existing v1.132 tests and signature checks remain mandatory.
    return ["virtual-window-isolation", "dual-circuit-parallel-regression", "manifest-1.133-code-136"]


if __name__ == "__main__":
    import sys
    print(apply(Path(sys.argv[1])))
