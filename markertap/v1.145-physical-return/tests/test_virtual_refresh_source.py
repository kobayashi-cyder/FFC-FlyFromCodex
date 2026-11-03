#!/usr/bin/env python3
"""Host regression plus source-wiring audit for v1.152 autonomous virtual refresh."""
from pathlib import Path
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[3]
code = repo / "markertap/code/TapMarker"
source = code / "src/jp/tapmarker/VirtualRefreshPolicy.java"
test = code / "tests/VirtualRefreshPolicyTest.java"
windows = (code / "src/jp/tapmarker/VirtualWindows.java").read_text(encoding="utf-8")
required = {
    "per-window decision state": "final VirtualRefreshPolicy refreshPolicy=new VirtualRefreshPolicy()",
    "removed display isolation": "onSingleDisplayRemoved(w,id)",
    "one-window refresh handler": "performWindowRefresh(w,refresh)",
    "network classification": "networkValidated()",
    "DNS classification": "hasFreshDnsFailure(w,scanAt)",
    "manual refresh UI": 'setNeutralButton("リフレッシュ"',
    "single-display replacement": 'case RECREATE_DISPLAY:',
    "release of already removed display": 'if(id!=oldId||!w.removedObserved)throw missing',
    "explicit-only budget reset": "resetAfterExplicitUserStart()",
}
for description,needle in required.items():
    if needle not in windows:
        raise AssertionError("source integration missing: "+description)

with tempfile.TemporaryDirectory() as out:
    subprocess.run(["javac","--release","8","-encoding","UTF-8","-d",out,str(source),str(test)],check=True)
    subprocess.run(["java","-cp",out,"jp.tapmarker.VirtualRefreshPolicyTest"],check=True)
print("PASS source integration markers and 26 policy assertions")
