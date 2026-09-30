(() => {
'use strict';
if(window.__BANC888_DEV_LIVE_UI)return;window.__BANC888_DEV_LIVE_UI=true;
function status(){try{return window.AndroidDev?JSON.parse(AndroidDev.status()):null}catch{return null}}
function render(){
 if(!window.AndroidDev)return;
 let box=document.getElementById('bancDevLiveBox');
 if(!box){
  box=document.createElement('div');box.id='bancDevLiveBox';
  box.style.cssText='display:flex;gap:6px;align-items:center;flex-wrap:wrap;margin:8px 0;padding:7px 9px;border:1px dashed #355a82;border-radius:10px;font-size:11px';
  box.innerHTML='<b>DEV LIVE</b><span id="bancDevLiveState"></span><button id="bancDevSync">🔄 Live更新</button><button id="bancDevReload">↻ 再読込</button><button id="bancDevBundled">📦 APK内蔵</button>';
  const host=document.getElementById('ffcThreadHub')||document.getElementById('flyAgentCard')||document.body;
  host.insertBefore(box,host.firstChild);
  document.getElementById('bancDevSync').onclick=()=>{const s=document.getElementById('bancDevLiveState');if(s)s.textContent='同期中…';AndroidDev.syncAndReload()};
  document.getElementById('bancDevReload').onclick=()=>AndroidDev.reload();
  document.getElementById('bancDevBundled').onclick=()=>AndroidDev.useBundledAndReload();
 }
 const s=status(),label=document.getElementById('bancDevLiveState');
 if(label&&s)label.textContent=s.live?('LIVE · '+s.ref+' · '+s.bundle):('BUNDLED · '+s.ref);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',render,{once:true});else setTimeout(render,0);
})();