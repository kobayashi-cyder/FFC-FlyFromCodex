#!/usr/bin/env python3
"""Rebuild from the archived runtime, execute regressions and audit actual DEX hooks."""
from pathlib import Path
import argparse
parser=argparse.ArgumentParser(add_help=False);parser.add_argument('--output');known,_=parser.parse_known_args()
here=Path(__file__).resolve().parent
out=Path(known.output).resolve() if known.output else here.parents[1]/'kaitaku-build-results'
source=(here.parent/'v1.134-autonomy/build_ci.py').read_text()
replacements={
 '1.134':'1.142',"versionCode='137'":"versionCode='145'","'version_code':137":"'version_code':145",
 "'autonomy-build-results'":"'kaitaku-build-results'",
 "'new_regression_checks':38":"'new_regression_checks':'44 Kaitaku control/state/coordinate checks; inherited core/host and previous recovery regressions'",
 "'tools/AutonomyDeadlineAudit.java']":"'tools/AutonomyDeadlineAudit.java','tools/ReturnProgressAudit.java','tools/RewardWindowAudit.java','tools/KaitakuAudit.java']",
 "('BiologicalRemovalAudit',[])]":"('BiologicalRemovalAudit',[]),('ReturnProgressAudit',[]),('RewardWindowAudit',[]),('KaitakuAudit',[])]",
}
for before,after in replacements.items():
 if before not in source:raise RuntimeError('Builder anchor changed: '+before)
 source=source.replace(before,after)
try:exec(compile(source,str(here/'generated-builder.py'),'exec'),{'__file__':str(here/'build_ci.py'),'__name__':'__main__'})
except BaseException:
 for name in ['restore','build','tests','host-tests','audit-compile','KaitakuAudit']:
  p=out/(name+'.log')
  if p.exists():print(name+':\n'+p.read_text(errors='replace')[-10000:],flush=True)
 raise
