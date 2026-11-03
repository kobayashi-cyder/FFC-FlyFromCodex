#!/usr/bin/env python3
"""Source-level proof that v1.153 does not require the Shizuku provider/app.

Not an Android APK build or on-device privilege escalation test.
"""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[3]
app=root/"markertap/code/TapMarker"
src=app/"src/jp/tapmarker"
manifest=(app/"AndroidManifest.xml").read_text(encoding="utf-8")
windows=(src/"VirtualWindows.java").read_text(encoding="utf-8")
wireless=(src/"WirelessAdb.java").read_text(encoding="utf-8")
mode=(src/"WirelessModePolicy.java").read_text(encoding="utf-8")
bridge=(src/"VirtualBridgeProvider.java").read_text(encoding="utf-8")
launcher=(src/"SplitLauncher.java").read_text(encoding="utf-8")
keys=(src/"WirelessKeys.java").read_text(encoding="utf-8")
for p in src.glob("*.java"):
    s=p.read_text(encoding="utf-8")
    assert "import rikka.shizuku" not in s, f"Shizuku dependency in {p.name}"
    assert "Shizuku." not in s, f"External Shizuku class call in {p.name}"
assert "ShizukuProvider" not in manifest
assert "moe.shizuku" not in manifest
assert "API_V23" not in manifest
assert 's.pilot.wireless.guide(true);return;' in windows
assert 'void openAdb()' in windows
assert 'onSingleDisplayRemoved(w,id)' in windows
assert "shouldReconnectPaired" in mode and "shouldReconnectPaired(" in wireless
assert 'getBoolean("paired",false)' in wireless
assert 'new AdbConnection.Builder("127.0.0.1",port)' in wireless
assert "uid!=2000&&uid!=0" in bridge
assert 'ACCESS_NETWORK_STATE' in manifest
assert "AndroidKeystore" not in keys or "AndroidKeyStore" in keys
assert '内蔵ADB' in launcher
assert 'Shizuku接続' not in launcher

with tempfile.TemporaryDirectory() as out:
    sources=[src/"WirelessModePolicy.java",app/"tests/WirelessModePolicyTest.java",
             src/"LocalLaunch.java"]
    subprocess.run(["javac","--release","8","-encoding","UTF-8","-d",out]
                   +[str(p) for p in sources],check=True)
    subprocess.run(["java","-cp",out,"jp.tapmarker.WirelessModePolicyTest"],check=True)
print("PASS: no Shizuku runtime/provider dependency; local ADB pairing/security guards present")
