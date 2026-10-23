#!/usr/bin/env python3
"""Compile v1.138 shipping sources via the inherited audited Android builder."""
from pathlib import Path
here=Path(__file__).resolve().parent
src=(here.parent/"v1.137-connectome"/"build_ci.py").read_text(encoding="utf-8")
for word in ("1.137","versionCode='140'","'version_code':140","'connectome-build-results'"):
 if word not in src:raise RuntimeError("Unexpected baseline builder: "+word)
src=src.replace("1.137","1.138").replace("versionCode='140'","versionCode='141'").replace("'version_code':140","'version_code':141").replace("'connectome-build-results'","'ad-intent-build-results'")
src=src.replace("'38 baseline + 3 hardening + frame guard + connectome regression'","'38 baseline + 3 hardening + frame guard + connectome + ad-intent regression'")
exec(compile(src,str(here/"build_ci-v1138-generated.py"),"exec"),{"__file__":str(here/"build_ci.py"),"__name__":"__main__"})
