(() => {
'use strict';
if(window.__BANC888_WEBVIEW_RUNTIME_V7__)return;
window.__BANC888_WEBVIEW_RUNTIME_V7__=true;

const VERSION=7;
const bootAt=performance.now();
const state={
  ready:false,
  visible:document.visibilityState,
  online:navigator.onLine,
  jsErrors:0,
  longTasks:0,
  lastNativeAt:0,
  native:null,
  errors:[],
  metrics:{domContentLoaded:0,load:0,firstPaint:0,firstContentfulPaint:0}
};
let badge=null,panel=null,readySent=false;

function snapshot(){
  return{
    version:VERSION,
    ready:state.ready,
    visible:state.visible,
    online:state.online,
    uptimeMs:Math.round(performance.now()-bootAt),
    jsErrors:state.jsErrors,
    longTasks:state.longTasks,
    lastNativeAt:state.lastNativeAt,
    native:state.native,
    metrics:{...state.metrics},
    errors:state.errors.slice(-8),
    bridges:{
      voice:!!window.AndroidVoice,
      files:!!window.AndroidFiles,
      research:!!window.AndroidResearch,
      dev:!!window.AndroidDev,
      runtime:!!window.AndroidRuntime
    }
  };
}
function send(type,data){
  if(!window.AndroidRuntime)return Promise.resolve({ok:false,fallback:true});
  return AndroidRuntime.post(type,data||{}).then(r=>{
    if(r&&r.status){
      state.native=r.status;
      state.lastNativeAt=Date.now();
    }
    render();
    return r;
  });
}
function recordError(kind,value){
  state.jsErrors++;
  let text='';
  try{text=typeof value==='string'?value:JSON.stringify(value)}
  catch{text=String(value)}
  state.errors.push({kind,text:text.slice(0,900),ts:Date.now()});
  if(state.errors.length>20)state.errors.shift();
  send('runtime.error',{kind,text:text.slice(0,900)});
  render();
}
function capturePaints(){
  try{
    for(const e of performance.getEntriesByType('paint')){
      if(e.name==='first-paint')state.metrics.firstPaint=e.startTime;
      if(e.name==='first-contentful-paint')state.metrics.firstContentfulPaint=e.startTime;
    }
  }catch{}
}
function saveScroll(){
  try{sessionStorage.setItem('banc888:webview:scrollY',String(scrollY||0))}catch{}
}
function restoreScroll(){
  try{
    const y=Number(sessionStorage.getItem('banc888:webview:scrollY')||0);
    if(Number.isFinite(y)&&y>0)requestAnimationFrame(()=>scrollTo(0,y));
  }catch{}
}
function ensureUi(){
  if(badge||!document.body)return;
  const host=document.querySelector('header h1')||document.body;
  badge=document.createElement('button');
  badge.type='button';
  badge.id='bancWebRuntimeBadge';
  badge.textContent='WV …';
  badge.style.cssText='margin-left:8px;padding:3px 8px;border-radius:999px;font-size:11px;border:1px solid #8a7a46;background:#0b1528;color:#cfe0ff;cursor:pointer';
  badge.onclick=()=>{
    ensurePanel();
    panel.hidden=!panel.hidden;
    render();
  };
  host.appendChild(badge);
}
function ensurePanel(){
  if(panel)return;
  panel=document.createElement('div');
  panel.hidden=true;
  panel.id='bancWebRuntimePanel';
  panel.style.cssText='position:fixed;right:10px;bottom:10px;z-index:2147483000;width:min(430px,calc(100vw - 20px));max-height:65vh;overflow:auto;padding:12px;border:1px solid #38577f;border-radius:14px;background:#07111ff5;box-shadow:0 18px 60px #0009;color:#e9f1ff;font:12px/1.45 system-ui,sans-serif';
  panel.innerHTML='<div style="display:flex;justify-content:space-between;align-items:center;gap:8px"><b>WebView Runtime v7</b><button data-x>×</button></div><pre data-dump style="white-space:pre-wrap;word-break:break-word;max-height:42vh;overflow:auto"></pre><div style="display:flex;gap:6px;flex-wrap:wrap"><button data-ping>Native ping</button><button data-reload>再読込</button><button data-roll>Rollback</button><button data-bundle>APK内蔵</button></div>';
  document.body.appendChild(panel);
  panel.querySelector('[data-x]').onclick=()=>panel.hidden=true;
  panel.querySelector('[data-ping]').onclick=()=>send('runtime.status');
  panel.querySelector('[data-reload]').onclick=()=>send('runtime.reload').then(r=>{if(r&&r.fallback)location.reload()});
  panel.querySelector('[data-roll]').onclick=()=>send('runtime.rollback');
  panel.querySelector('[data-bundle]').onclick=()=>send('runtime.useBundled');
}
function render(){
  ensureUi();
  if(!badge)return;
  const stale=!!window.AndroidRuntime&&state.lastNativeAt>0&&Date.now()-state.lastNativeAt>40000;
  const bad=state.jsErrors>0||stale;
  badge.textContent=bad?'WV !':state.ready?'WV ✓':'WV …';
  badge.style.borderColor=bad?'#a45a68':state.ready?'#3f8b67':'#8a7a46';
  if(panel&&!panel.hidden){
    const d=panel.querySelector('[data-dump]');
    if(d)d.textContent=JSON.stringify(snapshot(),null,2);
  }
}
function ready(){
  if(readySent)return;
  readySent=true;
  state.ready=true;
  capturePaints();
  render();
  send('runtime.ready',snapshot());
}
function probe(){
  render();
  return send('runtime.status',snapshot());
}

window.addEventListener('error',e=>recordError('error',{message:e.message,source:e.filename,line:e.lineno,col:e.colno}));
window.addEventListener('unhandledrejection',e=>recordError('promise',e.reason));
window.addEventListener('online',()=>{state.online=true;render()});
window.addEventListener('offline',()=>{state.online=false;render()});
document.addEventListener('visibilitychange',()=>{state.visible=document.visibilityState;render()});
document.addEventListener('DOMContentLoaded',()=>{
  state.metrics.domContentLoaded=performance.now();
  restoreScroll();
  ensureUi();
  render();
},{once:true});
window.addEventListener('load',()=>{
  state.metrics.load=performance.now();
  ready();
},{once:true});
window.addEventListener('pagehide',saveScroll);

try{
  if('PerformanceObserver'in window){
    const po=new PerformanceObserver(list=>{
      for(const e of list.getEntries())if(e.entryType==='longtask')state.longTasks++;
      render();
    });
    po.observe({entryTypes:['longtask']});
  }
}catch{}

if(window.AndroidRuntime){
  AndroidRuntime.subscribe(msg=>{
    if(msg&&msg.status){
      state.native=msg.status;
      state.lastNativeAt=Date.now();
      render();
    }
  });
}

setInterval(()=>{
  if(document.visibilityState==='visible'){
    send('runtime.heartbeat',{
      jsErrors:state.jsErrors,
      longTasks:state.longTasks,
      uptimeMs:Math.round(performance.now()-bootAt),
      online:navigator.onLine
    });
  }
},15000);

window.FFC_WEBVIEW_RUNTIME={
  version:VERSION,
  status:snapshot,
  probe,
  reload:()=>send('runtime.reload'),
  post:send
};

if(document.readyState==='complete')ready();
else{
  ensureUi();
  render();
}
})();
