#!/usr/bin/env python3
"""Run host regressions without requiring the Android SDK or a signed baseline APK."""
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parent
out=root/'build/host-tests'
out.mkdir(parents=True,exist_ok=True)
sources=list((root/'tests/host').rglob('*.java'))+list((root/'tests/reference').rglob('*.java'))+[
 root/'tests/PhysicalRecoveryTest.java',
 root/'tests/PanelModePolicyTest.java',
 root/'src/jp/tapmarker/PhysicalRecoveryBridge.java',
 root/'src/jp/tapmarker/PhysicalReturnEpisode.java',
 root/'src/jp/tapmarker/PanelModePolicy.java',
]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(out)]+[str(p) for p in sources],check=True)
for name in ['PhysicalRecoveryTest','PanelModePolicyTest']:
 subprocess.run(['java','-cp',str(out),'jp.tapmarker.'+name],check=True)
subprocess.run(['python3',str(root/'tests/ui_source_audit.py')],check=True)
