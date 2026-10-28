#!/usr/bin/env python3
"""Rebuild only the reviewed overlay against the exact distributed 1.144 APK."""
from pathlib import Path
import argparse, hashlib, json, os, struct, subprocess, zipfile

BASE_SHA='f62e8d6020a3cacb2ea9a9085be235ea48da12f03c5de2f4e3ae80b801b6e970'
ROOT=Path(__file__).resolve().parent

def run(args,log):
 with log.open('w') as out:subprocess.run([str(x) for x in args],stdout=out,stderr=subprocess.STDOUT,check=True)

def manifest(data):
 data=bytearray(data);pos=8;strings=[];locations=[];changes=set()
 def length(at,wide):
  if wide:
   v=struct.unpack_from('<H',data,at)[0];at+=2
   if v&32768:v=((v&32767)<<16)|struct.unpack_from('<H',data,at)[0];at+=2
  else:
   v=data[at];at+=1
   if v&128:v=((v&127)<<8)|data[at];at+=1
  return v,at
 while pos<len(data):
  kind,header,size=struct.unpack_from('<HHI',data,pos)
  if size<header or pos+size>len(data):raise ValueError('Invalid AXML')
  if kind==1:
   count,_,flags,start=struct.unpack_from('<4I',data,pos+8)
   for i in range(count):
    at=pos+start+struct.unpack_from('<I',data,pos+header+i*4)[0]
    if flags&256:_,at=length(at,False);n,at=length(at,False);encoding='utf-8'
    else:n,at=length(at,True);n*=2;encoding='utf-16le'
    strings.append(bytes(data[at:at+n]).decode(encoding));locations.append((at,n,encoding))
  elif kind==0x102:
   node=struct.unpack_from('<I',data,pos+20)[0]
   if strings[node]=='manifest':
    start,step,count=struct.unpack_from('<3H',data,pos+24)
    for i in range(count):
     at=pos+16+start+i*step;_,name,raw=struct.unpack_from('<3I',data,at)
     if strings[name]=='versionCode':
      assert struct.unpack_from('<I',data,at+16)[0]==147
      struct.pack_into('<I',data,at+16,148);changes.add('code')
     elif strings[name]=='versionName':
      idx=struct.unpack_from('<I',data,at+16)[0];assert strings[idx]=='1.144'
      where,n,enc=locations[idx];replacement='1.145'.encode(enc);assert len(replacement)==n
      data[where:where+n]=replacement;changes.add('name')
  pos+=size
 assert changes=={'code','name'}
 return bytes(data)

def signature(name):
 u=name.upper()
 return u.startswith('META-INF/') and (u.endswith(('.SF','.RSA','.DSA','.EC')) or u=='META-INF/MANIFEST.MF')

def build(base,sdk,jadx):
 assert hashlib.sha256(base.read_bytes()).hexdigest()==BASE_SHA,'Wrong baseline APK'
 b=ROOT/'build';b.mkdir(exist_ok=True)
 for n in ['helpers','classes','dex','evidence']:(b/n).mkdir(exist_ok=True)
 evidence=b/'evidence';android=sdk/'platforms/android-35/android.jar';bt=sdk/'build-tools/35.0.0'
 run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',jadx,'-d',b/'helpers',ROOT/'tools/PatchPhysicalReturn.java'],evidence/'compile-patcher.log')
 helper=['java','-Xmx768m','-cp',str(b/'helpers')+os.pathsep+str(jadx),'PatchPhysicalReturn']
 run(helper+['api',base,b/'baseline-api.jar'],evidence/'compile-api.log')
 sources=sorted((ROOT/'src').rglob('*.java'))
 run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',str(b/'baseline-api.jar')+os.pathsep+str(android),'-d',b/'classes']+sources,evidence/'compile-runtime.log')
 with zipfile.ZipFile(b/'runtime.jar','w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted((b/'classes').rglob('*.class')):z.write(p,p.relative_to(b/'classes'))
 run([bt/'d8','--release','--min-api','24','--lib',android,'--classpath',b/'baseline-api.jar','--output',b/'dex',b/'runtime.jar'],evidence/'d8.log')
 run(helper+['merge',base,b/'dex/classes.dex',b/'merged.dex'],evidence/'merge.log')
 run(helper+['audit',base,b/'merged.dex'],evidence/'preservation-audit.log')
 with zipfile.ZipFile(base) as old,zipfile.ZipFile(b/'unaligned.apk','w') as new:
  for entry in old.infolist():
   if signature(entry.filename):continue
   data=old.read(entry)
   if entry.filename=='classes.dex':data=(b/'merged.dex').read_bytes()
   elif entry.filename=='AndroidManifest.xml':data=manifest(data)
   new.writestr(entry,data)
 with zipfile.ZipFile(base) as old,zipfile.ZipFile(b/'unaligned.apk') as new:
  kept=0
  for name in old.namelist():
   if name not in ('classes.dex','AndroidManifest.xml') and not signature(name):
    assert old.read(name)==new.read(name),name;kept+=1
  assert set(new.namelist())=={n for n in old.namelist() if not signature(n)}
 (evidence/'resource-preservation.json').write_text(json.dumps({'unchanged_entries':kept,'manifest_edits':['versionCode:147->148','versionName:1.144->1.145'],'target_sdk':35,'base_sha256':BASE_SHA},indent=2))
 output=ROOT/'output';output.mkdir(exist_ok=True);apk=output/'MarkerTap-v1.145-integrated-unsigned.apk'
 run([bt/'zipalign','-P','16','-f','4',b/'unaligned.apk',apk],evidence/'alignment-build.log')
 run([bt/'zipalign','-c','-P','16','4',apk],evidence/'alignment-check.log')
 run([bt/'aapt2','dump','badging',apk],evidence/'badging.log')
 print(apk)

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jadx',type=Path,required=True);a=p.parse_args()
 build(a.baseline.resolve(),a.sdk.resolve(),a.jadx.resolve())
