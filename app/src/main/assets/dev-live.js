(() => {
'use strict';
if(window.__BANC888_DEV_LIVE_UI)return;window.__BANC888_DEV_LIVE_UI=true;
function parse(s){try{return JSON.parse(String(s||''))}catch{return null}}
function status(){return window.AndroidDev?parse(AndroidDev.status()):null}
function diagnostics(){return window.AndroidDiagnostics?parse(AndroidDiagnostics.status()):null}
function render(){
 if(!window.AndroidDev)return;
 let box=document.getElementById('bancDevLiveBox');
 if(!box){
  box=document.createElement('div');box.id='bancDevLiveBox';
  box.style.cssText='display:flex;gap:6px;align-items:center;flex-wrap:wrap;margin:8px 0;padding:7px 9px;border:1px dashed #355a82;border-radius:10px;font-size:11px';
  box.innerHTML='<b>DEV LIVE</b><span id="bancDevLiveState"></span><button id="bancDevSync">🔄 Live更新</button><button id="bancDevReload">↻ 再読込</button><button id="bancDevRollback">↩ Rollback</button><button id="bancDevBundled">📦 APK内蔵</button><button id="bancDevDiag">診断</button><pre id="bancDevDiagOut" style="display:none;width:100%;max-height:200px;overflow:auto;white-space:pre-wrap"></pre>';
  const settings=document.getElementById('uiSettingsBody');
  const host=settings||document.getElementById('ffcThreadHub')||document.getElementById('flyAgentCard')||document.body;
  if(settings){const d=document.createElement('details');d.className='agentSubDetails';d.id='bancDevLiveDetails';d.innerHTML='<summary>Dev Live / Native診断</summary><div class="agentSubBody"></div>';d.querySelector('.agentSubBody').append(box);settings.prepend(d)}else host.insertBefore(box,host.firstChild);
  document.getElementById('bancDevSync').onclick=()=>{const s=document.getElementById('bancDevLiveState');if(s)s.textContent='同期中…';AndroidDev.syncAndReload()};
  document.getElementById('bancDevReload').onclick=()=>AndroidDev.reload();
  document.getElementById('bancDevRollback').onclick=()=>AndroidDev.rollbackAndReload();
  document.getElementById('bancDevBundled').onclick=()=>AndroidDev.useBundledAndReload();
  document.getElementById('bancDevDiag').onclick=()=>{const out=document.getElementById('bancDevDiagOut'),d=diagnostics();if(out){out.style.display='block';out.textContent=JSON.stringify(d,null,2)}};
 }
 const s=status(),label=document.getElementById('bancDevLiveState'),rb=document.getElementById('bancDevRollback');
 if(label&&s){
   label.textContent=s.live?('LIVE · '+s.ref+' · '+s.bundle):('BUNDLED · '+s.ref);
   if(s.lastError)label.textContent+=' · ERROR '+s.lastError;
 }
 if(rb&&s)rb.disabled=!s.rollbackAvailable;
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',render,{once:true});else setTimeout(render,0);
})();