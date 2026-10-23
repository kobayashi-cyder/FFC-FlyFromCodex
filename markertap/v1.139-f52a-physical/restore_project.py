#!/usr/bin/env python3
"""Restore v1.138 shipping runtime then apply and audit physical F-52A flow."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
if len(sys.argv)!=2:raise SystemExit("Usage: restore_project.py DEST")
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.138-ad-intent"/"restore_project.py"),str(project)],check=True)
from apply import apply
apply(project)
subprocess.run([sys.executable,str(here/"source_audit.py"),str(project)],check=True)
