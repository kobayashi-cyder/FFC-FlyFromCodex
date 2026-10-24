#!/usr/bin/env python3
"""Use the checksum-pinned SDK/DEX builder and audit the actual return wrapper."""
from pathlib import Path
import argparse

args = argparse.ArgumentParser(add_help=False)
args.add_argument("--output")
known, _ = args.parse_known_args()

here = Path(__file__).resolve().parent
out = Path(known.output).resolve() if known.output else here.parents[1] / "recovery-progress-build-results"
# A failed retry must not retain the previous run's success receipt, or vice versa.
for name in ("validation.json", "failure.json", "SHA256SUMS.txt", "MarkerTap-v1.140-signed.apk", "MarkerTap-v1.140-unsigned.apk"):
    (out / name).unlink(missing_ok=True)
source = (here.parent / "v1.134-autonomy/build_ci.py").read_text(encoding="utf-8")
replacements = {
    "1.134": "1.140",
    "versionCode='137'": "versionCode='143'",
    "'version_code':137": "'version_code':143",
    "'autonomy-build-results'": "'recovery-progress-build-results'",
    "'new_regression_checks':38": "'new_regression_checks':'38 baseline plus inherited frame/ad/physical tests and 25 return-progress checks'",
    "'tools/AutonomyDeadlineAudit.java']": "'tools/AutonomyDeadlineAudit.java','tools/ReturnProgressAudit.java']",
    "('BiologicalRemovalAudit',[])]": "('BiologicalRemovalAudit',[]),('ReturnProgressAudit',[])]",
}
for before, after in replacements.items():
    if before not in source:
        raise RuntimeError("Unexpected baseline builder: " + before)
    source = source.replace(before, after)
try:
    exec(compile(source, str(here / "generated-builder.py"), "exec"),
         {"__file__": str(here / "build_ci.py"), "__name__": "__main__"})
except BaseException:
    for name in ("restore", "build", "tests", "host-tests", "audit-compile", "ReturnProgressAudit"):
        log = here.parents[1] / "recovery-progress-build-results" / (name + ".log")
        if log.is_file():
            print(name + ":\n" + log.read_text(errors="replace")[-14000:], flush=True)
    raise
