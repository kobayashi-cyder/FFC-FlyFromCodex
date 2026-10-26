#!/usr/bin/env python3
"""Run the inherited build, regressions and audits, then inspect the new call sites."""
from pathlib import Path
import argparse

args = argparse.ArgumentParser(add_help=False)
args.add_argument("--output")
known, _ = args.parse_known_args()
here = Path(__file__).resolve().parent
out = Path(known.output).resolve() if known.output else here.parents[1] / "reward-window-build-results"
for name in ("validation.json", "failure.json", "SHA256SUMS.txt", "MarkerTap-v1.141-signed.apk", "MarkerTap-v1.141-unsigned.apk"):
    (out / name).unlink(missing_ok=True)
source = (here.parent / "v1.134-autonomy/build_ci.py").read_text(encoding="utf-8")
replacements = {
    "1.134": "1.141",
    "versionCode='137'": "versionCode='144'",
    "'version_code':137": "'version_code':144",
    "'autonomy-build-results'": "'reward-window-build-results'",
    "'new_regression_checks':38": "'new_regression_checks':'inherited core/host/replay tests, 25 return checks, visible-window/capture-clock/reward regressions'",
    "'tools/AutonomyDeadlineAudit.java']": "'tools/AutonomyDeadlineAudit.java','tools/ReturnProgressAudit.java','tools/RewardWindowAudit.java']",
    "('BiologicalRemovalAudit',[])]": "('BiologicalRemovalAudit',[]),('ReturnProgressAudit',[]),('RewardWindowAudit',[])]",
}
for before, after in replacements.items():
    if before not in source:
        raise RuntimeError("Unexpected baseline builder: " + before)
    source = source.replace(before, after)
try:
    exec(compile(source, str(here / "generated-builder.py"), "exec"),
         {"__file__": str(here / "build_ci.py"), "__name__": "__main__"})
except BaseException:
    for name in ("restore", "build", "tests", "host-tests", "audit-compile", "ReturnProgressAudit", "RewardWindowAudit"):
        log = out / (name + ".log")
        if log.is_file():
            print(name + ":\n" + log.read_text(errors="replace")[-14000:], flush=True)
    raise
