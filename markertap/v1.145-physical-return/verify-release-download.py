#!/usr/bin/env python3
"""Fail-closed v1.151 release audit. Never signs, uploads or edits README.

Two stages: --stage preflight validates a locally signed APK plus real-device
logs; --stage published also downloads the release asset and checks its bytes.
The JSON/log evidence is operator-supplied and is not cryptographic proof that
an Android device actually executed a test. Independent review is required.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

REPO = 'kobayashi-cyder/FFC-FlyFromCodex'
TAG = 'markertap-v1.151'
ASSET = 'MarkerTap-v1.151-integrated-signed.apk'
PACKAGE = 'jp.tapmarker.lite'
VERSION = '1.151'
CODE = 154
CERT = '7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
UNSIGNED = '54cfccffa8bd6b5149fb2da563088b1cd3dde85037f567a33182308619d8befe'
REQUIRED_CASES = frozenset((
    'one_screen_expanded', 'one_screen_collapsed_restored',
    'two_screens_expanded', 'two_screens_one_collapsed_restored',
    'network_remains_online', 'advertisement_recovery',
    'reward_collection_continuous', 'memory_and_hardware_buffers',
    'frame_freshness_and_generation', 'input_display_isolation',
    'no_redundant_taps',
))

def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def read_json(path):
    with Path(path).open(encoding='utf-8') as f:
        value = json.load(f)
    if not isinstance(value, dict):
        raise ValueError('expected JSON object: ' + str(path))
    return value

def require(condition, reason):
    if not condition:
        raise ValueError('STOP: ' + reason)

def validate_receipt(signed, receipt):
    receipt = read_json(receipt)
    digest = sha256(signed)
    require(receipt.get('status') == 'signed_host_verified_not_device_tested', 'unexpected signing receipt status')
    for key, expected in (
        ('package', PACKAGE), ('version_name', VERSION),
        ('version_code', CODE), ('certificate_sha256', CERT),
        ('unsigned_apk_sha256', UNSIGNED), ('signed_apk_sha256', digest),
        ('device_tested', False),
    ):
        require(receipt.get(key) == expected, 'signing receipt mismatch: ' + key)
    require(receipt.get('certificate_matches_existing_install') is True,
            'original signing certificate not verified')
    return digest

def validate_device_evidence(path, digest):
    evidence = read_json(path)
    require(evidence.get('signed_apk_sha256') == digest, 'device evidence APK digest differs')
    require(evidence.get('package') == PACKAGE and evidence.get('version_code') == CODE,
            'device evidence package/version mismatch')
    require(evidence.get('android_api') == 37, 'Android 17/API37 evidence required')
    require(isinstance(evidence.get('device_fingerprint'), str)
            and len(evidence['device_fingerprint']) >= 16,
            'device build fingerprint missing')
    require(isinstance(evidence.get('device_model'), str) and evidence['device_model'].strip(),
            'device model missing')
    if evidence['device_model'] != 'Pixel 10':
        require(isinstance(evidence.get('equivalence_justification'), str)
                and len(evidence['equivalence_justification'].strip()) >= 30,
                'non-Pixel-10 device requires explicit equivalence rationale')
    cases = evidence.get('cases')
    require(isinstance(cases, list), 'device test cases missing')
    seen = set()
    root = Path(path).resolve().parent
    for case in cases:
        require(isinstance(case, dict), 'malformed device test case')
        name = case.get('name')
        require(isinstance(name, str) and name in REQUIRED_CASES and name not in seen,
                'unknown or duplicate device test case: ' + str(name))
        seen.add(name)
        require(case.get('status') == 'pass', 'device test not passed: ' + name)
        filename = case.get('evidence_file')
        require(isinstance(filename, str) and filename and not Path(filename).is_absolute(),
                'device evidence must use relative log paths: ' + name)
        logfile = (root / filename).resolve()
        require(logfile.is_relative_to(root) and logfile.is_file() and logfile.stat().st_size > 0,
                'device evidence missing/outside directory: ' + name)
        require(case.get('evidence_sha256') == sha256(logfile),
                'device evidence hash mismatch: ' + name)
    require(seen == REQUIRED_CASES, 'missing device cases: ' + ', '.join(sorted(REQUIRED_CASES - seen)))
    return len(seen)

def run(*command):
    return subprocess.run([str(c) for c in command], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True).stdout

def validate_apk(apk, sdk, expected_digest):
    require(Path(apk).is_file() and sha256(apk) == expected_digest,
            'signed APK bytes differ from signing receipt')
    bt = Path(sdk) / 'build-tools' / '35.0.0'
    for name in ('aapt2', 'apksigner', 'zipalign'):
        require((bt / name).is_file(), 'missing Android build tool: ' + name)
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, 'APK ZIP CRC invalid')
    badging = run(bt / 'aapt2', 'dump', 'badging', apk)
    for item in ("name='" + PACKAGE + "'", "versionCode='" + str(CODE) + "'",
                 "versionName='" + VERSION + "'"):
        require(item in badging, 'APK identity mismatch: ' + item)
    signature = run(bt / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    for item in ('Signer #1 certificate SHA-256 digest: ' + CERT,
                 'Verified using v2 scheme (APK Signature Scheme v2): true',
                 'Verified using v3 scheme (APK Signature Scheme v3): true'):
        require(item in signature, 'APK signature check failed: ' + item)
    run(bt / 'zipalign', '-c', '-P', '16', '4', apk)

def verify(signed, receipt, device_evidence, sdk, stage):
    digest = validate_receipt(signed, receipt)
    count = validate_device_evidence(device_evidence, digest)
    validate_apk(signed, sdk, digest)
    if stage == 'published':
        with tempfile.TemporaryDirectory(prefix='markertap-release-audit-') as tmp:
            run('gh', 'release', 'download', TAG, '--repo', REPO,
                '--pattern', ASSET, '--dir', tmp)
            downloaded = Path(tmp) / ASSET
            require(downloaded.is_file(), 'GitHub Release asset absent')
            validate_apk(downloaded, sdk, digest)
    return {'status': 'host_and_evidence_gate_passed', 'stage': stage,
            'apk_sha256': digest, 'version_code': CODE, 'certificate_sha256': CERT,
            'device_evidence_cases': count,
            'caveat': 'Device logs are operator-supplied and require independent review.'}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--signed', required=True, type=Path)
    p.add_argument('--receipt', required=True, type=Path)
    p.add_argument('--device-evidence', required=True, type=Path)
    p.add_argument('--sdk', required=True, type=Path)
    p.add_argument('--stage', choices=('preflight', 'published'), default='preflight')
    a = p.parse_args()
    print(json.dumps(verify(a.signed, a.receipt, a.device_evidence, a.sdk, a.stage),
                     ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
