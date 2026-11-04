#!/usr/bin/env python3
"""Make a separate CIFA lab application from a restored historical source tree.

NEVER alters the original MarkerTap distribution or package. This is an
independently installable lab build only, not a production upgrade.
"""
import pathlib
import re
import sys

root=pathlib.Path(sys.argv[1]).resolve()
manifest=root/"AndroidManifest.xml"
assert manifest.exists() and (root/"src/jp/tapmarker/CifaRuntime.java").exists()
text=manifest.read_text()
assert 'package="jp.tapmarker.lite"' in text
assert 'android:versionCode="126"' in text
text=text.replace('package="jp.tapmarker.lite"','package="jp.tapmarker.cifa.getfix1615"',1)
text=text.replace('android:versionCode="126"','android:versionCode="7"',1)
text=text.replace('android:versionName="1.123"','android:versionName="1.161.5-cifa-video-calibrated"',1)
manifest.write_text(text)

# Avoid conflicting authorities/intent packages with the installed MarkerTap.
extensions={".java",".xml",".json",".properties",".txt"}
for prefix in ("src","res","assets"):
    base=root/prefix
    if not base.exists():continue
    for file in base.rglob("*"):
        if file.is_file() and file.suffix in extensions and file.stat().st_size<2_000_000:
            try:body=file.read_text()
            except UnicodeError:continue
            if "jp.tapmarker.lite" in body:
                file.write_text(body.replace("jp.tapmarker.lite","jp.tapmarker.cifa.getfix1615"))
manifest.write_text(manifest.read_text().replace("jp.tapmarker.lite","jp.tapmarker.cifa.getfix1615"))

# Distinct launcher name must be obvious even if the original app is installed.
translations=root/"tools/generate-locales.py"
src=translations.read_text()
old="'app_name':('マーカー連打 軽量版','MarkerTap Lite'),"
new="'app_name':('CIFA 動画座標修正 検証版','CIFA Video Calibrated Lab'),"
assert src.count(old)==1
translations.write_text(src.replace(old,new))
print("PASS: CIFA lab package is isolated; test versionCode 7 and unique authorities")
