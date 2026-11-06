'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const base='app/src/main/assets/';
const html=fs.readFileSync(base+'solzero-chat.html','utf8');
const js=fs.readFileSync(base+'solzero-workspace.js','utf8');
const css=fs.readFileSync(base+'solzero-workspace.css','utf8');
const config=fs.readFileSync('app/src/main/java/com/ffc/banc888/fly/AppConfig.java','utf8');
assert.ok(html.startsWith('<!doctype html>'));
assert.ok(html.includes("const ENGINE_VERSION = '0.14.1'"));
assert.ok(html.includes('class SolZeroEngine'));
assert.ok(html.includes('class CognitiveAgent'));
assert.ok(html.includes("id=\"chatForm\""));
assert.ok(html.includes('solzero_chat_v0141_local'));
assert.equal((html.match(/<script\b/g)||[]).length,1,'SolZero origin must be single-file and offline');
assert.ok(!/https?:\/\/[^'"]+\.js/.test(html),'SolZero must not load remote JS');
const scripts=[...html.matchAll(/<script\s+type="module"\s*>([\s\S]*?)<\/script>/gi)];
assert.equal(scripts.length,1,'SolZero embedded engine must remain intact');
new Function(scripts[0][1]); // Only parse; DO NOT execute or evaluate user scripts
assert.ok(config.includes('"solzero-chat.html"'));
assert.ok(config.includes('"solzero-workspace.js"'));
assert.ok(config.includes('"solzero-workspace.css"'));
assert.ok(config.indexOf('"solzero-workspace.js"')>config.indexOf('"ui-recovery.js"'));
assert.ok(css.includes('min-height:48px'));
assert.ok(css.includes('safe-area-inset-bottom'));
assert.ok(!js.includes('fetch(') && !js.includes('location.reload('));
assert.ok(js.includes('returnLast')&&js.includes('handoff'));
assert.ok(js.includes('FFC_SOLZERO_WORKSPACE'));
assert.ok(!js.includes('executeAsync('),'handoff must not silently execute BANC tools');
class Fake {
 constructor(tag,owner){this.tagName=tag.toUpperCase();this.ownerDocument=owner;this.children=[];this.dataset={};this.attributes={};
  this.hidden=false;this.value='';this.textContent='';this.style={};this.isConnected=true;this.parentElement=null;}
 append(...xs){xs.forEach(x=>this.appendChild(x))}
 appendChild(x){this.children.push(x);x.parentElement=this;return x}
 insertBefore(x,p){this.children.splice(Math.max(0,this.children.indexOf(p)),0,x);x.parentElement=this}
 setAttribute(k,v){this.attributes[k]=String(v)}
 getAttribute(k){return this.attributes[k]??null}
 addEventListener(){}
 dispatchEvent(){}
 focus(){this.ownerDocument.activeElement=this}
 querySelector(sel){
  if(sel==='.uiHubTop .conversationHeaderActions:last-child')return this.ownerDocument.topActions||null;
  if(sel==='.bancRescueHeader')return this.ownerDocument.rescueHeader||null;
  return null
 }
 querySelectorAll(sel){
  if(sel==='.message.assistant .bubble')return this.ownerDocument.responses||[];
  return[]
 }
}
const doc={readyState:'complete',activeElement:null,topActions:null,rescueHeader:null,responses:[],
 createElement:tag=>new Fake(tag,doc),addEventListener(){},
 getElementById(id){
  function scan(n){if(n.id===id)return n;for(const x of n.children){const v=scan(x);if(v)return v}return null}
  return scan(this.body)||scan(this.head)
 }};
doc.body=new Fake('body',doc);doc.head=new Fake('head',doc);
const hub=new Fake('div',doc);hub.id='uiHub';doc.topActions=new Fake('div',doc);hub.querySelector=sel=>sel==='.uiHubTop .conversationHeaderActions:last-child'?doc.topActions:null;hub.appendChild(doc.topActions);doc.body.appendChild(hub);
const draft=new Fake('textarea',doc);draft.id='ffcThreadText';draft.value='挨拶をして';doc.body.appendChild(draft);
const events={};
const sandbox={window:{addEventListener(){}},document:doc,location:{href:'https://appassets.androidplatform.net/assets/index.html',origin:'https://appassets.androidplatform.net'},URL,Event:class{},MutationObserver:class{observe(){}disconnect(){}},console};
vm.runInNewContext(js,sandbox);
const api=sandbox.window.FFC_SOLZERO_WORKSPACE;
assert.equal(api.VERSION,'1.0');
assert.ok(doc.getElementById('ffcSolZeroOpen'),'normal header must expose SolZero');
assert.equal(api.open(doc.getElementById('ffcSolZeroOpen')),true);
const panel=doc.getElementById('ffcSolZeroWorkspace');
assert.equal(panel.hidden,false);
const frame=doc.getElementById('ffcSolZeroFrame');
assert.ok(frame.attributes.src.startsWith('https://appassets.androidplatform.net/assets/solzero-chat.html'));
const frameDoc={getElementById:id=>id==='input'?frameInput:id==='chatForm'?{}:null,
 querySelectorAll:()=>[reply]};
const frameInput={value:'',dispatchEvent(){},focus(){}};
const reply={textContent:'SolZeroの計画案'};
frame.contentDocument=frameDoc;frame.onload();
assert.equal(api.status().loaded,true);
assert.equal(api.handoff(),true);assert.equal(frameInput.value,'挨拶をして');
assert.equal(api.returnLast(),true);
assert.ok(draft.value.includes('SolZeroの計画案'));
assert.ok(draft.value.includes('未検証'));
assert.equal(panel.hidden,true);
console.log('solzero-workspace: PASS (full engine parse, no external JS, local iframe, explicit two-way draft handoff)');
