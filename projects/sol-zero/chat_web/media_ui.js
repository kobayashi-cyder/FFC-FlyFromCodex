/** Unified offline media workspace for Sol-Zero. No external AI/services. */
import {MEDIA_LIMITS,validateMediaSpec,renderMediaScene,audioSpec,synthSamples,wavBytes,rgbaToIndex,gifBytes,zipBytes,unzipStored,MediaWSP} from './media_studio.js';
const STORE='solzero_studio_v012_recipe',DB_NAME='solzero_media_v012';
function element(tag,txt){const x=document.createElement(tag);if(txt!==undefined)x.textContent=txt;return x;}
function mimeFor(name){const ext=name.toLowerCase().split('.').pop();return ({png:'image/png',gif:'image/gif',jpg:'image/jpeg',jpeg:'image/jpeg',wav:'audio/wav',mp3:'audio/mpeg',webm:'video/webm',mp4:'video/mp4'})[ext]||null;}
function fileDownload(data,name,type){const blob=new Blob([data],{type}),url=URL.createObjectURL(blob),a=element('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),10_000);}
function makeCanvas(w,h){const c=element('canvas');c.width=w;c.height=h;return c;}
function toPng(canvas){return new Promise((resolve,reject)=>canvas.toBlob(async blob=>{if(!blob)return reject(Error('PNG変換に失敗'));resolve(new Uint8Array(await blob.arrayBuffer()));},'image/png'));}
async function studioDB(){if(typeof indexedDB==='undefined')return null;return new Promise((res)=>{let r;try{r=indexedDB.open(DB_NAME,1);r.onupgradeneeded=()=>{if(!r.result.objectStoreNames.contains('assets'))r.result.createObjectStore('assets',{keyPath:'id'});};r.onsuccess=()=>res(r.result);r.onerror=()=>res(null);r.onblocked=()=>res(null);}catch{res(null);}});}
async function dbOp(op,value){const db=await studioDB();if(!db)return null;return new Promise(resolve=>{let req;try{const tx=db.transaction('assets',op==='getAll'?'readonly':'readwrite'),s=tx.objectStore('assets');req=op==='getAll'?s.getAll():op==='put'?s.put(value):op==='clear'?s.clear():s.delete(value);req.onsuccess=()=>resolve(req.result??true);req.onerror=()=>resolve(null);tx.oncomplete=()=>db.close();tx.onerror=()=>db.close();}catch{db.close();resolve(null);}});}
export class MediaStudio {
 constructor(){this.assets=[];this.loaded=false;this.busy=false;this.wsp=new MediaWSP();this.project={name:'Sol-Zero作品',prompt:'夕焼けの海に浮かぶ星',seed:2026,theme:'auto',width:512,height:288,frames:24,fps:8,kind:'waves',text:'こんにちは'};
  try{const raw=JSON.parse(localStorage.getItem(STORE)||'null');if(raw&&typeof raw==='object'){Object.assign(this.project,raw);if(raw.wsp)this.wsp.restore(raw.wsp);}}catch{}
 }
 async ensureLoaded(){if(this.loaded)return;this.loaded=true;const rows=await dbOp('getAll');if(Array.isArray(rows)){for(const a of rows){if(a?.id&&a?.name&&a?.bytes instanceof Uint8Array&&a.bytes.length<=MEDIA_LIMITS.projectBytes&&this.assets.length<MEDIA_LIMITS.assets)this.assets.push(a);}}}
 saveRecipe(){try{localStorage.setItem(STORE,JSON.stringify({...this.project,wsp:this.wsp.snapshot()}));return true;}catch{return false;}}
 buildSpec(p=this.project){const theme=p.theme==='auto'?undefined:p.theme;const result=validateMediaSpec({prompt:p.prompt,seed:Number(p.seed),width:Number(p.width),height:Number(p.height),frames:Number(p.frames),fps:Number(p.fps),theme});return validateMediaSpec({...result,controls:this.wsp.controls(result.theme)});}
 async add(name,type,bytes,kind,spec){if(!(bytes instanceof Uint8Array)||bytes.length>22_000_000||bytes.length===0)throw Error('素材サイズが範囲外です');if(this.assets.length>=MEDIA_LIMITS.assets||this.assets.reduce((n,a)=>n+a.bytes.length,0)+bytes.length>MEDIA_LIMITS.projectBytes)throw Error('素材の個数または合計容量が上限です');const id=(typeof crypto!=='undefined'&&crypto.randomUUID)?crypto.randomUUID():String(Date.now())+'_'+Math.random().toString(16).slice(2);
  const asset={id,name,type,bytes,kind,spec,created:new Date().toISOString()};this.assets.push(asset);await dbOp('put',asset);return asset;
 }
 async remove(id){this.assets=this.assets.filter(a=>a.id!==id);await dbOp('delete',id);}
 async clear(){this.assets=[];await dbOp('clear');}
 async makeImage(p=this.project){const s=this.buildSpec(p),canvas=makeCanvas(s.width,s.height);renderMediaScene(canvas.getContext('2d'),s,0);const bytes=await toPng(canvas);return this.add(`media_${Date.now()}.png`,'image/png',bytes,'image',s);}
 async makeAudio(p=this.project){const spec=audioSpec({kind:p.kind,seconds:Number(p.frames)/Number(p.fps),seed:Number(p.seed),text:p.text,pitch:this.buildSpec(p).controls.pitch}),bytes=wavBytes(spec);return this.add(`audio_${Date.now()}.wav`,'audio/wav',bytes,'audio',spec);}
 async makeGif(p=this.project){const origin=this.buildSpec(p),s={...origin,width:Math.min(320,origin.width),height:Math.min(180,origin.height)},frames=[],canvas=makeCanvas(s.width,s.height),ctx=canvas.getContext('2d',{willReadFrequently:true});
  for(let i=0;i<s.frames;i++){renderMediaScene(ctx,s,i/s.fps);frames.push(rgbaToIndex(ctx.getImageData(0,0,s.width,s.height).data,s.width,s.height));if(i%5===0)await new Promise(resolve=>setTimeout(resolve,0));}
  const bytes=gifBytes(frames,s.width,s.height,s.fps);return this.add(`animation_${Date.now()}.gif`,'image/gif',bytes,'animation',s);
 }
 async makeWebm(p=this.project){
  const origin=this.buildSpec(p),s={...origin,width:Math.min(320,origin.width),height:Math.min(180,origin.height)};
  if(typeof MediaRecorder==='undefined'||typeof AudioContext==='undefined')throw Error('このブラウザはWebM録画・音声合成に対応しません。GIFとWAVは利用可能です。');
  const type=['video/webm;codecs=vp8,opus','video/webm;codecs=vp9,opus','video/webm'].find(x=>MediaRecorder.isTypeSupported(x));if(!type)throw Error('WebMエンコーダがありません。GIFで書き出してください。');
  const canvas=makeCanvas(s.width,s.height),ctx=canvas.getContext('2d'),ac=new AudioContext(),stream=canvas.captureStream(s.fps),dest=ac.createMediaStreamDestination();let rec=null;
  try{
   const spec=audioSpec({kind:p.kind,seconds:s.frames/s.fps,seed:s.seed,text:p.text,pitch:s.controls.pitch}),a=synthSamples(spec),audio=ac.createBuffer(1,a.samples.length,a.rate);audio.copyToChannel(a.samples,0);const source=ac.createBufferSource();source.buffer=audio;source.connect(dest);
   const joined=new MediaStream([...stream.getVideoTracks(),...dest.stream.getAudioTracks()]);rec=new MediaRecorder(joined,{mimeType:type,videoBitsPerSecond:500_000,audioBitsPerSecond:64_000});let chunks=[];
   const finished=new Promise((resolve,reject)=>{rec.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};rec.onerror=e=>reject(Error(e.error?.message||'録画エラー'));rec.onstop=async()=>{try{resolve(new Uint8Array(await new Blob(chunks,{type}).arrayBuffer()));}catch(err){reject(err);}};});
   renderMediaScene(ctx,s,0);await ac.resume();rec.start(200);source.start();
   for(let i=0;i<s.frames;i++){renderMediaScene(ctx,s,i/s.fps);await new Promise(resolve=>setTimeout(resolve,1000/s.fps));}
   if(rec.state!=='inactive')rec.stop();const bytes=await Promise.race([finished,new Promise((_,reject)=>setTimeout(()=>reject(Error('WebM保存タイムアウト')),3500))]);
   if(bytes.length<100)throw Error('WebMが空です');return await this.add(`movie_${Date.now()}.webm`,'video/webm',bytes,'video',s);
  }finally{try{if(rec&&rec.state!=='inactive')rec.stop();}catch{}stream.getTracks().forEach(x=>x.stop());dest.stream.getTracks().forEach(x=>x.stop());await ac.close();}
 }
 async generate(kind,override=null){if(this.busy)throw Error('すでに生成中です');this.busy=true;try{const p=override?{...this.project,...override}:this.project;switch(kind){case'image':return await this.makeImage(p);case'audio':return await this.makeAudio(p);case'gif':return await this.makeGif(p);case'webm':return await this.makeWebm(p);default:throw Error('生成種別が不正です');}}finally{this.busy=false;}}
 download(asset){fileDownload(asset.bytes,asset.name,asset.type);}
 exportZip(){const manifest={format:'solzero-media-v012',version:1,project:{...this.project,wsp:this.wsp.snapshot()},assets:this.assets.map((a,i)=>({file:`assets/a${String(i).padStart(2,'0')}.${a.name.split('.').at(-1)}`,kind:a.kind,mime:a.type,originalName:a.name,spec:a.spec,created:a.created}))};const entries=[{name:'project.json',bytes:JSON.stringify(manifest,null,2)},...manifest.assets.map((a,i)=>({name:a.file,bytes:this.assets[i].bytes}))];const bytes=zipBytes(entries);fileDownload(bytes,'SolZero_media_project.zip','application/zip');return bytes.length;}
 async importZip(file){if(file.size>MEDIA_LIMITS.projectBytes+100_000)throw Error('ZIP上限超過');const entries=unzipStored(new Uint8Array(await file.arrayBuffer())),manifestFile=entries.find(x=>x.name==='project.json');if(!manifestFile)throw Error('project.jsonなし');const m=JSON.parse(new TextDecoder().decode(manifestFile.bytes));if(m.format!=='solzero-media-v012'||m.version!==1||!Array.isArray(m.assets)||m.assets.length>MEDIA_LIMITS.assets)throw Error('プロジェクト形式不正');
  const next=[];for(const a of m.assets){if(typeof a.file!=='string'||!/^assets\/a\d{2}\.(png|gif|jpg|jpeg|wav|mp3|webm|mp4)$/.test(a.file))throw Error('素材名が不正');const f=entries.find(x=>x.name===a.file),type=mimeFor(a.file);if(!f||f.bytes.length>22_000_000||a.mime!==type)throw Error('素材と説明が不一致');next.push({id:(crypto.randomUUID?crypto.randomUUID():String(Date.now()+next.length)),name:String(a.originalName||a.file).slice(0,120),type,bytes:f.bytes,kind:String(a.kind||'import'),spec:a.spec||{},created:a.created||new Date().toISOString()});}
  if(next.reduce((n,a)=>n+a.bytes.length,0)>MEDIA_LIMITS.projectBytes)throw Error('総容量が多すぎます');const p=m.project;if(!p||typeof p!=='object'||!validateMediaSpec({...p,theme:p.theme==='auto'?undefined:p.theme}))throw Error('作品仕様が不正');
  await this.clear();for(const a of next){this.assets.push(a);await dbOp('put',a);}this.project={...this.project,...p};if(p.wsp)this.wsp.restore(p.wsp);this.saveRecipe();return next.length;
 }
 async importMedia(files){let added=0;for(const f of [...files]){const type=mimeFor(f.name);if(!type||f.type && type!==f.type||f.size>22_000_000)continue;await this.add(`imported_${Date.now()}_${added}.${f.name.split('.').at(-1).toLowerCase()}`,type,new Uint8Array(await f.arrayBuffer()),'import',{originalName:f.name.slice(0,100)});added++;}return added;}
 render(root,helpers){const {notify}=helpers;root.replaceChildren();const notice=element('p','生成AIではなく、コードから直接画像・波形・フレームを作る独自エンジンです。学習済みモデル・通信・外部APIなし。WebMはブラウザ対応時のみ音声入りで生成します。');notice.className='muted';root.append(notice);
  const setRow=(label,type,value,options=null)=>{const row=element('div');row.className='field';const caption=element('label',label),input=options?element('select'):element(type==='textarea'?'textarea':'input');if(!options&&type!=='textarea')input.type=type;if(options)for(const [val,text] of options){const option=element('option',text);option.value=val;input.append(option);}input.value=String(value);caption.append(input);row.append(caption);root.append(row);return input;};
  const name=setRow('作品名','text',this.project.name),prompt=setRow('生成指示（対応テーマ：宇宙・海・山・森・街・花・雨・雪・夕焼け・抽象）','textarea',this.project.prompt);
  const seed=setRow('乱数シード','number',this.project.seed),theme=setRow('画風','text',this.project.theme,[['auto','指示から自動選択'],...['space','sea','mountain','forest','city','flowers','rain','snow','sunset','abstract'].map(x=>[x,x])]);
  const dims=setRow('画像サイズ','text',`${this.project.width}x${this.project.height}`,[['512x288','512×288'],['768x432','768×432'],['320x180','320×180'],['1024x576','1024×576']]);
  const duration=setRow('動画長（フレーム数）','text',`${this.project.frames}`,[['16','2秒'],['24','3秒'],['32','4秒'],['48','6秒']]);
  const sound=setRow('音の種類','text',this.project.kind,[['ambient','アンビエント'],['waves','波音'],['wind','風音'],['tones','オリジナル電子音'],['robot','簡易ロボット声（音素風）']]);
  const speech=setRow('ロボット声の文字','text',this.project.text);
  const log=element('div','準備完了。生成結果は端末に保存されます（対応ブラウザのIndexedDB）。');log.className='listentry';root.append(log);
  const btnrow=element('div');btnrow.className='buttons';root.append(btnrow);
  const collect=()=>{const [w,h]=dims.value.split('x').map(Number);const p={name:name.value.slice(0,60),prompt:prompt.value,seed:Number(seed.value),theme:theme.value,width:w,height:h,frames:Number(duration.value),fps:8,kind:sound.value,text:speech.value};this.buildSpec(p);audioSpec({kind:p.kind,seconds:p.frames/p.fps,seed:p.seed,text:p.text});this.project=p;this.saveRecipe();return p;};
  const action=(label,fn)=>{const b=element('button',label);b.className='btn primary';b.addEventListener('click',async()=>{if(this.busy)return;try{const p=collect();b.disabled=true;log.textContent='処理中：'+label;const result=await fn(p);log.textContent=typeof result==='string'?result:`生成完了：${result.name}（${result.bytes.length.toLocaleString('ja-JP')}バイト）`;this.drawAssets(list,notify);}catch(err){log.textContent='エラー：'+err.message;}finally{b.disabled=false;}});btnrow.append(b);};
  action('画像 PNG',p=>this.generate('image',p));action('音声 WAV',p=>this.generate('audio',p));action('動画 GIF',p=>this.generate('gif',p));action('音声付き動画 WebM',p=>this.generate('webm',p));
  action('画像＋音声＋GIF一括',async p=>{let count=0;for(const kind of ['image','audio','gif']){await this.generate(kind,p);count++;}return `${count}種類の素材を生成しました。作品ZIPで一括取得できます。`;});
  const extras=element('div');extras.className='buttons';root.append(extras);
  const extra=(label,fn)=>{const b=element('button',label);b.className='btn';b.addEventListener('click',fn);extras.append(b);};
  extra('全素材をZIPで書き出し',()=>{try{log.textContent='作品ZIP保存：'+this.exportZip().toLocaleString('ja-JP')+'バイト';}catch(e){log.textContent='ZIPエラー：'+e.message;}});
  const inp=element('input');inp.type='file';inp.accept='.zip,application/zip';inp.className='hidden';root.append(inp);
  extra('作品ZIPを復元',()=>inp.click());inp.addEventListener('change',async()=>{const file=inp.files?.[0];inp.value='';if(!file)return;if(!confirm('現在のメディア作品と素材を、ZIPの内容で置き換えます。続けますか？'))return;try{const count=await this.importZip(file);log.textContent=`作品復元完了：${count}素材`;this.render(root,helpers);}catch(e){log.textContent='ZIP復元拒否：'+e.message;}});
  const importInput=element('input');importInput.type='file';importInput.accept='image/png,image/jpeg,image/gif,audio/wav,audio/mpeg,video/mp4,video/webm';importInput.multiple=true;importInput.className='hidden';root.append(importInput);
  extra('端末の素材を取り込む',()=>importInput.click());importInput.addEventListener('change',async()=>{const files=[...importInput.files];importInput.value='';try{const count=await this.importMedia(files);log.textContent=`端末素材 ${count}件を取り込みました。ZIPに統合できます。`;this.drawAssets(list,notify);}catch(e){log.textContent='取込制限：'+e.message;}});
  extra('全部削除',async()=>{if(!confirm('生成素材・取り込んだ素材をすべて削除しますか？'))return;await this.clear();this.drawAssets(list,notify);});
  const preview=makeCanvas(320,180);preview.style.cssText='max-width:100%;height:auto;border-radius:12px;border:1px solid var(--line);margin:8px 0';root.append(preview);
  extra('指定からプレビュー',()=>{try{const s=this.buildSpec(collect());renderMediaScene(preview.getContext('2d'),{...s,width:320,height:180},0);notify('プレビュー更新');}catch(e){log.textContent='プレビュー失敗：'+e.message;}});
  try{renderMediaScene(preview.getContext('2d'),{...this.buildSpec(),width:320,height:180},0);}catch{}
  root.append(element('h3','メディアWSP：生成制御重み 4×3'));
  root.append(element('p','テーマ特徴を入力とする4行×3列の重みを直接編集します。出力は描画密度・動作速度・音程へ反映されます。自然言語理解や学習済み画像生成ではありません。'));
  const ww=element('div');ww.className='buttons';root.append(ww);
  const rows=[['0','宇宙'],['1','海・夕焼'],['2','自然・天候'],['3','都市・抽象']];
  const cols=[['0','描画密度'],['1','動作速度'],['2','音程']];
  const mkSelect=(data)=>{const x=element('select');for(const [v,label] of data){const o=element('option',label);o.value=v;x.append(o);}return x;};
  const wr=mkSelect(rows),wc=mkSelect(cols),wv=element('input');wv.type='number';wv.min='-1.5';wv.max='1.5';wv.step='0.05';wv.value='0.5';wv.style.maxWidth='90px';ww.append(wr,wc,wv);
  const report=element('div');report.className='listentry';const reportRefresh=()=>{report.textContent=`直接設定した重み：${JSON.stringify(this.wsp.weights)}\n現在の画風への出力：${JSON.stringify(this.wsp.controls(this.buildSpec().theme))}`;};
  const edit=element('button','重みを設定'),undo=element('button','戻す');edit.className='btn primary';undo.className='btn';edit.addEventListener('click',()=>{try{this.wsp.edit(Number(wr.value),Number(wc.value),Number(wv.value));this.saveRecipe();reportRefresh();notify('生成制御の重みを更新');}catch(e){report.textContent=e.message;}});undo.addEventListener('click',()=>{this.wsp.rollback();this.saveRecipe();reportRefresh();});ww.append(edit,undo);root.append(report);reportRefresh();
  root.append(element('h3','作品に含まれる素材（最大16件／合計55MB）'));const list=element('div');root.append(list);this.drawAssets(list,notify);
 }
 drawAssets(root,notify){root.replaceChildren();const total=this.assets.reduce((s,a)=>s+a.bytes.length,0);root.append(element('p',`${this.assets.length}件・${(total/1024/1024).toFixed(2)} MiB。画像・音声・動画を同じプロジェクトとしてZIPに保存できます。`));
  for(const a of this.assets){const entry=element('div');entry.className='listentry';entry.append(element('b',`${a.kind}: ${a.name} (${Math.round(a.bytes.length/1024)}KB)`));const blob=new Blob([a.bytes],{type:a.type}),url=URL.createObjectURL(blob);const media=a.type.startsWith('image/')?element('img'):a.type.startsWith('audio/')?element('audio'):a.type.startsWith('video/')?element('video'):null;
   if(media){media.src=url;media.style.cssText='display:block;max-width:100%;max-height:190px;object-fit:contain;margin:8px 0;border-radius:8px';if(media.tagName!=='IMG'){media.controls=true;if(media.tagName==='VIDEO')media.muted=false;}entry.append(media);}else URL.revokeObjectURL(url);
   const buttons=element('div');buttons.className='buttons';const dl=element('button','保存');dl.className='btn';dl.addEventListener('click',()=>this.download(a));const del=element('button','削除');del.className='btn danger';del.addEventListener('click',async()=>{await this.remove(a.id);URL.revokeObjectURL(url);this.drawAssets(root,notify);});buttons.append(dl,del);entry.append(buttons);root.append(entry);
  }
 }
}
