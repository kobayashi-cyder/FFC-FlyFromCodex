(() => {
'use strict';
// Native H.264/MP4 export, one acknowledged JPEG frame at a time.
const parse=raw=>{try{return typeof raw==='string'?JSON.parse(raw):raw||{ok:false}}catch{return{ok:false,error:'invalid encoder response'}}};
let active=null,last={ok:false,status:'idle'},source=window.FFCVideoSceneSource||null,sequence=0;
const yieldFrame=()=>new Promise(resolve=>setTimeout(resolve,0));
function publish(job,patch){Object.assign(job,patch);last=job;try{window.dispatchEvent(new CustomEvent('ffc-video-export',{detail:status()}))}catch{}const el=document.getElementById('videoMp4Status');if(el)el.textContent=job.status==='complete'?'MP4保存完了: '+job.name+' / '+job.bytes+' bytes':job.status==='encoding'?'MP4保存中: '+job.framesWritten+' / '+job.frameCount+' frames':job.status==='cancelled'?'MP4保存を取り消しました。':job.error||'MP4を保存できます。';const share=document.getElementById('videoMp4Share');if(share)share.disabled=job.status!=='complete';const cancel=document.getElementById('videoMp4Cancel');if(cancel)cancel.disabled=job.status!=='encoding';const save=document.getElementById('videoMp4Save');if(save)save.disabled=job.status==='encoding';}
function fail(error){return{ok:false,status:'failed',error:String(error||'MP4 export failed')}}
function start(options={}){
 if(active)return fail('MP4保存中です。完了を待つか取り消してください。');
 if(!window.AndroidVideo?.begin)return fail('MP4保存には対応するAndroid APKが必要です。');
 let spec;try{spec=source&&source()}catch(e){return fail(e.message)}
 if(!spec||typeof spec.render!=='function')return fail('動画シーンがありません。動画を生成してください。');
 const fps=Math.round(Number(options.fps??24)),durationMs=Number(options.durationMs??spec.durationMs),width=Math.round(Number(spec.width)),height=Math.round(Number(spec.height));
 if(!Number.isFinite(durationMs)||durationMs<100||durationMs>15000||!Number.isInteger(fps)||fps<1||fps>30||width<16||height<16||width%2||height%2||width*height>921600)return fail('動画設定が保存範囲を超えています。');
 const frameCount=Math.ceil(durationMs*fps/1000);if(frameCount>450)return fail('動画は450フレーム以下にしてください。');
 const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;const ctx=canvas.getContext('2d',{alpha:false});if(!ctx)return fail('frame canvas unavailable');
 let began;try{began=parse(AndroidVideo.begin(width,height,fps,frameCount,String(options.filename||'BANC888_video.mp4')))}catch(e){return fail(e.message)}if(!began.ok)return fail(began.error);
 const job={...began,ok:true,status:'encoding',jobId:began.jobId,fps,width,height,frameCount,framesWritten:0,durationMs:frameCount*1000/fps,requestId:++sequence,cancelled:false};active=job;publish(job,{});
 // A single frame remains live; native append acknowledges it before the next is rendered.
 job.completion=(async()=>{
  try{
   for(let index=0;index<frameCount;index++){
    await yieldFrame();if(job.cancelled)throw new Error('cancelled');
    spec.render(index/fps*1000/durationMs,ctx);let jpeg=canvas.toDataURL('image/jpeg',.86).split(',')[1];
    if(!jpeg||jpeg.length>2097152)throw new Error('frame payload exceeds limit');
    const result=parse(AndroidVideo.append(job.jobId,index,jpeg));jpeg=null;if(!result.ok)throw new Error(result.error||'frame encoding failed');
    publish(job,{framesWritten:index+1});
   }
   if(job.cancelled)throw new Error('cancelled');
   const result=parse(AndroidVideo.finish(job.jobId));if(!result.ok)throw new Error(result.error||'MP4 finalization failed');
   publish(job,{...result,status:'complete',ok:true});
  }catch(e){try{AndroidVideo.cancel(job.jobId)}catch{}publish(job,{ok:false,status:job.cancelled?'cancelled':'failed',error:job.cancelled?null:String(e.message||e)});}
  finally{canvas.width=0;canvas.height=0;active=null;}
  return status();
 })();
 return{ok:true,status:'encoding',jobId:job.jobId,name:began.name||options.filename||'BANC888_video.mp4',frameCount,fps,width,height,durationMs:job.durationMs};
}
function status(){const {completion,cancelled,...value}=last;return{...value}}
function cancel(){if(!active)return{ok:false,error:'no active video export'};active.cancelled=true;return parse(AndroidVideo.cancel(active.jobId))}
function share(){if(last.status!=='complete')return fail('完成したMP4がありません。');return parse(AndroidVideo.share(last.jobId))}
function mount(){
 const card=document.getElementById('o23Card');if(!card||document.getElementById('videoMp4Status'))return;
 const row=document.createElement('div');row.className='row';
 for(const [id,text,handler] of [['videoMp4Save','MP4保存',()=>{const r=start();if(!r.ok){const el=document.getElementById('videoMp4Status');if(el)el.textContent=r.error}}],['videoMp4Cancel','保存取消',cancel],['videoMp4Share','MP4共有',share]]){const b=document.createElement('button');b.id=id;b.textContent=text;b.disabled=id!=='videoMp4Save';b.onclick=handler;row.appendChild(b)}
 const el=document.createElement('div');el.id='videoMp4Status';el.setAttribute('role','status');el.setAttribute('aria-live','polite');el.textContent='動画を生成してMP4保存できます。';card.appendChild(row);card.appendChild(el);
}
window.FFCVideoExport={start,status,cancel,share,setSource:fn=>{source=fn},whenComplete:()=>active?active.completion:Promise.resolve(status())};
window.addEventListener?.('pagehide',()=>{if(active)cancel()});mount();
const Agent=window.BANC888_FLY_AGENT;
if(Agent?.state){for(const [name,handler] of [['video.export.status',status],['video.export.cancel',cancel],['video.export.share',share]]){Agent.state.tools.set(name,{name,description:name,capability:'o3.write',sideEffect:name.endsWith('status')?'none':'local',handler});}}
})();
