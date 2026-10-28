from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from zipfile import ZipFile
import hashlib
import os
import urllib.request

root=Path(__file__).resolve().parent/'tooling'
root.mkdir(exist_ok=True)
jobs=[
 ('platform','https://dl.google.com/android/repository/platform-35_r02.zip','0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
 ('build-tools','https://dl.google.com/android/repository/build-tools_r35_linux.zip','bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88'),
 ('jadx','https://github.com/skylot/jadx/releases/download/v1.5.3/jadx-1.5.3.zip','8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec'),
]
def fetch(job):
 name,url,expected=job
 archive=root/(name+'.zip')
 if not archive.exists():urllib.request.urlretrieve(url,archive)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise ValueError('Tool checksum mismatch: '+name)
 target=root/name
 with ZipFile(archive) as z:
  for entry in z.infolist():
   if not (target/entry.filename).resolve().is_relative_to(target.resolve()):raise ValueError('Unsafe archive path')
  z.extractall(target)
 print('Prepared '+name,flush=True)
with ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(fetch,jobs))
sdk=root/'sdk'
for kind,name,target in [('platforms','android-35',root/'platform/android-35'),('build-tools','35.0.0',root/'build-tools/android-15')]:
 folder=sdk/kind;folder.mkdir(parents=True,exist_ok=True);link=folder/name
 if not link.exists():link.symlink_to(target,target_is_directory=True)
for name in ['aapt2','apksigner','d8','zipalign']:
 p=root/'build-tools/android-15'/name;p.chmod(p.stat().st_mode|0o555)
print('export ANDROID_SDK_ROOT="'+str(sdk)+'"')
