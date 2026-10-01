(() => {
'use strict';
const parse=x=>{try{return typeof x==='string'?JSON.parse(x):x||{ok:false}}catch{return{ok:false,error:'invalid response'}}};
const KEY='FFC_A1111_VIDEO_CONFIG_V1';let config={mode:'offline',endpoint:'http://127.0.0.1:7860',frames:8,fps:4,width:512,height:512,steps:20,seed:888,denoising:.25,negative:'',motion:6,improve:true,maxAttempts:2};
try{Object.assign(config,JSON.parse(localStorage.getItem(KEY)||'{}'))}catch{}
let active=null,last={ok:false,status:'idle'};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function publish(job,patch){Object.assign(job,patch);last=job;const el=document.getElementById('a1111VideoStatus');if(el)el.textContent=job.status==='complete'?'A1111 MP4保存完了: '+job.name:job.status==='failed'?job.error:job.status==='cancelled'?'取り消しました。':'A1111動画生成: '+(job.framesWritten||0)+' / '+job.frameCount+' frames';try{window.dispatchEvent(new CustomEvent('ffc-video-export',{detail:status()}))}catch{}const share=document.getElementById('a1111VideoShare');if(share)share.disabled=job.status!=='complete';}
function status(){const {completion,cancelled,requestId,endpoint,...value}=last;return{...value}}
function configure(value){const next={...config,...value};next.maxAttempts=Math.max(1,Math.min(3,Math.floor(Number(next.maxAttempts)||2)));next.improve=next.improve!==false;if(!['offline','a1111'].includes(next.mode))throw new Error('invalid generation mode');const url=new URL(String(next.endpoint));if(!/^https?:$/.test(url.protocol)||url.username||url.password||url.search||url.hash)throw new Error('A1111接続先は認証情報を含まないHTTP(S) URLにしてください。');for(const k of ['frames','fps','width','height','steps','seed','motion'])next[k]=Number(next[k]);next.denoising=Number(next.denoising);if(!Number.isInteger(next.frames)||next.frames<2||next.frames>24||!Number.isInteger(next.fps)||next.fps<1||next.fps>30||next.frames/next.fps>15||!Number.isInteger(next.width)||!Number.isInteger(next.height)||next.width%8||next.height%8||next.width<64||next.height<64||next.width>1280||next.height>1280||next.width*next.height>921600||next.steps<1||next.steps>60||!Number.isFinite(next.seed)||!Number.isFinite(next.motion)||Math.abs(next.motion)>32||!Number.isFinite(next.denoising)||next.denoising<.05||next.denoising>.8)throw new Error('A1111動画設定が範囲外です。');next.endpoint=url.href.replace(/\/$/,'');config=next;localStorage.setItem(KEY,JSON.stringify(config));return{...config}}
async function request(job,path,body){
 const began=parse(AndroidA1111.start(job.endpoint,path,JSON.stringify(body)));if(!began.ok)throw new Error(began.error);job.requestId=began.requestId;
 const deadline=Date.now()+130000;
 while(Date.now()<deadline){if(job.cancelled)throw new Error('cancelled');const result=parse(AndroidA1111.poll(job.requestId));if(!result.ok||result.status==='failed')throw new Error(result.error||'A1111 failed');if(result.status==='complete'){if(!result.image)throw new Error('A1111 returned no frame');return result.image}await delay(150)}
 throw new Error('A1111 generation timed out');
}
function image(base64){return new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=()=>reject(new Error('A1111画像を読み込めませんでした。'));img.src='data:image/png;base64,'+base64})}
function start(args={}){
 if(active||window.FFCVideoExport?.status?.().status==='encoding')return{ok:false,status:'failed',error:'別の動画を処理中です。'};
 try{configure(args.config||{})}catch(e){return{ok:false,status:'failed',error:e.message}}
 if(!window.AndroidA1111||!window.AndroidVideo?.beginExternal)return{ok:false,status:'failed',error:'A1111対応APKが必要です。'};
 if(config.mode==='offline')return{ok:false,status:'failed',error:'オフライン設定です。A1111接続を設定で有効にしてください。'};
 const settings={...config},job={ok:true,status:'generating',provider:'a1111',endpoint:settings.endpoint,jobId:'a1111-'+Date.now(),frameCount:settings.frames,framesWritten:0,fps:settings.fps,width:settings.width,height:settings.height,durationMs:settings.frames*1000/settings.fps,prompt:String(args.prompt||''),cancelled:false};job.clientJobId=job.jobId;active=job;publish(job,{});
 job.completion=(async()=>{
  const canvas=document.createElement('canvas'),motion=document.createElement('canvas');canvas.width=motion.width=settings.width;canvas.height=motion.height=settings.height;const ctx=canvas.getContext('2d',{alpha:false}),mx=motion.getContext('2d',{alpha:false});let previous=null,nativeId=null,previousSample=null;const probe=document.createElement('canvas');probe.width=probe.height=32;const px=probe.getContext('2d',{willReadFrequently:true});job.quality={kind:'technical-luminance-continuity',scores:[],requests:0,retries:0};
  try{
   for(let index=0;index<settings.frames;index++){
    if(job.cancelled)throw new Error('cancelled');let init=null;
    if(previous){mx.fillStyle='#000';mx.fillRect(0,0,motion.width,motion.height);const shift=settings.motion;mx.drawImage(canvas,shift,0);if(shift>0)mx.drawImage(canvas,0,0,1,canvas.height,0,0,shift,canvas.height);if(shift<0)mx.drawImage(canvas,canvas.width-1,0,1,canvas.height,canvas.width+shift,0,-shift,canvas.height);init=motion.toDataURL('image/jpeg',.9)}
    const body={prompt:job.prompt,negative_prompt:settings.negative,seed:settings.seed,width:settings.width,height:settings.height,steps:settings.steps,cfg_scale:7,batch_size:1,n_iter:1,sampler_name:'Euler a',...(init?{init_images:[init],denoising_strength:settings.denoising}:{})};
    let best=null,bestQuality=null,priorScore=null;
    for(let attempt=0;attempt<(settings.improve?settings.maxAttempts:1);attempt++){
     const candidate={...body,seed:settings.seed+attempt,...(init&&attempt?{denoising_strength:Math.max(.05,settings.denoising*.6)}:{})};
     let encoded=await request(job,index?'/sdapi/v1/img2img':'/sdapi/v1/txt2img',candidate);job.quality.requests++;if(attempt)job.quality.retries++;
     const img=await image(encoded);if(job.cancelled)throw new Error('cancelled');ctx.drawImage(img,0,0,canvas.width,canvas.height);img.src='';
     let quality={score:100,pass:true,sample:null};if(window.FFCVideoQuality){px.drawImage(canvas,0,0,32,32);quality=FFCVideoQuality.evaluate(px.getImageData(0,0,32,32).data,previousSample)}
     if(!bestQuality||quality.score>bestQuality.score){best=encoded;bestQuality=quality}
     encoded=null;if(quality.pass||priorScore!==null&&quality.score<=priorScore)break;priorScore=quality.score;
    }
    const chosen=await image(best);best=null;ctx.drawImage(chosen,0,0,canvas.width,canvas.height);chosen.src='';previousSample=bestQuality.sample;job.quality.scores.push(bestQuality.score);previous=true;
    if(!nativeId){const begin=parse(AndroidVideo.beginExternal(settings.width,settings.height,settings.fps,settings.frames,'BANC888_A1111_video.mp4'));if(!begin.ok)throw new Error(begin.error);nativeId=begin.jobId;publish(job,{...begin,jobId:nativeId,status:'generating',provider:'a1111'});}
    let jpeg=canvas.toDataURL('image/jpeg',.86).split(',')[1];const wrote=parse(AndroidVideo.append(nativeId,index,jpeg));jpeg=null;if(!wrote.ok)throw new Error(wrote.error);publish(job,{framesWritten:index+1,status:'generating'});
   }
   const complete=parse(AndroidVideo.finish(nativeId));if(!complete.ok)throw new Error(complete.error);publish(job,{...complete,status:'complete',provider:'a1111'});
  }catch(e){try{if(job.requestId)AndroidA1111.cancel(job.requestId);if(nativeId)AndroidVideo.cancel(nativeId)}catch{}publish(job,{ok:false,status:job.cancelled?'cancelled':'failed',error:job.cancelled?null:e.message});}
  finally{probe.width=probe.height=0;canvas.width=motion.width=0;canvas.height=motion.height=0;active=null;}
  return status();
 })();
 return{ok:true,status:'generating',provider:'a1111',jobId:job.jobId,frameCount:job.frameCount,fps:job.fps,durationMs:job.durationMs};
}
function cancel(){if(!active)return{ok:false,error:'no A1111 video'};active.cancelled=true;if(active.requestId)AndroidA1111.cancel(active.requestId);if(!String(active.jobId).startsWith('a1111-'))AndroidVideo.cancel(active.jobId);return{ok:true}}
function mount(){const card=document.getElementById('a1111Card');if(!card)return;const box=document.createElement('div');box.innerHTML='<h3>生成方式</h3><label>方式 <select id="a1111VideoMode"><option value="offline">コネクトーム・端末内（オフライン）</option><option value="a1111">コネクトーム制御＋A1111（接続先が必要）</option></select></label><h3>A1111実画像 → MP4</h3><p>PCのA1111は --api --listen で起動し、PCのLANアドレスを指定します。127.0.0.1はこの端末自身です。</p><label>接続先 <input id="a1111VideoEndpoint" placeholder="http://192.168.1.10:7860"></label><div class="row"><label>フレーム <input id="a1111VideoFrames" type="number" min="2" max="24"></label><label>fps <input id="a1111VideoFps" type="number" min="1" max="30"></label></div><div class="row"><button id="a1111VideoGenerate">A1111で動画を生成</button><button id="a1111VideoCancel">取消</button><button id="a1111VideoShare" disabled>MP4共有</button></div><div id="a1111VideoStatus" role="status">接続先を設定して生成できます。</div>';card.append(box);for(const [id,k] of [['a1111VideoMode','mode'],['a1111VideoEndpoint','endpoint'],['a1111VideoFrames','frames'],['a1111VideoFps','fps']])document.getElementById(id).value=config[k];for(const [id,k] of [['a1111VideoMode','mode'],['a1111VideoEndpoint','endpoint'],['a1111VideoFrames','frames'],['a1111VideoFps','fps']])document.getElementById(id).onchange=()=>{try{configure({[k]:document.getElementById(id).value})}catch(e){document.getElementById('a1111VideoStatus').textContent=e.message}};document.getElementById('a1111VideoGenerate').onclick=()=>{const r=start({prompt:document.getElementById('a1Prompt')?.value||'',config:{endpoint:document.getElementById('a1111VideoEndpoint').value,frames:document.getElementById('a1111VideoFrames').value,fps:document.getElementById('a1111VideoFps').value}});if(!r.ok)document.getElementById('a1111VideoStatus').textContent=r.error};document.getElementById('a1111VideoCancel').onclick=cancel;document.getElementById('a1111VideoShare').onclick=()=>{if(last.status==='complete')AndroidVideo.share(last.jobId)}}
window.FFCA1111Video={start,cancel,status,configure,config:()=>({...config}),whenComplete:()=>active?active.completion:Promise.resolve(status())};window.addEventListener('pagehide',()=>{if(active)cancel()});mount();
const Agent=window.BANC888_FLY_AGENT;
if(Agent?.state?.tools){const tool=Agent.state.tools.get('o3.generate'),local=tool.handler;tool.handler=args=>{const explicit=/a1111|automatic1111/i.test(String(args.prompt||''));if(config.mode==='a1111'||explicit){const exported=start(args);return{provider:'a1111',durationMs:exported.durationMs,exported}}return local(args)};}
})();
