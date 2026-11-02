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

# Video-derived decisions are pure Java; compile the integration shim against test-only named-control stubs.
video=root/'build/video-tests'
video.mkdir(parents=True,exist_ok=True)
video_sources=[
 root/'src/jp/tapmarker/KaitakuVideoModalPolicy.java',
 root/'src/jp/tapmarker/KaitakuActionPacer.java',
 root/'src/jp/tapmarker/KaitakuVideoNamedControls.java',
 root/'tests/KaitakuVideoModalPolicyTest.java',
 root/'tests/KaitakuActionPacerTest.java',
 root/'tests/video/VideoActionHostStubs.java',
 root/'tests/video/KaitakuVideoNamedControlsTest.java',
]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(video)]+[str(p) for p in video_sources],check=True)
for cls in ['KaitakuVideoModalPolicyTest','KaitakuActionPacerTest','KaitakuVideoNamedControlsTest']:
 subprocess.run(['java','-cp',str(video),'jp.tapmarker.'+cls],check=True)
subprocess.run(['python3',str(root/'tests/video_source_audit.py')],check=True)
# Virtual fast scan is JVM-pure and exercises both 1-screen and 2-screen policies.
virtual=root/'build/virtual-tests'
virtual.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(virtual),
 str(root/'tests/virtual/PerformancePolicy.java'),
 str(root/'src/jp/tapmarker/VirtualFastWait.java'),
 str(root/'tests/virtual/VirtualFastWaitTest.java')],check=True)
subprocess.run(['java','-cp',str(virtual),'jp.tapmarker.VirtualFastWaitTest'],check=True)

