#!/usr/bin/env python3
"""Build audited v1.137 from reconstructed baseline with actionable failure logs."""
from pathlib import Path
here=Path(__file__).resolve().parent
source=(here.parent/"v1.134-autonomy"/"build_ci.py").read_text(encoding="utf-8")
for old in ("1.134","versionCode='137'","'version_code':137","'new_regression_checks':38"):
 if old not in source:raise RuntimeError("Unexpected baseline builder lineage: "+old)
source=source.replace("1.134","1.137")
source=source.replace("versionCode='137'","versionCode='140'")
source=source.replace("'version_code':137","'version_code':140")
source=source.replace("'new_regression_checks':38","'new_regression_checks':'38 baseline + 3 hardening + frame guard + connectome regression'")
source=source.replace("'autonomy-build-results'","'connectome-build-results'")
# Do not hide the root error inside a CI artifact; restore/build logs are
# surfaced even if a prior source stage failed.
try:
 exec(compile(source,str(here/"build_ci-v1137-generated.py"),"exec"),{"__file__":str(here/"build_ci.py"),"__name__":"__main__"})
except BaseException:
 for name in ("restore","build","tests","host-tests","audit-compile"):
  p=(here.parents[1]/"connectome-build-results"/(name+".log"))
  if p.is_file():print("=== "+name+".log (tail) ===\n"+p.read_text(errors="replace")[-10000:],flush=True)
 raise
