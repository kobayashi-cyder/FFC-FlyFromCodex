#!/usr/bin/env python3
"""Reproducible v1.131 stability build with APK-level regression gates."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

ROOT = Path(os.environ['GITHUB_WORKSPACE'])
RESULTS = ROOT / 'stability-build-results'
RESULTS.mkdir(exist_ok=True)
sys.path.insert(0, str(ROOT / 'markertap/tools'))
import import_source as channel
spec = importlib.util.spec_from_file_location('signing_transport', ROOT / 'markertap/tools/build_source.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)
channel.CHANNEL = 'markertap/stability-v1131-channel/'
EXPECTED_CERT = '7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
STAGE = 'source_verification'

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def stage(name):
    global STAGE
    STAGE = name
    print('Stage: ' + name, flush=True)
    channel.publish('progress.json', {'run_id': channel.RUN_ID, 'stage': name})

def download(url, target):
    with urllib.request.urlopen(url, timeout=60) as response, Path(target).open('wb') as output:
        shutil.copyfileobj(response, output)

def logged(args, project, env, name, timeout=400, check=True):
    with (RESULTS / name).open('w') as output:
        result = subprocess.run(args, cwd=project, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
    if check:
        result.check_returncode()
    return result

def restore_source(private):
    directory = ROOT / 'markertap/v1.130-crashfix'
    manifest = json.loads((directory / 'source-manifest.json').read_text())
    archive = private / 'source.zip'
    with archive.open('wb') as output:
        for item in manifest['parts']:
            if PurePosixPath(item['name']).name != item['name']:
                raise RuntimeError('invalid source part')
            part = directory / item['name']
            if part.stat().st_size != item['size_bytes'] or digest(part) != item['sha256']:
                raise RuntimeError('source part checksum mismatch')
            output.write(part.read_bytes())
    if digest(archive) != manifest['sha256']:
        raise RuntimeError('source archive checksum mismatch')
    project = private / 'project'
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            path = PurePosixPath(item.filename)
            if path.is_absolute() or '..' in path.parts or stat.S_ISLNK(item.external_attr >> 16):
                raise RuntimeError('unsafe archive entry')
            if path.suffix.lower() in {'.jks', '.keystore', '.pem', '.p12', '.pfx', '.key'}:
                raise RuntimeError('private material found in source')
        if z.testzip() is not None:
            raise RuntimeError('source ZIP checksum failure')
        z.extractall(project)
    overlays = json.loads((ROOT / 'markertap/v1.131-stability/overlay-manifest.json').read_text())
    for item in overlays:
        relative = PurePosixPath(item['path'])
        if relative.is_absolute() or '..' in relative.parts:
            raise RuntimeError('unsafe source overlay path')
        source = ROOT / 'markertap/v1.131-stability/overlay' / str(relative)
        if digest(source) != item['sha256']:
            raise RuntimeError('source overlay checksum mismatch')
        target = project / str(relative)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    return project, manifest, overlays

def public_mirror(apk):
    api = '/repos/' + channel.REPO
    blob = channel.api(api + '/git/blobs', {'encoding': 'base64', 'content': base64.b64encode(apk.read_bytes()).decode()})
    branch = 'apk-v1.131'
    try:
        ref = channel.api(api + '/git/ref/heads/' + branch)
        parent = ref['object']['sha']
        exists = True
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        parent = os.environ['GITHUB_SHA']
        exists = False
    url = 'https://raw.githubusercontent.com/' + channel.REPO + '/' + branch + '/' + apk.name
    readme = '# MarkerTap 1.131\n\nSigned APK: ' + url + '\n\nSHA-256: `' + digest(apk) + '`\n\n' \
        'Version 1.131 / code 134. Feedback interruption no longer tears down the automation session. ' \
        'Corrected two malformed invoke-range calls; visible version agrees with manifest. ' \
        'Build and host regression tests passed; device long-run stability remains unverified.\n'
    tree = channel.api(api + '/git/trees', {'tree': [
        {'path': apk.name, 'mode': '100644', 'type': 'blob', 'sha': blob['sha']},
        {'path': 'README.md', 'mode': '100644', 'type': 'blob', 'content': readme}]})
    commit = channel.api(api + '/git/commits', {'message': 'Publish verified MarkerTap v1.131 APK', 'tree': tree['sha'], 'parents': [parent]})
    if exists:
        channel.api(api + '/git/refs/heads/' + branch, {'sha': commit['sha'], 'force': False}, method='PATCH')
    else:
        channel.api(api + '/git/refs', {'ref': 'refs/heads/' + branch, 'sha': commit['sha']})
    with urllib.request.urlopen(url, timeout=60) as response:
        if response.status != 200 or hashlib.sha256(response.read()).hexdigest() != digest(apk):
            raise RuntimeError('anonymous public APK download verification failed')
    return url

def main():
    with tempfile.TemporaryDirectory(prefix='markertap-v1131-private-', dir=os.environ['RUNNER_TEMP']) as directory:
        private = Path(directory)
        private.chmod(0o700)
        stage('source_verification')
        project, manifest, overlays = restore_source(private)
        stage('toolchain')
        archive = private / 'jadx.zip'
        download('https://github.com/skylot/jadx/releases/download/v1.5.3/jadx-1.5.3.zip', archive)
        if digest(archive) != '8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec':
            raise RuntimeError('JADX checksum mismatch')
        with zipfile.ZipFile(archive) as z:
            z.extractall(private / 'jadx')
        jar = private / 'jadx/lib/jadx-1.5.3-all.jar'
        env = os.environ.copy()
        env.pop('TAPMARKER_KEYSTORE', None)
        env['ANDROID_SDK_ROOT'] = os.environ['ANDROID_HOME']
        env['TAPMARKER_JADX_JAR'] = str(jar)
        env['TAPMARKER_REPLAY'] = '1'
        stage('build_and_dex_verification')
        logged(['bash', 'build.sh'], project, env, 'build.log')
        logged(['javac', '-cp', str(jar), '-d', 'build/helpers', 'tools/ReleaseRuntimeAudit.java'], project, env, 'release-audit-compile.log')
        cp = 'build/helpers:' + str(jar)
        stage('regression_rejects_previous_faults')
        previous = private / 'previous-v1130.apk'
        download('https://github.com/' + channel.REPO + '/releases/download/markertap-v1.130-crashfix/MarkerTap-v1.130-crashfix.apk', previous)
        if digest(previous) != 'd37173e2e7097d5a301e15e854154552749e5c4b92104075a8eba2d32b70d7a2':
            raise RuntimeError('unexpected previous APK')
        old = logged(['java', '-cp', cp, 'DexAudit', str(previous)], project, env, 'previous-invoke-regression.log', check=False)
        evidence = (RESULTS / 'previous-invoke-regression.log').read_text()
        if old.returncode == 0 or not all(item in evidence for item in ['invoke register count', 'FrameLockBridge;->preview', 'FrameLockBridge;->copy']):
            raise RuntimeError('regression gate did not reject both previous malformed invocations')
        old = logged(['java', '-cp', cp, 'ReleaseRuntimeAudit', str(previous), '1.130'], project, env, 'previous-interrupt-regression.log', check=False)
        if old.returncode == 0 or 'fatal teardown in onInterrupt' not in (RESULTS / 'previous-interrupt-regression.log').read_text():
            raise RuntimeError('regression gate did not reject previous interrupt shutdown')
        stage('core_and_anr_tests')
        logged(['bash', 'run-tests.sh'], project, env, 'tests.log')
        logged(['bash', 'run-host-tests.sh'], project, env, 'anr-regression.log')
        unsigned = project / 'output/MarkerTap-v1.131-unsigned.apk'
        logged(['java', '-cp', cp, 'ReleaseRuntimeAudit', str(unsigned), '1.131'], project, env, 'unsigned-release-runtime.log')
        stage('private_signing_reference')
        keystore = transport.receive_key(private)
        stage('signing_and_final_apk_verification')
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
        apk = RESULTS / 'MarkerTap-v1.131.apk'
        logged([str(tools / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', 'tapmarker',
                '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', str(apk), str(unsigned)], project, env, 'signing.log')
        signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
        if 'Signer #1 certificate SHA-256 digest: ' + EXPECTED_CERT not in signature:
            raise RuntimeError('unexpected signing certificate')
        (RESULTS / 'signature.txt').write_text(signature)
        badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
        if not all(value in badging for value in ["name='jp.tapmarker.lite'", "versionCode='134'", "versionName='1.131'"]):
            raise RuntimeError('unexpected APK identity')
        (RESULTS / 'badging.txt').write_text(badging)
        version = re.search(r"versionName='([^']+)'", badging).group(1)
        logged(['java', '-cp', cp, 'ReleaseRuntimeAudit', str(apk), version], project, env, 'release-runtime-audit.log')
        logged(['java', '-cp', cp, 'DexAudit', str(apk)], project, env, 'signed-dex-audit.log')
        logged(['java', '-cp', cp, 'BiologicalRemovalAudit', str(apk)], project, env, 'biological-removal.log')
        logged([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)], project, env, 'alignment.log')
        for name in ['dex-merge.log', 'dex-audit.log', 'integration-audit.log']:
            shutil.copyfile(project / 'build' / name, RESULTS / name)
        stage('public_download_verification')
        url = public_mirror(apk)
        validation = {
            'status': 'built_signed_and_host_verified', 'version_name': '1.131', 'version_code': 134,
            'package': 'jp.tapmarker.lite', 'apk_sha256': digest(apk), 'apk_size_bytes': apk.stat().st_size,
            'signing_certificate_sha256': EXPECTED_CERT, 'source_archive_sha256': manifest['sha256'],
            'source_commit': os.environ['GITHUB_SHA'], 'source_overlays': overlays, 'run_id': channel.RUN_ID,
            'core_tests': 'passed', 'anr_host_regression': 'passed', 'invoke_layout_audit': 'passed',
            'previous_apk_rejected_for_both_malformed_invocations': True,
            'previous_interrupt_teardown_rejected': True, 'visible_version_matches_manifest': True,
            'manual_stop_and_service_destruction_retained': True,
            'public_anonymous_download_verified': True, 'apk_url': url,
            'device_tested': False, 'device_accessibility_stability_confirmed': None}
        (RESULTS / 'validation.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n')
        (RESULTS / 'SHA256SUMS.txt').write_text(digest(apk) + '  ' + apk.name + '\n')
        notes = RESULTS / 'RELEASE_NOTES.md'
        notes.write_text('MarkerTap v1.131 stability candidate.\n\n'
            'Corrects the preview/copy invoke-range argument layout that could reject VirtualWindows at runtime. '
            'Accessibility feedback interruption no longer tears down every active session. '
            'Manual stop and service destruction retain shutdown behavior. '
            'Displayed version and APK manifest both read 1.131 (code 134).\n\n'
            'The new regression gates reject the previously published APK for both malformed calls and interrupt teardown. '
            'Core, ANR host, signed DEX, original certificate and 16KiB alignment checks passed. '
            'Device long-run stability still requires confirmation.\n\nDirect APK: ' + url + '\n')
        stage('release_publication')
        subprocess.run(['gh', 'release', 'create', 'markertap-v1.131-stability', str(apk), str(RESULTS / 'validation.json'),
            str(RESULTS / 'SHA256SUMS.txt'), '--repo', channel.REPO, '--target', os.environ['GITHUB_SHA'],
            '--title', 'MarkerTap v1.131 stability fix', '--notes-file', str(notes), '--prerelease', '--latest=false'], check=True, timeout=120)
        channel.publish('result.json', {**validation, 'apk_published': True})
        channel.publish('inbox.json', {'run_id': channel.RUN_ID, 'status': 'consumed'})
        print('Verified public APK: ' + url, flush=True)

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        failure = {'run_id': channel.RUN_ID, 'status': 'failed', 'stage': STAGE,
                   'error_type': type(error).__name__, 'apk_published': False}
        (RESULTS / 'failure.json').write_text(json.dumps(failure, indent=2) + '\n')
        try:
            channel.publish('result.json', failure)
        except Exception:
            pass
        print('Build stopped at ' + STAGE + '; inspect build logs.', file=sys.stderr)
        raise SystemExit(1)
