#!/usr/bin/env python3
"""Build the exact v1.130 source on GitHub; receive only encrypted signing data."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(os.environ['GITHUB_WORKSPACE'])
RESULTS = ROOT / 'crash-build-results'
RESULTS.mkdir(exist_ok=True)
sys.path.insert(0, str(ROOT / 'markertap/tools'))
import import_source as channel
spec = importlib.util.spec_from_file_location('existing_signing_transport', ROOT / 'markertap/tools/build_source.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)
# The reused transport module initializes its legacy channel during import.
# Select this build's channel after that import so progress and signing agree.
channel.CHANNEL = 'markertap/crash-v1130-channel/'
EXPECTED_CERT = '7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
stage = 'source_verification'

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def logged(args, cwd, env, name):
    with (RESULTS / name).open('w') as output:
        subprocess.run(args, cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT, check=True)

def source(private):
    directory = ROOT / 'markertap/v1.130-crashfix'
    manifest = json.loads((directory / 'source-manifest.json').read_text())
    archive = private / 'source.zip'
    with archive.open('wb') as output:
        for item in manifest['parts']:
            if PurePosixPath(item['name']).name != item['name']:
                raise RuntimeError('invalid source part')
            path = directory / item['name']
            if path.stat().st_size != item['size_bytes'] or digest(path) != item['sha256']:
                raise RuntimeError('source part checksum mismatch')
            output.write(path.read_bytes())
    if digest(archive) != manifest['sha256']:
        raise RuntimeError('source archive checksum mismatch')
    project = private / 'project'
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            path = PurePosixPath(item.filename)
            if path.is_absolute() or '..' in path.parts or stat.S_ISLNK(item.external_attr >> 16):
                raise RuntimeError('unsafe archive entry')
            if path.suffix.lower() in {'.jks', '.keystore', '.pem', '.p12', '.pfx', '.key'}:
                raise RuntimeError('private material found in source archive')
        if z.testzip() is not None:
            raise RuntimeError('source ZIP checksum failure')
        z.extractall(project)
    return project, manifest

def main():
    global stage
    with tempfile.TemporaryDirectory(prefix='markertap-v1130-private-', dir=os.environ['RUNNER_TEMP']) as temporary:
        private = Path(temporary)
        private.chmod(0o700)
        project, manifest = source(private)
        stage = 'toolchain'
        jadx_zip = private / 'jadx.zip'
        urllib.request.urlretrieve('https://github.com/skylot/jadx/releases/download/v1.5.3/jadx-1.5.3.zip', jadx_zip)
        if digest(jadx_zip) != '8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec':
            raise RuntimeError('JADX checksum mismatch')
        with zipfile.ZipFile(jadx_zip) as z:
            z.extractall(private / 'jadx')
        jar = private / 'jadx/lib/jadx-1.5.3-all.jar'
        if not jar.is_file():
            raise RuntimeError('pinned JADX JAR missing')
        env = os.environ.copy()
        env.pop('TAPMARKER_KEYSTORE', None)
        env['ANDROID_SDK_ROOT'] = os.environ['ANDROID_HOME']
        env['TAPMARKER_JADX_JAR'] = str(jar)
        env['TAPMARKER_REPLAY'] = '1'
        stage = 'build'
        logged(['bash', 'build.sh'], project, env, 'build.log')
        stage = 'core_tests'
        logged(['bash', 'run-tests.sh'], project, env, 'tests.log')
        stage = 'anr_regression_tests'
        logged(['bash', 'run-host-tests.sh'], project, env, 'anr-regression.log')
        stage = 'private_signing_reference'
        channel.publish('progress.json', {'run_id': channel.RUN_ID, 'stage': stage, 'build_passed': True, 'tests_passed': True})
        keystore = transport.receive_key(private)
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
        unsigned = project / 'output/MarkerTap-v1.130-unsigned.apk'
        apk = RESULTS / 'MarkerTap-v1.130-crashfix.apk'
        stage = 'signing'
        logged([str(tools / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', 'tapmarker',
                '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', str(apk), str(unsigned)],
               project, env, 'signing.log')
        verification = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
        if 'Signer #1 certificate SHA-256 digest: ' + EXPECTED_CERT not in verification:
            raise RuntimeError('signing certificate differs from the installed application')
        (RESULTS / 'signature.txt').write_text(verification)
        badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
        if not all(value in badging for value in ["name='jp.tapmarker.lite'", "versionCode='133'", "versionName='1.130'"]):
            raise RuntimeError('unexpected APK identity')
        (RESULTS / 'badging.txt').write_text(badging)
        logged([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)], project, env, 'alignment.log')
        logged(['java', '-cp', 'build/helpers:' + str(jar), 'BiologicalRemovalAudit', str(apk)], project, env, 'biological-removal.log')
        for name in ['dex-merge.log', 'dex-audit.log', 'integration-audit.log']:
            shutil.copyfile(project / 'build' / name, RESULTS / name)
        with zipfile.ZipFile(apk) as z:
            if z.testzip() is not None:
                raise RuntimeError('APK ZIP checksum failure')
        validation = {
            'status': 'built_signed_and_host_verified', 'version_name': '1.130', 'version_code': 133,
            'package': 'jp.tapmarker.lite', 'apk_sha256': digest(apk), 'apk_size_bytes': apk.stat().st_size,
            'signing_certificate_sha256': EXPECTED_CERT, 'zipalign': '16KiB passed',
            'source_archive_sha256': manifest['sha256'], 'original_source_sha256': manifest['original_source_sha256'],
            'source_commit': os.environ['GITHUB_SHA'], 'run_id': channel.RUN_ID,
            'core_tests': 'passed', 'anr_host_regression': 'passed', 'integration_audit': 'passed',
            'device_tested': False, 'device_anr_resolved': None,
            'changes': ['native image reads outside shared UI frame lock',
                        'temporary bitmap disposal uses independent retirement worker',
                        'owner-death backend hard-stop independent of native cleanup']}
        (RESULTS / 'validation.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n')
        (RESULTS / 'SHA256SUMS.txt').write_text(digest(apk) + '  ' + apk.name + '\n')
        stage = 'publish'
        notes = RESULTS / 'RELEASE_NOTES.md'
        notes.write_text('MarkerTap v1.130 crash-fix candidate.\n\n'
                         'Built from the v1.130 source with shared frame-lock isolation, deferred temporary bitmap disposal, '
                         'and bounded owner-death backend cleanup. Existing signing certificate, package/version, '
                         'DEX integration, host regression tests and 16KiB alignment verified.\n\n'
                         'Pixel 10 / Android 17 long-run ANR resolution and game rewards require device validation.\n')
        tag = 'markertap-v1.130-crashfix'
        subprocess.run(['gh', 'release', 'create', tag, str(apk), str(RESULTS / 'SHA256SUMS.txt'),
                        str(RESULTS / 'validation.json'), '--repo', channel.REPO, '--target', os.environ['GITHUB_SHA'],
                        '--title', 'MarkerTap v1.130 crash-fix candidate', '--notes-file', str(notes),
                        '--prerelease', '--latest=false'], check=True)
        channel.publish('result.json', {**validation, 'apk_published': True,
            'apk_url': 'https://github.com/' + channel.REPO + '/releases/download/' + tag + '/' + apk.name})
        channel.publish('inbox.json', {'run_id': channel.RUN_ID, 'status': 'consumed'})
        print('v1.130 APK built, signed, verified and published.')

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        failure = {'run_id': channel.RUN_ID, 'status': 'failed', 'stage': stage,
                   'error_type': type(error).__name__, 'apk_published': False}
        (RESULTS / 'failure.json').write_text(json.dumps(failure, indent=2) + '\n')
        try:
            channel.publish('result.json', failure)
        except Exception:
            pass
        # Do not publish private transport, key bytes or signing references.
        print('Build stopped at ' + stage + '; inspect the safe build logs.', file=sys.stderr)
        raise SystemExit(1)
