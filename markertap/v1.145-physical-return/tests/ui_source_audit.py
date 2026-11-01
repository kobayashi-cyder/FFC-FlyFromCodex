#!/usr/bin/env python3
"""Fail fast on missing production UI hooks and accidental stop coupling."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
ui=(root/'src/jp/tapmarker/PanelModeController.java').read_text()
policy=(root/'src/jp/tapmarker/PanelModePolicy.java').read_text()
patch=(root/'tools/PatchPhysicalReturn.java').read_text()
build=(root/'build.py').read_text()
checks={
 'persist display choice':'putInt(KEY,PanelModePolicy.normalize(next))' in ui,
 'service initial mode':'initializePanel(TapService s)' in ui and 'sync(s);' in ui,
 'restore action':'showPanel(TapService s)' in ui,
 'hide action':'hidePanel(TapService s)' in ui,
 'lock safe visibility':'showOverlay(mode(s),s.isScreenGuarded())' in ui,
 'normal activity control':'augmentHome(MainActivity a)' in ui,
 'per-game input diagnosis':'"\\n入力："+s.inputHold' in ui,
 'original panel UI retained':'showPanelBeforePanelMode()' in ui,
 'refresh mode kept':'refreshBeforePanelMode()' in ui,
 'bound UI build':'panelPatch(ClassDef c)' in patch,
 'original service hook':'"onServiceConnected"' in patch,
 'original UI bootstrap hook':'"homeBeforePanelMode"' in patch,
 'shipping panel class allowed':'ROOT+"PanelModeController"' in patch,
 'APK version 1.146':"replacement='1.146'.encode(enc)" in build,
 'APK code 149':"struct.pack_into('<I',data,at+16,149)" in build,
 'both modes':'FLOAT=0, APP=1' in policy,
 '48dp touch':'dp(c,48)' in ui,
 'explicit non-color status':'modeLabel(mode(a))' in ui,
}
hide=re.search(r'static void hidePanel\(TapService s\)\s*\{([^{}]*)\}',ui,re.S)
checks['hiding never stops automation']=bool(hide) and not re.search(r'\b(stop|stopAll|stopMarkers|cancel)\s*\(',hide.group(1))
for label,ok in checks.items():
 if not ok:raise AssertionError(label)
print("UI source audit: "+str(len(checks))+" checks passed")
