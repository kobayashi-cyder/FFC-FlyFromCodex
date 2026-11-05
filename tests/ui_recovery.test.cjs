'use strict';
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const core=fs.readFileSync('app/src/main/assets/ui-recovery.js','utf8');
const css=fs.readFileSync('app/src/main/assets/ui-recovery.css','utf8');
const config=fs.readFileSync('app/src/main/java/com/ffc/banc888/fly/AppConfig.java','utf8');
assert.ok(config.includes('"ui-recovery.js"'));
assert.ok(config.includes('"ui-recovery.css"'));
assert.ok(config.indexOf('"ui-recovery.js"')>config.indexOf('"dads-accessible-ui.js"'));
assert.ok(css.includes('z-index:35')&&css.includes('min-height:48px'));
assert.ok(css.includes('safe-area-inset-bottom'));
assert.ok(core.includes('attempts>=3'),'automatic retries must be bounded');
assert.ok(!core.includes('location.reload'),'no reload loops or lost conversations');
assert.ok(core.includes('ui-recovery.css'),'recovery styling should be bundled');
class Element{
 constructor(tag){this.tagName=tag.toUpperCase();this.children=[];this.dataset={};this.attributes={};
  this.parentNode=null;this.style={};this.hidden=false;this.value='';this.textContent='';this.isConnected=true;}
 append(...nodes){nodes.forEach(n=>this.appendChild(n))}
 appendChild(n){n.parentNode=this;this.children.push(n);return n}
 remove(){if(this.parentNode)this.parentNode.children=this.parentNode.children.filter(x=>x!==this);this.isConnected=false}
 get firstElementChild(){return this.children[0]}
 setAttribute(k,v){this.attributes[k]=v}
 getAttribute(k){return this.attributes[k]}
 focus(){this.focused=true}
 querySelectorAll(tag){
  const match=tag==='button';
  function visit(n){return [...n.children.flatMap(k=>[...(match&&k.tagName==='BUTTON'?[k]:[]),...visit(k)])]}
  return visit(this);
 }
 getBoundingClientRect(){return this.bounds||{width:200,height:60,top:10,bottom:70}}
}
const doc={body:new Element('body'),head:new Element('head'),readyState:'complete',hidden:false,
 createElement:tag=>new Element(tag),addEventListener:()=>{},
 getElementById(id){
  const scan=n=>{if(n.id===id)return n;for(const child of n.children){const r=scan(child);if(r)return r}return null};
  return scan(this.body)||scan(this.head);
 }};
const store=new Map();
const localStorage={getItem:key=>store.get(key)||null,setItem:(k,v)=>store.set(k,v)};
let stubCall=null;
const window={FFC_PROXY_AGENT:{cognitionRequest:async text=>{stubCall=text;return{status:'done',finalText:'応答: '+text}}},addEventListener:()=>{}};
const sandbox={window,document:doc,localStorage,location:{href:'https://appassets.androidplatform.net/assets/index.html',origin:'https://appassets.androidplatform.net'},
 URL,innerHeight:800,setTimeout:()=>0,clearTimeout:()=>{},setInterval:()=>0,clearInterval:()=>{},console};
vm.runInNewContext(core,sandbox);
const api=window.FFC_UI_RECOVERY;
assert.equal(api.version,'1.0');
assert.equal(api.diagnostics().healthy,false);
const panel=doc.getElementById('bancRescueUI');assert.ok(panel,'missing thread UI must get a fallback');
assert.equal(api.diagnostics().fallbackVisible,true);
const box=doc.getElementById('bancRescueText'),send=doc.getElementById('bancRescueSend');
assert.ok(box);assert.ok(send);assert.equal(send.textContent,'送信');
const diag=doc.getElementById('bancRescueDiagnose');assert.ok(diag,'diagnostic control must be present');
diag.onclick();
assert.ok(doc.getElementById('bancRescueLog').children.length>0);
assert.match(api.diagnostics().lastStatus,/診断結果/);
assert.equal(api.setMode('image'),true);
assert.ok(box.value.includes('画像を生成'));
box.value='利用者の編集した本文';box.oninput();
assert.equal(api.setMode('video'),true);
assert.equal(box.value,'利用者の編集した本文','switch should never overwrite a user draft');
(async()=>{
 const ok=await api.send();
 assert.equal(ok,true);assert.equal(stubCall,'動画を生成して。内容: 利用者の編集した本文');
 assert.equal(box.value,'');
 assert.ok(doc.getElementById('bancRescueLog').children.length>=2);
 assert.ok(store.has('BANC_RESCUE_LOG_V1'));
 box.value='失敗テスト';
 window.FFC_PROXY_AGENT.cognitionRequest=async()=>({status:'blocked',finalText:'モデル未接続'});
 assert.equal(await api.send(),false);
 assert.equal(box.value,'失敗テスト','failed action must retain draft');
 assert.ok(doc.getElementById('bancRescueStatus').dataset.error==='true');
 const nodes={ffcThreadHub:new Element('div'),ffcThreadText:new Element('textarea'),
  ffcThreadSend:new Element('button'),ffcThreadLog:new Element('div')};
 nodes.ffcThreadSend.onclick=()=>{};
 for(const [id,n] of Object.entries(nodes)){n.id=id;doc.body.appendChild(n)}
 window.FFC_THREADS={route:()=>({}),repairUI:()=>true};
 assert.equal(api.healthy(),true);
 api.repair();
 assert.equal(panel.hidden,true,'fallback must disappear once normal input becomes usable');
 console.log('ui-recovery: PASS (independent fallback, diagnostic, mode switching, real send, error retention, automatic restoration)');
})().catch(e=>{console.error(e);process.exitCode=1});
