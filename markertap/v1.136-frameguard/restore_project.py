#!/usr/bin/env python3
"""Restore verified v1.135 sources then apply independent virtual-frame guard."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
if len(sys.argv)!=2:raise SystemExit("Usage: restore_project.py DESTINATION")
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.135-hardening"/"restore_project.py"),str(project)],check=True)
from apply import apply
apply(project)
subprocess.run([sys.executable,str(here/"source_audit.py"),str(project)],check=True)
