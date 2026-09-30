(() => {
'use strict';
if(window.__BANC_NATIVE_WRAPPED)return;

const token=String(window.__BANC_NATIVE_TOKEN||'');
const denied=()=>JSON.stringify({ok:false,error:'native bridge unavailable'});

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

const listeners=new Set(),pending=new Map();
let seq=0;
function dispatch(message){
  for(const fn of [...listeners]){try{fn(message)}catch(e){console.error('BancNative listener',e)}}
}
if(window.BancNative){
  window.BancNative.onmessage=event=>{
    let msg=event&&event.data;
    try{if(typeof msg==='string')msg=JSON.parse(msg)}catch{}
    if(msg&&msg.id&&pending.has(msg.id)){
      const x=pending.get(msg.id);
      pending.delete(msg.id);
      clearTimeout(x.timer);
      x.resolve(msg);
    }
    dispatch(msg);
  };
}
function post(type,data={},timeoutMs=3000){
  return new Promise(resolve=>{
    if(!window.BancNative||typeof window.BancNative.postMessage!=='function'){
      resolve({ok:false,fallback:true,type});
      return;
    }
    const id='bn-'+Date.now().toString(36)+'-'+(++seq).toString(36);
    const timer=setTimeout(()=>{
      pending.delete(id);
      resolve({ok:false,timeout:true,type});
    },timeoutMs);
    pending.set(id,{resolve,timer});
    try{
      window.BancNative.postMessage(JSON.stringify({id,type,data,ts:Date.now(),href:location.href}));
    }catch(error){
      clearTimeout(timer);
      pending.delete(id);
      resolve({ok:false,error:String(error),type});
    }
  });
}
window.AndroidRuntime={
  post,
  status:()=>post('runtime.status'),
  reload:()=>post('runtime.reload'),
  useBundled:()=>post('runtime.useBundled'),
  rollback:()=>post('runtime.rollback'),
  ping:()=>post('ping'),
  subscribe(fn){
    if(typeof fn==='function')listeners.add(fn);
    return()=>listeners.delete(fn);
  }
};

window.__BANC_NATIVE_WRAPPED=true;
})();
