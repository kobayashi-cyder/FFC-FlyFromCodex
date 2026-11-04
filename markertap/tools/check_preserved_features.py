#!/usr/bin/env python3
"""Stop a MarkerTap release that silently drops previously shipped controls.

This is a structural gate only, not evidence that the app functions correctly.
An intentional module replacement must have a separately reviewed behavior-parity
test before removing a requirement below.
"""
import argparse
import json
import struct
import zipfile

KEEP_CLASSES=(
 "ActionConnectome","AutonomySupervisor","ConnectomeRuntime",
 "ConnectomeSettings","KaitakuCollectionLoop","PanelModeController",
 "CustomConnectome","PhysicalFlowRecovery","FrameFreshnessGate",
 "OwnerDeathCleanup",
)
KEEP_ASSETS=(
 "assets/custom-connectome.bin",
 "assets/custom-connectome-provenance.json",
)
def uleb(data,off):
 value=0
 for bit in range(0,35,7):
  b=data[off];off+=1;value|=(b&127)<<bit
  if not b&128:return value,off
 raise ValueError("bad uleb")

def defined(data):
 if data[:4]!=bytes((100,101,120,10)):raise ValueError("Not a DEX")
 size,so,typeto,tido=struct.unpack_from("<4I",data,0x38)
 nc,co=struct.unpack_from("<2I",data,0x60)
 if max(size,typeto,nc)>1000000:raise ValueError("Corrupt DEX header")
 strings=[]
 for i in range(size):
  p=struct.unpack_from("<I",data,so+4*i)[0]
  _,p=uleb(data,p)
  strings.append(data[p:data.index(0,p)].decode("utf-8","replace"))
 types=[strings[struct.unpack_from("<I",data,tido+4*i)[0]] for i in range(typeto)]
 return {types[struct.unpack_from("<I",data,co+i*32)[0]] for i in range(nc)}

def main():
 p=argparse.ArgumentParser();p.add_argument("apk");p.add_argument("--report")
 args=p.parse_args()
 with zipfile.ZipFile(args.apk) as z:
  files=set(z.namelist())
  dex=[n for n in files if n=="classes.dex" or (n.startswith("classes") and n.endswith(".dex"))]
  classes=set()
  for n in dex:classes|=defined(z.read(n))
 missing_classes=[x for x in KEEP_CLASSES if "Ljp/tapmarker/"+x+";" not in classes]
 missing_assets=[x for x in KEEP_ASSETS if x not in files]
 report={"apk":args.apk,"defined_classes":len(classes),
         "missing_legacy_classes":missing_classes,
         "missing_legacy_assets":missing_assets,
         "pass":not missing_classes and not missing_assets,
         "status":"structural_gate_not_functional_test"}
 if args.report:
  with open(args.report,"w",encoding="utf-8") as f:json.dump(report,f,indent=2,ensure_ascii=False)
 print(json.dumps(report,indent=2,ensure_ascii=False))
 if not report["pass"]:
  p.exit(2,"FAIL: previously signed MarkerTap features were lost. Do not sign, merge, or publish.\\n")
if __name__=="__main__":main()
