#!/usr/bin/env python3
"""v1.139 inherit audited v1.138 build with updated shipping version / output."""
from pathlib import Path
here=Path(__file__).resolve().parent
source=(here.parent/"v1.138-ad-intent"/"build_ci.py").read_text(encoding="utf-8")
for anchor in ("1.138","versionCode='141'","'version_code':141","'ad-intent-build-results'"):
 if anchor not in source:raise RuntimeError("Unexpected v1.138 builder: "+anchor)
source=source.replace("1.138","1.139").replace("versionCode='141'","versionCode='142'").replace("'version_code':141","'version_code':142")
source=source.replace("'ad-intent-build-results'","'f52a-physical-build-results'")
try:
 exec(compile(source,str(here/"build_ci-v1139-generated.py"),"exec"),{"__file__":str(here/"build_ci.py"),"__name__":"__main__"})
except BaseException:
 for name in ("restore","build","tests","host-tests","audit-compile","ReleaseRuntimeAudit"):
  p=here.parents[1]/"f52a-physical-build-results"/(name+".log")
  if p.is_file():print("=== "+name+".log (tail) ===\n"+p.read_text(errors="replace")[-13000:],flush=True)
 raise
