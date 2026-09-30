(() => {
'use strict';
if(window.__BANC888_WEBVIEW_RUNTIME_V7__)return;
window.__BANC888_WEBVIEW_RUNTIME_V7__=true;

const VERSION=7;
const BUILD='7.1-java-integrated';
const bootAt=performance.now();
const state={
  ready:false,
  visible:document.visibilityState,
  online:navigator.onLine,
  jsErrors:0,
  longTasks:0,
  lastNativeAt:0,
  native:null,
  capabilities:null,
  nativeEvents:0,
  lastNativeEvent:null,
  errors:[],
  metrics:{domContentLoaded:0,load:0,firstPaint:0,firstContentfulPaint:0}
};
let panel=null;
let readySent=false;
let mountObserver=null;

function transport(){
  try{return window.AndroidRuntime&&AndroidRuntime.transport?AndroidRuntime.transport():{mode:'none',epoch:0,pending:0}}
  catch{return{mode:'error',epoch:0,pending:0}}
}
function snapshot(){
  return{
    version:VERSION,
    build:BUILD,
    ready:state.ready,
    visible:state.visible,
    online:state.online,
    uptimeMs:Math.round(performance.now()-bootAt),
    jsErrors:state.jsErrors,
    longTasks:state.longTasks,
    lastNativeAt:state.lastNativeAt,
    native:state.native,
    capabilities:state.capabilities,
    nativeEvents:state.nativeEvents,
    lastNativeEvent:state.lastNativeEvent,
    transport:transport(),
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
    if(r&&r.capabilities){
      state.capabilities=r.capabilities;
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
  if(panel&&document.contains(panel))return true;
  const settings=document.getElementById('uiSettingsBody');
  if(!settings)return false;
  panel=document.createElement('details');
  panel.id='bancWebRuntimePanel';
  panel.className='agentSubDetails';
  panel.innerHTML='<summary>WebView / Java連携 <span data-state class="pill">WV …</span></summary>'
    +'<div class="agentSubBody">'
    +'<div class="muted tiny" style="margin-bottom:8px">WebMessage・セッション世代・Renderer・Heartbeatをまとめて監視します。</div>'
    +'<pre data-dump class="expmono" style="white-space:pre-wrap;word-break:break-word;max-height:42vh;overflow:auto">接続確認中…</pre>'
    +'<div class="row" style="margin-top:8px"><button data-ping>連携確認</button><button data-reload>再読込</button><button data-roll>Rollback</button><button data-bundle>APK内蔵</button></div>'
    +'</div>';
  settings.prepend(panel);
  panel.querySelector('[data-ping]').onclick=()=>Promise.all([send('runtime.status'),send('runtime.capabilities')]);
  panel.querySelector('[data-reload]').onclick=()=>send('runtime.reload').then(r=>{if(r&&r.fallback)location.reload()});
  panel.querySelector('[data-roll]').onclick=()=>send('runtime.rollback');
  panel.querySelector('[data-bundle]').onclick=()=>send('runtime.useBundled');
  if(mountObserver){mountObserver.disconnect();mountObserver=null}
  render();
  return true;
}
function watchUi(){
  if(ensureUi()||mountObserver||!document.documentElement)return;
  mountObserver=new MutationObserver(()=>ensureUi());
  mountObserver.observe(document.documentElement,{childList:true,subtree:true});
}
function render(){
  if(!panel)ensureUi();
  if(!panel)return;
  const t=transport();
  const stale=!!window.AndroidRuntime&&state.lastNativeAt>0&&Date.now()-state.lastNativeAt>40000;
  const rendererBad=!!(state.native&&state.native.rendererUnresponsive);
  const bad=state.jsErrors>0||stale||rendererBad;
  const mark=panel.querySelector('[data-state]');
  if(mark){
    mark.textContent=bad?'WV !':state.ready?'WV ✓':'WV …';
    mark.title=t.mode+' / epoch '+t.epoch;
  }
  if(panel.open){
    const d=panel.querySelector('[data-dump]');
    if(d)d.textContent=JSON.stringify(snapshot(),null,2);
  }
}
function ready(){
  if(readySent)return;
  readySent=true;
  state.ready=true;
  capturePaints();
  watchUi();
  render();
  send('runtime.ready',snapshot()).then(()=>send('runtime.capabilities'));
}
function probe(){
  watchUi();
  render();
  return Promise.all([send('runtime.status',snapshot()),send('runtime.capabilities')]);
}

window.addEventListener('error',e=>recordError('error',{message:e.message,source:e.filename,line:e.lineno,col:e.colno}));
window.addEventListener('unhandledrejection',e=>recordError('promise',e.reason));
window.addEventListener('online',()=>{state.online=true;render()});
window.addEventListener('offline',()=>{state.online=false;render()});
document.addEventListener('visibilitychange',()=>{state.visible=document.visibilityState;render()});
document.addEventListener('DOMContentLoaded',()=>{
  state.metrics.domContentLoaded=performance.now();
  restoreScroll();
  watchUi();
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
    if(!msg)return;
    if(msg.status)state.native=msg.status;
    if(msg.capabilities)state.capabilities=msg.capabilities;
    if(msg.nativePush){
      state.nativeEvents++;
      state.lastNativeEvent=msg;
      if(msg.type==='renderer.state'&&state.native&&msg.data){
        state.native.rendererUnresponsive=!!msg.data.unresponsive;
        state.native.rendererUnresponsiveCount=Number(msg.data.count||0);
      }
    }
    state.lastNativeAt=Date.now();
    render();
  });
}

setInterval(()=>{
  if(document.visibilityState==='visible'){
    send('runtime.heartbeat',{
      jsErrors:state.jsErrors,
      longTasks:state.longTasks,
      uptimeMs:Math.round(performance.now()-bootAt),
      online:navigator.onLine,
      transport:transport()
    });
  }
},15000);

window.FFC_WEBVIEW_RUNTIME={
  version:VERSION,
  build:BUILD,
  status:snapshot,
  probe,
  reload:()=>send('runtime.reload'),
  post:send
};

watchUi();
if(document.readyState==='complete')ready();
else render();
})();