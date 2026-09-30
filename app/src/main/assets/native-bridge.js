(() => {
'use strict';
if(window.__BANC_NATIVE_WRAPPED)return;

const token=String(window.__BANC_NATIVE_TOKEN||'');
const epoch=Math.max(0,Number(window.__BANC_NATIVE_EPOCH||0)||0);
const denied=()=>JSON.stringify({ok:false,error:'native bridge unavailable',epoch});

window.AndroidVoice={
 status:()=>window.__BancVoice?__BancVoice.status(token):denied(),
 requestMicPermission:()=>window.__BancVoice?__BancVoice.requestMicPermission(token):false,
 startListening:(language)=>window.__BancVoice?__BancVoice.startListening(token,String(language||'ja-JP')):false,
 stopListening:()=>window.__BancVoice?__BancVoice.stopListening(token):false,
 speak:(text,language,rate,pitch)=>window.__BancVoice?__BancVoice.speak(token,String(text||''),String(language||'ja-JP'),Number(rate||1),Number(pitch||1)):false
};
window.AndroidFiles={
 createDocx:(title,body,filename)=>window.__BancFiles?__BancFiles.createDocx(token,String(title||''),String(body||''),String(filename||'')):denied(),
 shareText:(text,filename,mime)=>window.__BancFiles?__BancFiles.shareText(token,String(text||''),String(filename||''),String(mime||'text/plain')):denied()
};
window.AndroidResearch={
 searchWikipedia:(query,limit)=>window.__BancResearch?__BancResearch.searchWikipedia(token,String(query||''),Number(limit||3)):denied(),
 searchCrossref:(query,limit)=>window.__BancResearch?__BancResearch.searchCrossref(token,String(query||''),Number(limit||3)):denied()
};
window.AndroidDev={
 status:()=>window.__BancDev?__BancDev.status(token):denied(),
 syncAndReload:()=>window.__BancDev?__BancDev.syncAndReload(token):false,
 reload:()=>window.__BancDev?__BancDev.reload(token):false,
 useBundledAndReload:()=>window.__BancDev?__BancDev.useBundledAndReload(token):false,
 rollbackAndReload:()=>window.__BancDev?__BancDev.rollbackAndReload(token):false
};
window.AndroidDiagnostics={
 status:()=>window.__BancDiagnostics?__BancDiagnostics.status(token):denied(),
 simulateVoiceResult:(text,confidence)=>window.__BancDiagnostics?__BancDiagnostics.simulateVoiceResult(token,String(text||''),Number(confidence||0)):false
};

const listeners=new Set();
const pending=new Map();
let seq=0;

function parse(raw){
  let msg=raw;
  try{if(typeof msg==='string')msg=JSON.parse(msg)}catch{return null}
  return msg&&typeof msg==='object'?msg:null;
}
function currentEpoch(msg){
  if(!msg||msg.epoch===undefined||msg.epoch===null)return null;
  const n=Number(msg.epoch);
  return Number.isFinite(n)?n:null;
}
function accepts(msg){
  const incoming=currentEpoch(msg);
  return !(epoch>0&&incoming!==null&&incoming!==epoch);
}
function dispatch(message){
  if(!message||!accepts(message))return false;
  for(const fn of [...listeners]){
    try{fn(message)}catch(e){console.error('BancNative listener',e)}
  }
  return true;
}
function receive(raw){
  const msg=parse(raw);
  if(!msg||!accepts(msg))return false;
  if(msg.id&&pending.has(msg.id)){
    const x=pending.get(msg.id);
    pending.delete(msg.id);
    clearTimeout(x.timer);
    x.resolve(msg);
  }
  dispatch(msg);
  return true;
}
if(window.BancNative){
  window.BancNative.onmessage=event=>receive(event&&event.data);
}

function post(type,data={},timeoutMs=3000){
  return new Promise(resolve=>{
    if(!window.BancNative||typeof window.BancNative.postMessage!=='function'){
      resolve({ok:false,fallback:true,type,epoch});
      return;
    }
    const id='bn-'+Date.now().toString(36)+'-'+(++seq).toString(36);
    const timer=setTimeout(()=>{
      pending.delete(id);
      resolve({ok:false,timeout:true,type,epoch});
    },Math.max(250,Number(timeoutMs)||3000));
    pending.set(id,{resolve,timer});
    try{
      window.BancNative.postMessage(JSON.stringify({
        id,
        type:String(type||''),
        data:data&&typeof data==='object'?data:{value:data},
        ts:Date.now(),
        href:location.href,
        epoch
      }));
    }catch(error){
      clearTimeout(timer);
      pending.delete(id);
      resolve({ok:false,error:String(error),type,epoch});
    }
  });
}
function nativeDispatch(raw){
  const msg=parse(raw);
  if(!msg)return false;
  if(msg.nativePush!==true)msg.nativePush=true;
  return receive(msg);
}
function transport(){
  return{
    mode:window.BancNative&&typeof window.BancNative.postMessage==='function'?'webmessage':'legacy',
    epoch,
    pending:pending.size,
    origin:location.origin
  };
}

window.AndroidRuntime={
  post,
  request:post,
  status:()=>post('runtime.status'),
  capabilities:()=>post('runtime.capabilities'),
  reload:()=>post('runtime.reload'),
  useBundled:()=>post('runtime.useBundled'),
  rollback:()=>post('runtime.rollback'),
  ping:()=>post('ping'),
  transport,
  subscribe(fn){
    if(typeof fn==='function')listeners.add(fn);
    return()=>listeners.delete(fn);
  },
  __nativeDispatch:nativeDispatch
};

window.__BANC_NATIVE_WRAPPED=true;
})();