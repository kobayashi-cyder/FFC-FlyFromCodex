#!/usr/bin/env python3
"""Fail fast on missing dock/float hooks or on presentation coupled to automation."""
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
 'float restore action':'showPanel(TapService s)' in ui,
 'fixed docking action':'dockPanel(TapService s)' in ui,
 'docked overlay visible':'static boolean showOverlay(int stored,boolean guarded){return !guarded;}' in policy,
 'fixed position geometry':'PanelModePolicy.dockY(dm.heightPixels,s.panel.getHeight()' in ui,
 'full width fixed panel':'width=dm.widthPixels;' in ui,
 'position lock':'if(!PanelModePolicy.movable(mode(s)))return false;' in ui,
 'draggable floating listener':'s.status.setOnTouchListener' in ui,
 'floating position restored':'getInt("x"' in ui and 'getInt("y"' in ui,
 'fixed no-floating decoration':'current==PanelModePolicy.DOCKED?0:16' in ui and 'setElevation' in ui,
 'orientation adaptation':'addOnLayoutChangeListener' in ui,
 'normal activity controls':'augmentHome(MainActivity a)' in ui and '"画面下部に固定"' in ui and '"フロートに戻す"' in ui,
 'per-game input diagnosis':'"\\n入力："+s.inputHold' in ui,
 'original panel UI retained':'showPanelBeforePanelMode()' in ui,
 'refresh mode kept':'refreshBeforePanelMode()' in ui,
 'bound UI build':'panelPatch(ClassDef c)' in patch,
 'original service hook':'"onServiceConnected"' in patch,
 'original UI bootstrap hook':'"homeBeforePanelMode"' in patch,
 'shipping panel class allowed':'ROOT+"PanelModeController"' in patch,
 'APK version 1.146':"replacement='1.146'.encode(enc)" in build,
 'APK code 149':"struct.pack_into('<I',data,at+16,149)" in build,
 'both modes':'FLOAT=0, DOCKED=1' in policy,
 '48dp touch':'dp(c,48)' in ui,
 'non-color mode label':'modeLabel(mode(a))' in ui,
 'screen lock guard':'s.isScreenGuarded()' in ui,
}
dock=re.search(r'static void dockPanel\(TapService s\)\s*\{([^{}]*)\}',ui,re.S)
checks['docking never stops automation']=bool(dock) and not re.search(r'\b(stop|stopAll|stopMarkers|cancel)\s*\(',dock.group(1))
checks['nonhide UI']=not any(token in ui for token in ['hidePanel(', 'フロートを隠す', '通常画面のみ（フロート非表示）'])
for label,ok in checks.items():
 if not ok:raise AssertionError(label)
print("UI source audit: "+str(len(checks))+" checks passed")
