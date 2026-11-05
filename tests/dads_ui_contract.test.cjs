'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const read=p=>fs.readFileSync(p,'utf8');
const path='app/src/main/assets/';
const css=read(path+'dads-mobile.css'),js=read(path+'dads-accessible-ui.js');
const config=read('app/src/main/java/com/ffc/banc888/fly/AppConfig.java');
const workflow=read('.github/workflows/banc888-threaded-apk.yml');
assert.ok(config.includes('"dads-mobile.css"'),'CSS must be installed and live-updated');
assert.ok(config.includes('"dads-accessible-ui.js"'),'UI script must run inside APK');
assert.ok(config.indexOf('"dads-accessible-ui.js"')>config.indexOf('"thread-router.js"'),'UI script must load after router');
assert.ok(/grid-template-columns:repeat\(4,minmax\(0,1fr\)\)/.test(css),'four modes should fit in a row');
assert.ok(css.includes('min-height:48px'),'large target buttons required');
assert.ok(css.includes('min-height:44px'),'44 CSS px touch targets required');
assert.ok(css.includes('outline:3px solid #ffdf4d'),'focus must remain visible');
assert.ok(!css.includes('position:fixed;bottom:0'),'no prohibited bottom-fixed navigation');
assert.ok(css.includes('env(safe-area-inset-bottom)'),'avoid bottom gesture overlap');
assert.ok(css.includes('prefers-reduced-motion'),'reduce motion when requested');
assert.ok(js.includes("send.textContent='送信'"),'send must be clearly labeled');
assert.ok(js.includes("attach.textContent='添付'"),'attachment label missing');
assert.ok(js.includes("listen.textContent='音声'"),'speech label missing');
assert.ok(js.includes("drawer.setAttribute('role','dialog')"),'settings drawer dialog semantics missing');
assert.ok(js.includes("drawer.inert=!drawerOpen"),'closed drawer must not remain focusable');
assert.ok(js.includes("aria-pressed"),'mode buttons must expose active state');
assert.ok(workflow.includes('node tests/dads_ui_contract.test.cjs'),'UI contract must run in APK pipeline');

const input={value:'',dataset:{},dispatchEvent:()=>{},focus:()=>{input.focused=true}},
 hint={textContent:''},status={textContent:'',dataset:{}},
 buttons=['chat','code','image','video'].map(mode=>({dataset:{mode},setAttribute(k,v){this[k]=v}}));
const fixtures={'ffcThreadText':input,'dadsTaskHint':hint,'dadsStatus':status};
const sandbox={window:{},document:{
 getElementById:id=>fixtures[id]||null,
 querySelectorAll:selector=>selector==='.dadsTaskButtons button'?buttons:[],
 body:{classList:{contains:()=>false}},documentElement:null,readyState:'loading',
 addEventListener:()=>{}
},MutationObserver:class{observe(){}disconnect(){}},Event:class{constructor(name){this.type=name}},console};
vm.runInNewContext(js,sandbox);
const ui=sandbox.window.FFCDadsUI;
assert.equal(ui.VERSION,'1.0');
assert.equal(ui.select('invalid'),false);
assert.equal(ui.select('code'),true);
assert.ok(input.value.includes('Python'));assert.equal(buttons[1]['aria-pressed'],'true');
assert.equal(ui.select('image'),true);assert.ok(input.value.includes('画像を生成'));
assert.equal(ui.select('video'),true);assert.ok(input.value.includes('動画を生成'));
input.value='ユーザーが編集した内容';input.dataset.dadsPreset='no';
assert.equal(ui.select('code'),true);
assert.equal(input.value,'ユーザーが編集した内容','switch must not destroy a user draft');
assert.equal(ui.select('chat'),true);
assert.equal(input.value,'ユーザーが編集した内容','chat switch must preserve edited drafts');
assert.ok(input.focused);
console.log('dads-accessible-ui: PASS (touch/focus standards, wired controls, mode selection and preserved draft)');
