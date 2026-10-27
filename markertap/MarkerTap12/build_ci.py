#!/usr/bin/env python3
"""Build the independent MarkerTap12 profile using the repository's audited host pipeline."""
from pathlib import Path

here = Path(__file__).resolve().parent
source_path = here.parent / "v1.134-autonomy" / "build_ci.py"
source = source_path.read_text(encoding="utf-8")
replacements = [
    ("1.134", "12.0.0"),
    ("versionCode='137'", "versionCode='1200'"),
    ("'version_code':137", "'version_code':1200"),
    ("'autonomy-build-results'", "'MarkerTap12-build-results'"),
    ("str(HERE/'restore_project.py')", "str(ROOT/'MarkerTap12'/'restore_project.py')"),
    ("name='jp.tapmarker.lite'", "name='jp.tapmarker12.lite'"),
    ("'MarkerTap-v1.134-integrated.apk'", "'MarkerTap12-v12.0.0-integrated.apk'"),
    ("'MarkerTap-v1.134-unsigned.apk'", "'MarkerTap12-v12.0.0-unsigned.apk'"),
    ("'MarkerTap-v1.134-signed.apk'", "'MarkerTap12-v12.0.0-signed.apk'"),
    ("'package':'jp.tapmarker.lite'", "'package':'jp.tapmarker12.lite'"),
]
for old, new in replacements:
    if old not in source:
        raise RuntimeError("MarkerTap12 builder anchor changed: " + old)
    source = source.replace(old, new)
exec(compile(source, str(here / "generated-builder.py"), "exec"),
     {"__file__": str(source_path), "__name__": "__main__"})
