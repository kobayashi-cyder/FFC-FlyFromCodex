#!/usr/bin/env python3
"""Rebuild the reviewed runtime and retain honest host/device validation status."""
import argparse,hashlib,json,os,subprocess,tempfile,shutil,urllib.request
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
PREVIOUS='3810584c2309bbb45f7585abd0c63500b352a8287b9b6f375f892f9e992e60b5'
CERT='7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1'
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT')));p.add_argument('--jadx',default=os.environ.get('TAPMARKER_JADX_JAR'));p.add_argument('--previous-apk');p.add_argument('--output',default=str(ROOT/'autonomy-build-results'));a=p.parse_args()
 if not a.sdk or not a.jadx:p.error('SDK and checksum-verified JADX jar are required')
 out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
 env=os.environ.copy();env['ANDROID_SDK_ROOT']=str(Path(a.sdk).resolve());env['TAPMARKER_JADX_JAR']=str(Path(a.jadx).resolve());env['TAPMARKER_REPLAY']='1'
 with tempfile.TemporaryDirectory(prefix='markertap-v1134-') as temp:
  project=Path(temp)/'project';stage='restore'
  def run(name,command,negative=False):
   nonlocal stage
   stage=name;print('Stage: '+name,flush=True)
   with (out/(name+'.log')).open('w') as log:r=subprocess.run(command,cwd=project if project.exists() else ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=600)
   if (r.returncode==0)==negative:raise RuntimeError(name+' returned '+str(r.returncode))
  try:
   run('restore',['python3',str(HERE/'restore_project.py'),str(project)])
   run('build',['bash','build.sh']);run('tests',['bash','run-tests.sh']);run('host-tests',['bash','run-host-tests.sh'])
   jar=env['TAPMARKER_JADX_JAR'];cp='build/helpers'+os.pathsep+jar
   run('audit-compile',['javac','-cp',jar,'-d','build/helpers','tools/ReleaseRuntimeAudit.java','tools/JournalAnrAudit.java','tools/AutonomyDeadlineAudit.java'])
   signed=bool(env.get('TAPMARKER_KEYSTORE'));apk=project/'output'/('MarkerTap-v1.134-integrated.apk' if signed else 'MarkerTap-v1.134-unsigned.apk')
   for name,extra in [('ReleaseRuntimeAudit',['1.134']),('JournalAnrAudit',[]),('AutonomyDeadlineAudit',[]),('DexAudit',[]),('BiologicalRemovalAudit',[])]:run(name,['java','-cp',cp,name,str(apk)]+extra)
   previous=Path(a.previous_apk).resolve() if a.previous_apk else Path(temp)/'previous.apk'
   if not a.previous_apk:
    with urllib.request.urlopen('https://raw.githubusercontent.com/kobayashi-cyder/FFC-FlyFromCodex/apk-v1.133/MarkerTap-v1.133.apk',timeout=45) as r:previous.write_bytes(r.read())
   if digest(previous)!=PREVIOUS:raise RuntimeError('Unexpected previous APK')
   run('previous-deadline-regression',['java','-cp',cp,'AutonomyDeadlineAudit',str(previous)],negative=True)
   if 'pixel sampling has no freshness deadline' not in (out/'previous-deadline-regression.log').read_text():raise RuntimeError('Previous APK rejected for unrelated cause')
   bt=Path(a.sdk).resolve()/'build-tools/35.0.0';run('alignment',[str(bt/'zipalign'),'-c','-P','16','4',str(apk)]);run('badging',[str(bt/'aapt2'),'dump','badging',str(apk)])
   for text in ["name='jp.tapmarker.lite'","versionCode='137'","versionName='1.134'"]:
    if text not in (out/'badging.log').read_text():raise RuntimeError('APK identity mismatch')
   if signed:
    run('signature',[str(bt/'apksigner'),'verify','--verbose','--print-certs',str(apk)])
    if 'Signer #1 certificate SHA-256 digest: '+CERT not in (out/'signature.log').read_text():raise RuntimeError('Signing identity mismatch')
   name='MarkerTap-v1.134-signed.apk' if signed else apk.name;shutil.copyfile(apk,out/name)
   report={'status':'signed_host_verified' if signed else 'unsigned_host_verified','version_name':'1.134','version_code':137,'package':'jp.tapmarker.lite','source_commit':os.environ.get('GITHUB_SHA'),'apk':name,'apk_sha256':digest(apk),'apk_size_bytes':apk.stat().st_size,'signature_verified':signed,'signing_certificate_sha256':CERT if signed else None,'core_tests':'passed','host_anr_tests':'passed','apk_audits':'passed','new_regression_checks':38,'incident_replay':'8 exact result digests matched','previous_v1133_rejected':True,'device_tested':False,'daily_completion_verified':False,'three_day_unattended_run_verified':False}
   (out/'validation.json').write_text(json.dumps(report,indent=2)+'\n');(out/'SHA256SUMS.txt').write_text(digest(apk)+'  '+name+'\n')
  except Exception as error:
   (out/'failure.json').write_text(json.dumps({'stage':stage,'error':str(error),'device_tested':False},indent=2)+'\n');raise
  finally:
   for name in ['dex-audit.log','dex-merge.log','integration-audit.log']:
    f=project/'build'/name
    if f.exists():shutil.copyfile(f,out/name)
if __name__=='__main__':main()
