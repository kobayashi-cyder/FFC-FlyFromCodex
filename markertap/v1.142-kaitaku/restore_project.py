#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
if len(sys.argv)!=2:raise SystemExit('Usage: restore_project.py DESTINATION')
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/'v1.141-reward-window/restore_project.py'),str(project)],check=True)
from apply import apply
apply(project)
