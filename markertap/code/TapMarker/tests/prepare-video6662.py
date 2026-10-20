"""Recreate video evidence from the supplied 6662.mp4; no account or device access."""
import sys, json, gzip, struct, hashlib, subprocess, tempfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent.parent
video = Path(sys.argv[1])
pairs = {
    'home': [[.388, .092, .503, .106], [.143, .878, .258, .891]],
    'fever': [[.570, .284, .815, .324], [.143, .878, .258, .891]],
    'bonus': [[.202, .340, .798, .363], [.320, .774, .680, .794]],
    'clear': [[.453, .399, .548, .431], [.457, .467, .557, .524]],
    'missions': [[.289, .166, .715, .193], [.019, .168, .053, .196]],
    'get': [[.289, .166, .715, .193], [.729, .400, .869, .425]],
    'play': [[.289, .166, .715, .193], [.729, .400, .869, .425]],
    'reward': [[.179, .341, .824, .371], [.421, .556, .576, .577]],
    'boost-rare': [[.246, .295, .763, .331], [.267, .749, .738, .775]],
    'boost-pocket': [[.13, .281, .867, .314], [.256, .740, .739, .769]],
}
training = {'home': 0, 'home-gold': 6, 'fever': 1, 'fever-gold': 4, 'bonus': 14, 'clear': 6.5}
training.update({'missions':14.3,'get':14.3,'play':14.5,'reward':14.45,'boost-rare':8.3,'boost-pocket':2.3})
times = [0, .50, .75, 1, 1.25, 1.5, 2, 3, 3.5, 4, 4.25, 4.5, 6, 6.5, 6.55, 6.6, 8, 8.5,
         10, 12, 12.5, 14, 14.25, 14.5, 16, 18, 20, 22, 22.25, 22.5, 24, 26, 28,
         30, 30.5, 32, 33, 33.5, 34]
times=sorted(set(times+[2.3,3.5,3.6,3.75,4.8,5.3,7.625,8.3,14.3,14.375,14.45,15,22.3,22.375,31,33.6]))

def feature(image, bounds):
    height, width = image.shape[:2]
    r = np.asarray(bounds, dtype=np.float32)
    left, top, right, bottom = [int(v) for v in r * np.array([width,height,width,height],dtype=np.float32)]
    left, top = max(0,min(width-1,left)), max(0,min(height-1,top))
    right, bottom = max(left+1,min(width,right)), max(top+1,min(height,bottom))
    w, h = right-left, bottom-top
    result=[]
    for y in range(16):
        for x in range(16):
            pixel=np.zeros(3,dtype=np.float32)
            for dy in range(3):
                for dx in range(3):
                    px=min(w-1,int((x+(dx+.5)/3)*w/16))
                    py=min(h-1,int((y+(dy+.5)/3)*h/16))
                    pixel += image[top+py,left+px]
            result.extend((pixel/np.float32(9*255)).tolist())
    return result

def utf(out, value):
    b=value.encode('utf-8');out.write(struct.pack('>H',len(b)));out.write(b)

with tempfile.TemporaryDirectory() as tmp:
    images={}
    for t in times:
        p=Path(tmp)/f'{t:.2f}.png'
        subprocess.run(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-y',str(p)],check=True)
        images[t]=Image.open(p).convert('RGB')
    refs={}
    for key,t in training.items():
        anchors=pairs[key.removesuffix('-gold')]
        data=np.asarray(images[t]);refs[key]=sum([feature(data,b) for b in anchors],[])
    # Ready skip symbol: yellow circular border and two white chevrons, not its timer.
    data=np.asarray(images[30]); mask=(data[:,:,0]>190)&(data[:,:,1]>150)&(data[:,:,2]<110)
    seen=set();components=[]
    for y in range(140,270):
        for x in range(745,840):
            if not mask[y,x] or (x,y) in seen:continue
            seen.add((x,y));todo=[(x,y)]
            for px,py in todo:
                for dx in [-1,0,1]:
                    for dy in [-1,0,1]:
                        nx,ny=px+dx,py+dy
                        if 745<=nx<840 and 140<=ny<270 and mask[ny,nx] and (nx,ny) not in seen:
                            seen.add((nx,ny));todo.append((nx,ny))
            if len(todo)>40:components.append(todo)
    ring=max(components,key=len)
    xs,ys=zip(*ring);skip=[min(xs)/data.shape[1],min(ys)/data.shape[0],(max(xs)+1)/data.shape[1],(max(ys)+1)/data.shape[0]]
    refs['ad-skip']=feature(data,skip)
    (root/'assets/coin-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
    with (root/'tests/video6662-references.bin').open('wb') as out:
        out.write(struct.pack('>i',len(refs)))
        for key,values in refs.items():
            utf(out,key);out.write(struct.pack('>i',len(values)));out.write(struct.pack('>'+str(len(values))+'f',*values))
    frames=[]
    for t in times:
        frames.append((f'{t:.2f}',images[t].resize((428,960),Image.Resampling.NEAREST)))
    for t in [0, 1.25, 12, 14, 14.25, 14.3, 14.45, 22, 30, 34]:frames.append((f'{t:.2f}-native',images[t]))
    with gzip.open(root/'tests/video6662-replay.bin.gz','wb',compresslevel=6) as out:
        out.write(struct.pack('>i',len(frames)))
        for name,im in frames:
            utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
    evidence={'source':'6662.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),
              'app':'com.kurashiru.coin.drop','package_source':'https://play.google.com/store/apps/details?id=com.kurashiru.coin.drop',
              'width':856,'height':1920,'duration_seconds':34.363665,'training_seconds':training,
              'pairs':pairs,'skip_ready_symbol_bounds':skip,'skip_training_seconds':30,
              'replay_frames':[{'id':name,'size':im.size,'rgb_sha256':hashlib.sha256(im.tobytes()).hexdigest()} for name,im in frames],
              'limits':'Edited InShot video with cuts, touch indicators and top/bottom crop. Resized/inset and moved-button variants are synthetic; Android input, hidden mission rows and unknown ad symbols are not verified.'}
    (root/'tests/video6662-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps({'references':list(refs),'frames':len(frames),'skip':skip}))
