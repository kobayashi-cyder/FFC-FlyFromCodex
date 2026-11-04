#!/usr/bin/env python3
"""Build a v1.158 UNSIGNED binary graft directly from original-signed v1.157.

Keeps all shipped classes and resources. Only modifies AdV157DexBridge to
observe positive advertisement frames and append score helper classes.
The original v1.157 automation/recovery and PanelModeController remain
unchanged. The full source v1.158 is intentionally NOT used.

This is an incremental engineering candidate, NOT a completed reward-earning
app or an installable update. Original signing key + device validation are
mandatory before any distribution.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import struct
import subprocess
import zipfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent
BASE_SHA="0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d"
OUTNAME="MarkerTap-v1.158-preserved-unsigned.apk"

def sha256(path):
    h=hashlib.sha256()
    with Path(path).open("rb") as f:
        for block in iter(lambda:f.read(1024*1024),b""):
            h.update(block)
    return h.hexdigest()

def run(command,log):
    log.parent.mkdir(parents=True,exist_ok=True)
    with log.open("w",encoding="utf-8") as out:
        try:
            subprocess.run([str(x) for x in command],check=True,
                           stdout=out,stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError:
            print(out.name,Path(out.name).read_text(encoding="utf-8",errors="replace")[-5000:])
            raise

def binary_manifest(blob):
    b=bytearray(blob)
    strings=[]
    locations=[]
    at=8
    modified=set()
    def length(i,wide):
        if wide:
            v=struct.unpack_from("<H",b,i)[0];i+=2
            if v&0x8000:v=((v&0x7fff)<<16)|struct.unpack_from("<H",b,i)[0];i+=2
        else:
            v=b[i];i+=1
            if v&0x80:v=((v&0x7f)<<8)|b[i];i+=1
        return v,i
    while at<len(b):
        typ,header,size=struct.unpack_from("<HHI",b,at)
        if size<header or at+size>len(b):raise ValueError("Corrupt binary Android manifest")
        if typ==1:
            count,_,flags,start=struct.unpack_from("<4I",b,at+8)
            for i in range(count):
                p=at+start+struct.unpack_from("<I",b,at+header+4*i)[0]
                if flags&256:
                    _,p=length(p,False);n,p=length(p,False);encoding="utf-8"
                else:
                    n,p=length(p,True);n*=2;encoding="utf-16le"
                strings.append(bytes(b[p:p+n]).decode(encoding))
                locations.append((p,n,encoding))
        elif typ==0x102:
            tag=struct.unpack_from("<I",b,at+20)[0]
            if strings[tag]=="manifest":
                offset,step,count=struct.unpack_from("<3H",b,at+24)
                for i in range(count):
                    p=at+16+offset+step*i
                    _,name,_=struct.unpack_from("<3I",b,p)
                    if strings[name]=="versionCode":
                        old=struct.unpack_from("<I",b,p+16)[0]
                        if old!=160:raise ValueError("Expected v1.157 versionCode=160, got "+str(old))
                        struct.pack_into("<I",b,p+16,161);modified.add("code")
                    if strings[name]=="versionName":
                        idx=struct.unpack_from("<I",b,p+16)[0]
                        if strings[idx]!="1.157":raise ValueError("Wrong v1.157 version name")
                        loc,n,encoding=locations[idx]
                        repl="1.158".encode(encoding)
                        if len(repl)!=n:raise ValueError("Manifest string replacement length mismatch")
                        b[loc:loc+n]=repl;modified.add("name")
        at+=size
    if modified!={"code","name"}:raise ValueError("Manifest v1.158 identity not patched")
    return bytes(b)

def inject_bridge(out):
    original=(ROOT/"v1.145-physical-return"/"src/jp/tapmarker/AdV157DexBridge.java").read_text()
    physical="public static void runPhysical(Object original,int token){"
    virtual="public static void runVirtual(Object original,Object slot,Map<String,float[]> samples,Rect rect,int token){"
    if original.count(physical)!=1 or original.count(virtual)!=1:
        raise ValueError("Signed-v157 bridge source entrypoint signature changed")
    phys_hook="""public static void runPhysical(Object original,int token){
   // Read-only measurement; never blocks existing v1.157 recovery on failure.
   try{
     Object phases=get(original,"phases"),model=get(phases,"engine");
     @SuppressWarnings("unchecked") Map<String,float[]> samples=
       (Map<String,float[]>)get(original,"phaseSamples");
     int[] dimensions=(int[])invoke(original,"displaySize");
     long captured=time(original,"phaseSampleTakenAt"),now=SystemClock.uptimeMillis();
     RewardV158Observer.observe(original,captured,now,
         ad(model,samples,dimensions[0],dimensions[1]));
   }catch(Throwable unavailable){}"""
    virt_hook="""public static void runVirtual(Object original,Object slot,Map<String,float[]> samples,Rect rect,int token){
   try{
     Object phases=get(slot,"phases"),model=get(phases,"engine");
     RewardV158Observer.observe(slot,time(slot,"observedAt"),
       SystemClock.uptimeMillis(),ad(model,samples,rect.width(),rect.height()));
   }catch(Throwable unavailable){}"""
    generated=original.replace(physical,phys_hook).replace(virtual,virt_hook)
    p=out/"src/jp/tapmarker/AdV157DexBridge.java"
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(generated,encoding="utf-8")
    return p

def signature(name):
    s=name.upper()
    return s.startswith("META-INF/") and (
        s.endswith((".SF",".RSA",".DSA",".EC")) or s=="META-INF/MANIFEST.MF")

def build(base,sdk,jadx):
    if sha256(base)!=BASE_SHA:
        raise ValueError("STOP: not the authenticated original-signed v1.157 APK")
    work=HERE/"build"/"v158-preserved"
    for name in ["src","classes","helpers","dex","evidence"]:
        (work/name).mkdir(parents=True,exist_ok=True)
    logs=work/"evidence"
    android=sdk/"platforms/android-35/android.jar"
    tools=sdk/"build-tools/35.0.0"
    if not android.is_file() or not jadx.is_file():
        raise FileNotFoundError("SHA-pinned SDK35 / dexlib2 jar is mandatory")
    bridge=inject_bridge(work)
    sources=[
      bridge,
      HERE/"RewardV158Observer.java",
      ROOT/"code/TapMarker/src/jp/tapmarker/RewardEvaluationConnectome.java",
      ROOT/"code/TapMarker/src/jp/tapmarker/RewardLanePlanner.java",
      ROOT/"code/TapMarker/src/jp/tapmarker/KurashiruPointHud.java",
      ROOT/"code/TapMarker/src/jp/tapmarker/AdTwoSecondRecovery.java",
    ]
    run(["javac","--release","8","-encoding","UTF-8","-cp",android,
         "-d",work/"classes"]+sources,logs/"compile-runtime.log")
    runtime=work/"overlay.jar"
    with zipfile.ZipFile(runtime,"w",zipfile.ZIP_DEFLATED) as z:
        candidates=sorted((work/"classes").rglob("*.class"))
        count=0
        for path in candidates:
            name=path.name
            if not any(name.startswith(x) for x in (
              "AdV157DexBridge","RewardV158Observer","RewardEvaluationConnectome",
              "RewardLanePlanner","KurashiruPointHud")):
                continue
            z.write(path,path.relative_to(work/"classes"))
            count+=1
        if count<5:raise ValueError("Missing v1.158 compiled overlay classes")
    run([tools/"d8","--release","--min-api","24","--lib",android,
         "--output",work/"dex",runtime],logs/"d8.log")
    patcher=HERE/"PatchRewardV158.java"
    run(["javac","--release","8","-encoding","UTF-8","-cp",jadx,
         "-d",work/"helpers",patcher],logs/"compile-patcher.log")
    java=["java","-Xmx1g","-cp",str(work/"helpers")+os.pathsep+str(jadx),
          "PatchRewardV158"]
    merged=work/"merged.dex"
    dex=work/"dex/classes.dex"
    run(java+["merge",base,dex,merged],logs/"merge.log")
    run(java+["audit",base,dex,merged],logs/"dex-all-class-audit.log")
    unaligned=work/"unaligned.apk"
    with zipfile.ZipFile(base) as source,zipfile.ZipFile(unaligned,"w") as out:
        if source.testzip() is not None:raise ValueError("Original signed base corrupted")
        for info in source.infolist():
            if signature(info.filename):continue
            data=source.read(info)
            if info.filename=="classes.dex":data=merged.read_bytes()
            elif info.filename=="AndroidManifest.xml":data=binary_manifest(data)
            out.writestr(info,data)
    with zipfile.ZipFile(base) as original,zipfile.ZipFile(unaligned) as candidate:
        preserve={x for x in original.namelist() if not signature(x)}
        if preserve!=set(candidate.namelist()):
            raise ValueError("APK entry removal/addition detected")
        for name in preserve-{"classes.dex","AndroidManifest.xml"}:
            if original.read(name)!=candidate.read(name):
                raise ValueError("Old APK resources changed: "+name)
        if candidate.testzip() is not None:raise ValueError("Candidate CRC failed")
    final=HERE/"output"/OUTNAME
    final.parent.mkdir(parents=True,exist_ok=True)
    run([tools/"zipalign","-P","16","-f","4",unaligned,final],logs/"zipalign.log")
    run([tools/"zipalign","-c","-P","16","4",final],logs/"zipalign-check.log")
    run([tools/"aapt2","dump","badging",final],logs/"badging.log")
    badging=(logs/"badging.log").read_text()
    for key in ("name='jp.tapmarker.lite'","versionCode='161'","versionName='1.158'"):
        if key not in badging:raise ValueError("Wrong binary manifest: "+key)
    report={
      "status":"UNSIGNED_engineering_candidate_not_updatable_not_device_tested",
      "baseline_sha256":BASE_SHA,
      "new_version":"1.158","new_version_code":161,
      "apk_sha256":sha256(final),"size_bytes":final.stat().st_size,
      "preserved_all_original_classes":True,
      "preserved_all_original_assets":True,
      "original_minimized_float_preserved":True,
      "new_binary_scoring":"CM positive frame time only; reward gate not yet wired to signed DEX",
      "safe_for_release":False,
      "original_signature":False,
      "pixel10_tested":False,
    }
    (logs/"result.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print("Authenticated v1.157 preserved in v1.158 UNSIGNED engineering candidate:",final)
    print("SHA256",report["apk_sha256"])
    return final

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--baseline",required=True,type=Path)
    p.add_argument("--sdk",required=True,type=Path)
    p.add_argument("--jadx",required=True,type=Path)
    args=p.parse_args()
    build(args.baseline.resolve(),args.sdk.resolve(),args.jadx.resolve())
