#!/usr/bin/env python3
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parent
out=root/'build/host-tests';out.mkdir(parents=True,exist_ok=True)
sources=list((root/'tests').rglob('*.java'))+list((root/'src').rglob('*.java'))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(out)]+[str(p) for p in sources],check=True)
subprocess.run(['java','-cp',str(out),'jp.tapmarker.PhysicalRecoveryTest'],check=True)
