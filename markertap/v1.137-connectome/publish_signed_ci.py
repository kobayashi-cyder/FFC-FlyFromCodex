#!/usr/bin/env python3
"""Publish v1.137 signed with the original key, without storing key material in GitHub.

The signer travels RSA-OAEP encrypted for one Actions session, using the
existing ephemeral-key handoff. The temporary key and plaintext signing
keystore are destroyed with the runner job.
"""
import hashlib,json,os,sys,subprocess,shutil,tempfile,time,urllib.request,base64,re
from pathlib import Path
import importlib.util

ROOT=Path(os.environ["GITHUB_WORKSPACE"])
sys.path.insert(0,str(ROOT/"markertap/tools"))
import import_source as channel
channel.CHANNEL="markertap/v1137-signed-publish-channel/"
spec=importlib.util.spec_from_file_location("secure_signing",ROOT/"markertap/tools/build_source.py")
transport=importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)

CERT="7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"
UNSIGNED_SHA="0538c5810223a9253ce6ca2fd17f6eff4ce56c89264e8865e0406a8f44158107"
TAG="markertap-v1.137"
APK="MarkerTap-v1.137-signed.apk"

def digest(p):
 h=hashlib.sha256()
 with open(p,"rb") as f:
  for data in iter(lambda:f.read(1<<20),b""):h.update(data)
 return h.hexdigest()

def verify_public(url, expected):
 for attempt in range(12):
  try:
   req=urllib.request.Request(url+"?proof="+expected,headers={"User-Agent":"MarkerTap-signed-distribution-test","Cache-Control":"no-cache"})
   with urllib.request.urlopen(req,timeout=50) as response:
    if response.status==200 and hashlib.sha256(response.read()).hexdigest()==expected:return
  except Exception:
   pass
  time.sleep(3)
 raise RuntimeError("public APK SHA-256 download verification failed")

def publish_raw(apk,hashsum):
 blob=channel.api("git/blobs",{"encoding":"base64","content":base64.b64encode(apk.read_bytes()).decode()})
 branch="apk-v1.137"
 readme=("# MarkerTap v1.137 (signed)\\n\\n"
         "Original signer SHA-256: "+CERT+"\\n"
         "APK SHA-256: "+hashsum+"\\n"
         "APK: https://raw.githubusercontent.com/"+channel.REPO+"/"+branch+"/"+APK+"\\n"
         "v1.137 host tests passed; Pixel 10 device stability unverified.\\n")
 tree=channel.api("git/trees",{"tree":[
  {"path":APK,"mode":"100644","type":"blob","sha":blob["sha"]},
  {"path":"README.md","mode":"100644","type":"blob","content":readme}
 ]})
 commit=channel.api("git/commits",{"message":"Publish original-certificate signed MarkerTap v1.137","tree":tree["sha"],"parents":[os.environ["GITHUB_SHA"]]})
 channel.api("git/refs",{"ref":"refs/heads/"+branch,"sha":commit["sha"]})
 return "https://raw.githubusercontent.com/"+channel.REPO+"/"+branch+"/"+APK

def main():
 if os.environ["GITHUB_REF_NAME"]!="markertap/publish-signed-v1.137":
  raise RuntimeError("unexpected publishing branch")
 with tempfile.TemporaryDirectory(prefix="markertap-v1137-private-",dir=os.environ["RUNNER_TEMP"]) as tmp:
  private=Path(tmp);private.chmod(0o700)
  unsigned=ROOT/"signed-upload-input/MarkerTap-v1.137-unsigned.apk"
  if not unsigned.is_file() or digest(unsigned)!=UNSIGNED_SHA:
   raise RuntimeError("source-verified v1.137 unsigned artifact mismatch")
  tools=Path(os.environ["ANDROID_HOME"])/"build-tools/35.0.0"
  apk=private/APK
  keystore=transport.receive_key(private)
  subprocess.run([str(tools/"apksigner"),"sign","--ks",str(keystore),
    "--ks-key-alias","tapmarker","--ks-pass","pass:android",
    "--v4-signing-enabled","false","--out",str(apk),str(unsigned)],check=True)
  verification=subprocess.check_output([str(tools/"apksigner"),"verify",
    "--verbose","--print-certs",str(apk)],text=True)
  if "Signer #1 certificate SHA-256 digest: "+CERT not in verification:
   raise RuntimeError("original signer mismatch; publication blocked")
  badge=subprocess.check_output([str(tools/"aapt2"),"dump","badging",str(apk)],text=True)
  for expected in ("name='jp.tapmarker.lite'","versionCode='140'","versionName='1.137'"):
   if expected not in badge:raise RuntimeError("release APK identity mismatch: "+expected)
  subprocess.run([str(tools/"zipalign"),"-c","-P","16","4",str(apk)],check=True)
  checksum=digest(apk)
  release=ROOT/"signed-publication"
  release.mkdir(exist_ok=True)
  shutil.copyfile(apk,release/APK)
  validation={
   "status":"signed_host_verified","version_name":"1.137","version_code":140,
   "package":"jp.tapmarker.lite","sha256":checksum,
   "signing_certificate_sha256":CERT,
   "prior_unsigned_sha256":UNSIGNED_SHA,
   "source_run_id":40015180432,"device_tested":False,
   "physical_ad_handoff_in_v1137":False,
   "original_certificate_match":True,"signed_apk_publicly_downloadable":False}
  (release/"SHA256SUMS.txt").write_text(checksum+"  "+APK+"\\n")
  (release/"validation.json").write_text(json.dumps(validation,ensure_ascii=False,indent=2)+"\\n")
  url=publish_raw(apk,checksum)
  verify_public(url,checksum)
  notes=private/"notes.md"
  notes.write_text("MarkerTap v1.137: verified connectome-based two-lane virtual admission.\\n\\n"
   "This signed APK uses the original MarkerTap certificate. Host build and 13 graph invariants passed. "
   "This build does not contain the later v1.138 physical ad-navigation patch. Pixel 10 long-run stability, rewards and ad transitions are not physically verified.\\n\\n"
   "Raw download: "+url+"\\nSHA256: "+checksum+"\\n")
  subprocess.run(["gh","release","create",TAG,str(release/APK),str(release/"SHA256SUMS.txt"),
    str(release/"validation.json"),"--repo",channel.REPO,"--target",os.environ["GITHUB_SHA"],
    "--title","MarkerTap v1.137 original-signer signed APK","--notes-file",str(notes),
    "--prerelease","--latest=false"],check=True,timeout=120)
  direct="https://github.com/"+channel.REPO+"/releases/download/"+TAG+"/"+APK
  verify_public(direct,checksum)
  validation["status"]="public_release_verified"
  validation["signed_apk_publicly_downloadable"]=True
  validation["apk_url"]=direct
  (release/"validation.json").write_text(json.dumps(validation,ensure_ascii=False,indent=2)+"\\n")
  channel.publish("result.json",validation)
  print("Signed v1.137 release and public SHA256 verification passed. "+direct)

if __name__=="__main__":
 try:main()
 except Exception as error:
  # Never write the keystore, ciphertext or private transfer to CI artifacts.
  try:channel.publish("result.json",{"status":"failed","failure_class":type(error).__name__,"public_release_verified":False})
  except Exception:pass
  raise
