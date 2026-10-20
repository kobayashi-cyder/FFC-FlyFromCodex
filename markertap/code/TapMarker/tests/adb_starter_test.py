"""Test host-side device selection and command safety with a mock ADB, not a phone."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

STARTER = Path(__file__).resolve().parents[1] / "adb" / "start-adb.sh"
FAKE = r'''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
c=json.loads(Path(os.environ["TAPMARKER_MOCK_CONFIG"]).read_text())
a=sys.argv[1:]
with open(os.environ["TAPMARKER_MOCK_CALLS"],"a") as f: f.write(json.dumps(a)+"\n")
if a==["devices"]:
 print("List of devices attached")
 for device,state in c["devices"]: print(device+"\t"+state)
elif len(a)>2 and a[0]=="-s" and a[2:]==["get-state"]:
 print(dict(c["devices"]).get(a[1],"offline"))
elif len(a)>2 and a[0]=="-s" and a[2:]==["shell","pm","path","jp.tapmarker.lite"]:
 print(c.get("path",""))
elif len(a)==4 and a[0]=="-s" and a[2]=="shell":
 print("TAPMARKER_ADB_CONNECTED")
else: sys.exit(2)
'''


def run(config, args=()):
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        fake = root / "adb"
        fake.write_text(FAKE)
        fake.chmod(0o755)
        (root / "config.json").write_text(json.dumps(config))
        env = dict(os.environ, TAPMARKER_ADB=str(fake),
                   TAPMARKER_MOCK_CONFIG=str(root / "config.json"),
                   TAPMARKER_MOCK_CALLS=str(root / "calls.jsonl"))
        result = subprocess.run(["bash", str(STARTER), *args], env=env,
                                text=True, capture_output=True, timeout=10)
        calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
        launches = [call for call in calls if len(call) == 4 and call[2] == "shell"]
        return result, launches


path = "/data/app/~~test==/jp.tapmarker-test==/base.apk"
good = {"devices": [("phone1", "device")], "path": "package:" + path + "\r\n"}
result, launches = run(good)
assert result.returncode == 0 and launches == [["-s", "phone1", "shell",
    "CLASSPATH='" + path + "' app_process /system/bin jp.tapmarker.VirtualBootstrap"]]
result, launches = run(good, ["--stop"])
assert result.returncode == 0 and launches[0][3].endswith("VirtualBootstrap --stop")
result, launches = run(dict(good, devices=[("phone1", "unauthorized")]))
assert result.returncode != 0 and not launches
result, launches = run(dict(good, devices=[("phone1", "device"), ("phone2", "device")]))
assert result.returncode != 0 and not launches
result, launches = run(dict(good, devices=[("phone1", "device"), ("phone2", "device")]), ["--serial", "phone2"])
assert result.returncode == 0 and launches[0][1] == "phone2"
result, launches = run(dict(good, path=""))
assert result.returncode != 0 and not launches
result, launches = run(dict(good, path="package:/data/app/x'$(id)'/base.apk"))
assert result.returncode != 0 and not launches
result, launches = run(good, ["--serial", "missing"])
assert result.returncode != 0 and not launches
print("AdbStarterTest: 8 mock-ADB scenarios passed; actual ADB transport not tested")
