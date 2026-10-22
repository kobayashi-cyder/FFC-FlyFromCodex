"""Publish the already signed and tested build without rebuilding it."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(os.environ['GITHUB_WORKSPACE'])
output = root / 'stability-build-results'
apk = output / 'MarkerTap-v1.131.apk'
expected = '3fa6a51ce350411dcd833a75716bef3a7281a5ede5729a94e2dc8055742fa16b'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected, 'Unexpected artifact APK'
assert apk.stat().st_size == 5081883
checks = {
    'release-runtime-audit.log': ['UI 1.131 agrees with APK manifest', 'feedback interruption preserves session', 'manual stop and onDestroy shutdown retained'],
    'signed-dex-audit.log': ['38097 invoke register layouts checked'],
    'signature.txt': ['Signer #1 certificate SHA-256 digest: 7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'],
    'badging.txt': ["name='jp.tapmarker.lite'", "versionCode='134'", "versionName='1.131'"],
    'previous-invoke-regression.log': ['invoke register count 0 != 5', 'invoke register count 0 != 8'],
    'previous-interrupt-regression.log': ['fatal teardown in onInterrupt'],
    'tests.log': ['Incident replay: all 8 exact result digests match.', 'OwnerDeathCleanupTest: 9 checks passed'],
    'anr-regression.log': ['Host tests passed.']}
for name, tokens in checks.items():
    text = (output / name).read_text()
    assert all(token in text for token in tokens), 'Missing verification: ' + name
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
spec = importlib.util.spec_from_file_location('stability_build_controller', root / 'markertap/v1.131-stability/build_ci.py')
controller = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controller)
url = controller.public_mirror(apk)
validation = {
    'status': 'built_signed_host_verified_and_publicly_downloaded', 'version_name': '1.131', 'version_code': 134,
    'package': 'jp.tapmarker.lite', 'apk_sha256': expected, 'apk_size_bytes': apk.stat().st_size,
    'signing_certificate_sha256': controller.EXPECTED_CERT, 'build_run_id': '39726318247',
    'publication_run_id': os.environ['GITHUB_RUN_ID'], 'source_commit': 'b90c48802451ed15b121b84533284d0bd8080d6c',
    'source_overlays': json.loads((root / 'markertap/v1.131-stability/overlay-manifest.json').read_text()),
    'core_tests': 'passed', 'anr_host_regression': 'passed', 'invoke_layout_audit': '38097 calls passed',
    'previous_apk_rejected_for_both_malformed_invocations': True,
    'previous_interrupt_teardown_rejected': True, 'visible_version_matches_manifest': True,
    'manual_stop_and_service_destruction_retained': True, 'zipalign': '16KiB passed',
    'public_anonymous_download_verified': True, 'apk_url': url,
    'device_tested': False, 'device_accessibility_stability_confirmed': None}
if (output / 'failure.json').exists():
    (output / 'failure.json').rename(output / 'initial-publication-failure.json')
(output / 'validation.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n')
(output / 'SHA256SUMS.txt').write_text(expected + '  ' + apk.name + '\n')
notes = output / 'RELEASE_NOTES.md'
notes.write_text('MarkerTap v1.131 stability fix.\n\n'
    'Corrected two malformed DEX invoke-range calls. Accessibility feedback interruption now retains the active session; '
    'manual stop and actual service destruction retain their shutdown behavior. '
    'The in-app label and APK manifest both read 1.131 (code 134).\n\n'
    'Core, ANR host, signed DEX and original-signature verification passed. '
    'The previous APK is rejected by the new regression gates for both malformed calls and interrupt teardown. '
    'Device long-run stability remains unverified.\n\nAPK direct download: ' + url + '\n')
subprocess.run(['gh', 'release', 'create', 'markertap-v1.131-stability', str(apk), str(output / 'validation.json'),
    str(output / 'SHA256SUMS.txt'), '--repo', controller.channel.REPO, '--target', validation['source_commit'],
    '--title', 'MarkerTap v1.131 stability fix', '--notes-file', str(notes), '--prerelease', '--latest=false'], check=True, timeout=120)
controller.channel.publish('result.json', {**validation, 'apk_published': True})
controller.channel.publish('inbox.json', {'status': 'consumed', 'build_run_id': '39726318247'})
print('Verified public APK: ' + url)
