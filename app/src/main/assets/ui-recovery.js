(() => {
'use strict';
// Autonomous, bounded recovery for Android WebView. No page reloads,
// no deletion of user state and no fabricated generation successes.
if(window.FFC_UI_RECOVERY)return;
const ID='bancRescueUI',KEY='BANC_RESCUE_DRAFT_V1',LOG='BANC_RESCUE_LOG_V1';
const modes=[
 {id:'chat',name:'会話',prompt:''},
 {id:'code',name:'コード',prompt:'PythonでCSVを読み込み、列ごとに集計するコードを書いて。'},
 {id:'image',name:'画像',prompt:'青空の下で走る猫の画像を生成して。'},
 {id:'video',name:'動画',prompt:'猫が走る短い動画を生成して。'}
];
const get=id=>document.getElementById(id);
let mode='chat',inflight=false,attempting=false,attempts=0,recovered=0,lastError='',timer=null,lastStatus='';
const node=(tag,cls,value)=>{
 const e=document.createElement(tag);
 if(cls)e.className=cls;
 if(value!==undefined)e.textContent=value;
 return e;
};
function status(message,isError=false){
 lastStatus=String(message);
 const e=get('bancRescueStatus');
 if(e){e.textContent=String(message);e.dataset.error=String(!!isError)}
}
function healthy(){
 const hub=get('ffcThreadHub'),input=get('ffcThreadText'),send=get('ffcThreadSend'),log=get('ffcThreadLog');
 if(!hub||!input||!send||!log||typeof window.FFC_THREADS?.route!=='function')return false;
 if(!hub.isConnected||!input.isConnected||!send.isConnected||typeof send.onclick!=='function')return false;
 const a=input.getBoundingClientRect(),b=send.getBoundingClientRect();
 return a.width>=90&&a.height>=30&&b.width>=44&&b.height>=44&&a.top<innerHeight&&b.top<innerHeight
  &&a.bottom>0&&b.bottom>0;
}
function fixExisting(){
 if(typeof window.FFC_THREADS?.repairUI!=='function')return false;
 try{
  window.FFC_THREADS.repairUI();
  window.FFCDadsUI?.mount?.();
  return healthy();
 }catch(e){lastError='repairUI: '+String(e?.message||e).slice(0,150);return false}
}
function assetURL(name){
 const u=new URL(name,location.href);
 if(u.origin!==location.origin||!/^\/(assets|live)\//.test(u.pathname))throw Error('untrusted asset URL');
 return u.href;
}
function loadAsset(name){
 return new Promise((resolve,reject)=>{
  let src;try{src=assetURL(name)}catch(e){reject(e);return}
  const s=node('script');s.src=src;s.async=false;
  const timeout=setTimeout(()=>{s.remove();reject(Error('asset timeout: '+name))},5000);
  s.onload=()=>{clearTimeout(timeout);s.remove();resolve(true)};
  s.onerror=()=>{clearTimeout(timeout);s.remove();reject(Error('asset missing: '+name))};
  document.head.append(s);
 });
}
async function recover(){
 if(attempting||attempts>=3||healthy())return false;
 attempting=true;attempts++;
 try{
  if(!window.FFCThreadCore)await loadAsset('thread-router-core.js');
  if(!window.FFCConversationOutput)await loadAsset('conversation-output-core.js');
  if(!window.FFC_THREADS)await loadAsset('thread-router.js');
  if(window.FFC_THREADS&&!get('ffcThreadHub'))fixExisting();
  await new Promise(ok=>setTimeout(ok,320));
  if(healthy()||fixExisting()){recovered++;hide();return true}
  lastError='normal thread UI did not mount (attempt '+attempts+')';
 }catch(e){lastError=String(e?.message||e).slice(0,220)}
 finally{attempting=false}
 return false;
}
function diagnose(){
 const present=id=>!!get(id);
 const deps={
  threadCore:!!window.FFCThreadCore,
  threadRouter:typeof window.FFC_THREADS?.route==='function',
  normalInput:present('ffcThreadText'),
  normalSend:present('ffcThreadSend'),
  proxy:typeof window.FFC_PROXY_AGENT?.cognitionRequest==='function'
    ||typeof window.FFC_PROXY_AGENT?.executeAsync==='function',
  localChat:typeof window.BANC888_FLY_AGENT?.execute==='function'
 };
 const missing=Object.entries(deps).filter(([,ready])=>!ready).map(([key])=>key);
 return '標準UI='+ (healthy()?'正常':'復旧未完了')+
  ' / AI実行先='+ (deps.proxy?'接続':'未接続')+
  ' / 未準備='+ (missing.join(', ')||'なし')+
  ' / 復旧試行='+attempts+'/3'+
  (lastError?' / 復旧エラー='+lastError:'');
}
function loadEntries(){
 try{
  const a=JSON.parse(localStorage.getItem(LOG)||'[]');
  return Array.isArray(a)?a.filter(x=>x&&typeof x.text==='string'&&['user','assistant','system'].includes(x.role)).slice(-24):[];
 }catch{return[]}
}
let entries=loadEntries();
function persist(){
 try{localStorage.setItem(LOG,JSON.stringify(entries.slice(-24)))}catch{}
}
function put(role,text){
 const t=String(text||'').slice(0,24000);
 entries.push({role,text:t,time:Date.now()});entries=entries.slice(-24);persist();
 const area=get('bancRescueLog');
 if(!area)return;
 const item=node('div','bancRescueMessage bancRescue-'+role);
 item.append(node('strong','',role==='user'?'自分':role==='assistant'?'BANC888':'システム'),node('p','',t));
 area.appendChild(item);
 while(area.children.length>30)area.firstElementChild.remove();
 area.scrollTop=area.scrollHeight;
}
function saveDraft(s){
 try{localStorage.setItem(KEY,String(s||'').slice(0,12000))}catch{}
}
function setMode(next){
 const info=modes.find(x=>x.id===next),box=get('bancRescueText');
 if(!info||!box)return false;
 const old=modes.find(x=>x.id===mode);
 const isTemplate=!box.value.trim()||box.value===old?.prompt;
 mode=next;
 for(const b of get('bancRescueModes')?.querySelectorAll('button')||[]){
  b.setAttribute('aria-pressed',String(b.dataset.mode===mode));
 }
 if(isTemplate){box.value=info.prompt;saveDraft(box.value)}
 status(info.name+'を選択しました。入力を編集し、送信してください。');
 box.focus({preventScroll:true});
 return true;
}
function normalized(s){
 if(mode==='code'&&!/コード|プログラム|Python|Java|実装|coding|code/i.test(s))return'コードを生成して。条件: '+s;
 if(mode==='image'&&!/画像|イラスト|描|image/i.test(s))return'画像を生成して。内容: '+s;
 if(mode==='video'&&!/動画|映像|アニメ|video/i.test(s))return'動画を生成して。内容: '+s;
 return s;
}
async function execute(text){
 const proxy=window.FFC_PROXY_AGENT;
 if(proxy?.cognitionRequest)return proxy.cognitionRequest(text,{threadCode:'RESCUE'});
 if(proxy?.executeAsync)return proxy.executeAsync(text,{threadCode:'RESCUE'});
 // The only direct fallback is a read-only chat composition.
 // Code, image, video and device operations require the proxy policy gate.
 if(mode==='chat'&&window.BANC888_FLY_AGENT?.execute){
  const r=await Promise.resolve(window.BANC888_FLY_AGENT.execute('chat.compose',{goal:text,threadCode:'RESCUE'}));
  const body=String(r?.value?.reply||'');
  if(r?.ok&&body.trim())return{status:'done',finalText:body};
 }
 return{status:'blocked',finalText:'生成エンジンが利用できません。'+diagnose()+'。自動復旧を再試行してください。'};
}
async function send(){
 if(inflight)return false;
 const box=get('bancRescueText');if(!box)return false;
 const raw=box.value.trim();
 if(!raw){status('依頼内容を入力してください。',true);box.focus();return false}
 if(raw.length>12000){status('入力を12,000文字以内にしてください。',true);return false}
 inflight=true;const button=get('bancRescueSend');if(button)button.disabled=true;
 const prompt=normalized(raw);
 put('user',prompt);status('実行中です。結果を確認しています。');
 try{
  let deadline=null,slowNotice=null;
  const timeoutMs=mode==='video'?120000:mode==='image'?60000:45000;
  const timeout=new Promise((_,reject)=>{
   deadline=setTimeout(()=>reject(Error('AI処理が制限時間を超えました。'+diagnose())),timeoutMs);
  });
  slowNotice=setTimeout(()=>status('AI処理を継続中です。応答がない場合は診断を開いてください。'),8000);
  let result;
  try{result=await Promise.race([Promise.resolve().then(()=>execute(prompt)),timeout])}
  finally{if(deadline!==null)clearTimeout(deadline);if(slowNotice!==null)clearTimeout(slowNotice);}
  if(!result||!['done','completed','blocked','failed'].includes(result.status))
   throw Error('AI実行先が正常な結果状態を返しませんでした。');
  const ok=['done','completed'].includes(result?.status);
  const msg=String(result?.finalText||result?.error||'結果が返りませんでした。');
  put('assistant',(ok?'':'未完了: ')+msg);
  status(ok?'結果を表示しました。次の依頼を入力できます。':'未完了です。入力文は保持しています。',!ok);
  if(ok&&box.value.trim()===raw){box.value='';saveDraft('')}
  if(ok&&mode==='video'&&window.FFCVideoExport?.share){
   const video=result?.outputs?.find(x=>x.mode==='video')?.value?.exported;
   if(video?.status==='complete'&&video.ok){
    const share=node('button','','MP4を共有');share.type='button';
    share.onclick=()=>{try{window.FFCVideoExport.share()}catch(e){status(String(e?.message||e),true)}};
    get('bancRescueLog')?.appendChild(share);
   }
  }
  if(ok&&mode==='image'){
   const canvas=get('o2xCanvas');
   if(typeof HTMLCanvasElement!=='undefined'&&canvas instanceof HTMLCanvasElement&&canvas.width>0){
    try{const image=node('img');image.alt='生成画像';image.src=canvas.toDataURL('image/png');
     image.style.cssText='display:block;max-width:100%;height:auto';get('bancRescueLog')?.appendChild(image)}catch{}
   }
  }
  return ok;
 }catch(e){put('system','実行エラー: '+String(e?.message||e));status('実行に失敗しました。入力文は保持しています。',true);return false}
 finally{inflight=false;if(button)button.disabled=false}
}
function mount(){
 if(get(ID)){get(ID).hidden=false;return}
 // Critical styling is inline; the full stylesheet is a bundled optional asset.
 const critical=node('style');critical.id='bancRescueCritical';
 critical.textContent='#bancRescueUI{position:fixed;inset:0;z-index:35;display:flex;flex-direction:column;background:#f8faff;color:#14283e;max-height:100dvh;padding:12px;overflow:hidden}#bancRescueUI[hidden]{display:none!important}#bancRescueLog{flex:1;min-height:0;overflow:auto}#bancRescueText{width:100%;min-height:64px}#bancRescueUI button{min-height:48px}';
 if(!get('bancRescueCritical'))document.head.appendChild(critical);
 if(!get('bancRescueCSS')){
  const link=node('link');link.id='bancRescueCSS';link.rel='stylesheet';
  try{link.href=assetURL('ui-recovery.css')}catch{}
  document.head.appendChild(link);
 }
 const panel=node('section');panel.id=ID;panel.setAttribute('aria-label','BANC888 自動復旧操作画面');
 const head=node('header','bancRescueHeader');
 head.append(node('strong','','BANC888 操作画面'),node('span','','簡易操作モード'));
 const setting=node('button','','設定');setting.type='button';
 setting.onclick=()=>get('uiGearButton')?.click();head.appendChild(setting);
 const nav=node('nav','bancRescueModes');nav.id='bancRescueModes';nav.setAttribute('aria-label','生成機能');
 for(const item of modes){
  const button=node('button','',item.name);button.type='button';button.id='bancRescueMode'+item.id[0].toUpperCase()+item.id.slice(1);button.dataset.mode=item.id;
  button.setAttribute('aria-pressed',String(mode===item.id));
  button.onclick=()=>setMode(item.id);nav.appendChild(button);
 }
 const log=node('div');log.id='bancRescueLog';log.setAttribute('role','log');log.setAttribute('aria-live','polite');
 const form=node('div','bancRescueComposer');
 const label=node('label','','依頼内容');label.setAttribute('for','bancRescueText');
 const box=node('textarea');box.id='bancRescueText';box.rows=3;box.placeholder='何を作りたいですか？';
 box.oninput=()=>saveDraft(box.value);
 box.onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();send()}};
 const row=node('div','bancRescueAction');
 const retry=node('button','','自動復旧を再試行');retry.type='button';retry.id='bancRescueRetry';
 retry.onclick=()=>{attempts=0;status('復旧を再試行しています。'+diagnose());maintain(true)};
 const diagnostic=node('button','','診断');diagnostic.type='button';diagnostic.id='bancRescueDiagnose';
 diagnostic.onclick=()=>{put('system',diagnose());status('診断結果を上の履歴欄に表示しました。')};
 const sendBtn=node('button','','送信');sendBtn.type='button';sendBtn.id='bancRescueSend';sendBtn.onclick=send;
 row.append(retry,diagnostic,sendBtn);
 const state=node('p');state.id='bancRescueStatus';state.setAttribute('role','status');
 state.setAttribute('aria-live','polite');
 form.append(label,box,row,state);panel.append(head,nav,log,form);document.body.appendChild(panel);
 for(const e of entries){
  const msg=node('div','bancRescueMessage bancRescue-'+e.role);
  msg.append(node('strong','',e.role==='user'?'自分':e.role==='assistant'?'BANC888':'システム'),node('p','',e.text));
  log.append(msg);
 }
 try{box.value=localStorage.getItem(KEY)||''}catch{}
 status('標準画面を自動復旧中です。'+diagnose());
}
function hide(){const p=get(ID);if(p)p.hidden=true}
function maintain(force=false){
 if(healthy()){hide();return true}
 if(fixExisting()){recovered++;hide();return true}
 if(get('uiSettingsDrawer')?.classList.contains('open'))return false;
 mount();
 if((force||attempts<3)&&!attempting)recover().then(ok=>{
  if(ok)hide();
  else status('標準UIが利用できないため簡易モードで動作中です。',true);
 });
 return false;
}
window.FFC_UI_RECOVERY={
 version:'1.0',repair:()=>maintain(true),healthy,send,setMode,
 diagnostics:()=>({healthy:healthy(),fallbackVisible:!!get(ID)&&!get(ID).hidden,
 attempts,recovered,lastError,lastStatus,threads:!!window.FFC_THREADS,proxy:!!window.FFC_PROXY_AGENT})
};
const begin=()=>{
 maintain();
 timer=setInterval(maintain,3500);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)maintain()});
 window.addEventListener('pagehide',()=>{if(timer)clearInterval(timer)});
};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',begin,{once:true});
else begin();
})();