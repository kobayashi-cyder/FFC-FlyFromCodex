#!/usr/bin/env python3
"""Build v1.136 from audited v1.134 baseline, then restore through v1.135."""
from pathlib import Path
here=Path(__file__).resolve().parent
source=(here.parent/"v1.134-autonomy"/"build_ci.py").read_text(encoding="utf-8")
for old in ("1.134","versionCode='137'","'version_code':137","'new_regression_checks':38"):
 if old not in source:raise RuntimeError("Unexpected baseline builder lineage: "+old)
source=source.replace("1.134","1.136")
source=source.replace("versionCode='137'","versionCode='139'")
source=source.replace("'version_code':137","'version_code':139")
source=source.replace("'new_regression_checks':38","'new_regression_checks':'38 baseline + 3 hardening + 1 frame freshness gate'")
source=source.replace("'autonomy-build-results'","'frameguard-build-results'")
exec(compile(source,str(here/"build_ci-v1136-generated.py"),"exec"),{"__file__":str(here/"build_ci.py"),"__name__":"__main__"})
