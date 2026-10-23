#!/usr/bin/env python3
"""Reuse the pinned 1.134 SDK/DEX audits with version-specific 1.135 gates."""
from pathlib import Path
here=Path(__file__).resolve().parent
source=(here.parent/"v1.134-autonomy"/"build_ci.py").read_text(encoding="utf-8")
for old in ["1.134","versionCode='137'","'version_code':137","'new_regression_checks':38"]:
    if old not in source:raise RuntimeError("Unexpected 1.134 builder lineage: "+old)
source=source.replace("1.134","1.135")
source=source.replace("versionCode='137'","versionCode='138'")
source=source.replace("'version_code':137","'version_code':138")
source=source.replace("'new_regression_checks':38","'new_regression_checks':'38 baseline plus 3 new suites'")
source=source.replace("'autonomy-build-results'","'hardening-build-results'")
try:
    exec(compile(source,str(here/"build_ci-v1135-generated.py"),"exec"),{"__file__":str(here/"build_ci.py"),"__name__":"__main__"})
except Exception:
    # The temporary restoration directory is removed on failure. Surface the
    # actionable failure in the Actions log, not only in an artifact ZIP.
    for log in sorted((here.parents[1]/"hardening-build-results").glob("*.log")):
        lines=log.read_text(errors="replace").splitlines()
        print(f"--- {log.name}: last 70 lines ---", flush=True)
        print("\\n".join(lines[-70:]), flush=True)
    raise
