(() => {
'use strict';
// Adaptation of Digital Agency Design System; not an official government UI.
if(window.FFCDadsUI)return;
const VERSION='1.0',PRESETS=[
 {id:'chat',label:'会話',hint:'質問を入力して、送信を押してください。',prompt:''},
 {id:'code',label:'コード',hint:'作りたいプログラムの条件を編集して、送信してください。',prompt:'PythonでCSVファイルを読み込み、列ごとに集計するコードを書いて。'},
 {id:'image',label:'画像',hint:'描きたい対象・構図・色を編集して、送信してください。',prompt:'青空の下で走る猫の画像を生成して。'},
 {id:'video',label:'動画',hint:'動きと時間を指定して、送信してください。',prompt:'青空の下で猫が走る短い動画を生成して。'}
];
const el=id=>document.getElementById(id);
let active='chat',ready=false,previousFocus=null;
function status(message,kind='info'){
 const node=el('dadsStatus');if(node){node.textContent=String(message);node.dataset.kind=kind}
}
function select(mode){
 const preset=PRESETS.find(p=>p.id===mode),input=el('ffcThreadText');
 if(!preset||!input)return false;
 active=mode;
 for(const button of document.querySelectorAll('.dadsTaskButtons button'))button.setAttribute('aria-pressed',String(button.dataset.mode===mode));
 const hint=el('dadsTaskHint');if(hint)hint.textContent=preset.hint;
 if(!input.value.trim()||input.dataset.dadsPreset==='yes'){
  input.value=preset.prompt;input.dataset.dadsPreset=preset.prompt?'yes':'no';
  input.dispatchEvent(new Event('input',{bubbles:true}));
  status(preset.label+'を選択しました。'+preset.hint);
 }else status(preset.label+'を選択しました。入力済みの内容は保持されています。');
 input.focus({preventScroll:true});return true;
}
function loadStyles(){
 if(el('dadsStyle'))return;
 const link=document.createElement('link');link.id='dadsStyle';link.rel='stylesheet';
 link.href=new URL('dads-mobile.css',location.href).href;
 link.onerror=()=>status('画面スタイルを読み込めませんでした。基本の操作は利用できます。','error');
 document.head.appendChild(link);
}
function controls(){
 const hub=el('ffcThreadHub'),input=el('ffcThreadText'),send=el('ffcThreadSend');
 if(!hub||!input||!send||!hub.querySelector('.conversationComposerWrap'))return false;
 if(!el('dadsTaskNav')){
  const nav=document.createElement('nav');nav.id='dadsTaskNav';nav.className='dadsTaskNav';
  nav.setAttribute('aria-label','作成機能を選ぶ');
  const title=document.createElement('p');title.textContent='使う機能を選択';
  const buttons=document.createElement('div');buttons.className='dadsTaskButtons';
  buttons.setAttribute('role','group');buttons.setAttribute('aria-label','生成する種類');
  for(const p of PRESETS){
   const button=document.createElement('button');button.type='button';
   button.id='dadsMode'+p.id[0].toUpperCase()+p.id.slice(1);
   button.dataset.mode=p.id;button.textContent=p.label;
   button.setAttribute('aria-pressed',String(active===p.id));
   button.setAttribute('aria-controls','ffcThreadText');
   button.addEventListener('click',()=>select(p.id));
   buttons.appendChild(button);
  }
  const hint=document.createElement('p');hint.id='dadsTaskHint';
  hint.className='dadsTaskHint';hint.textContent=PRESETS[0].hint;
  nav.append(title,buttons,hint);
  const log=el('ffcThreadLog');if(log)hub.insertBefore(nav,log);else hub.prepend(nav);
 }
 const composer=hub.querySelector('.conversationComposerWrap');
 if(!el('dadsStatus')){
  const node=document.createElement('p');node.id='dadsStatus';node.className='dadsStatus';
  node.setAttribute('role','status');node.setAttribute('aria-live','polite');
  node.textContent='依頼を入力し、「送信」を押してください。';
  const note=composer.querySelector('.conversationComposerNote');
  if(note)composer.insertBefore(node,note);else composer.appendChild(node);
 }
 send.textContent='送信';send.title='入力内容を送信';
 send.setAttribute('aria-label','メッセージを送信');
 input.setAttribute('aria-label','依頼内容');
 input.setAttribute('aria-describedby','dadsTaskHint');
 input.setAttribute('placeholder','何を作りたいですか？');
 if(!input.dataset.dadsInputHook){
  input.dataset.dadsInputHook='1';
  input.addEventListener('input',e=>{if(e.isTrusted)input.dataset.dadsPreset='no'});
 }
 const gear=el('uiGearButton');if(gear)gear.setAttribute('aria-label','詳細設定を開く');
 const menu=el('conversationSidebarToggle');if(menu){menu.textContent='☰ 会話';menu.setAttribute('aria-label','会話一覧を開く')}
 const attach=el('ffcAttachButton');if(attach){attach.textContent='添付';attach.title='資料を添付';attach.setAttribute('aria-label','資料を添付')}
 const listen=el('ffcQuickListen');if(listen){listen.textContent='音声';listen.title='音声入力';listen.setAttribute('aria-label','音声入力を開始')}
 const stop=el('ffcQuickStop');if(stop){stop.textContent='停止';stop.setAttribute('aria-label','音声入力を停止')}
 const drawer=el('uiSettingsDrawer');
 if(drawer){drawer.setAttribute('role','dialog');drawer.setAttribute('aria-label','詳細設定');drawer.setAttribute('aria-modal','true')}
 const sidebar=el('conversationSidebar');if(sidebar)sidebar.setAttribute('aria-label','会話一覧');
 el('uiSettingsShade')?.setAttribute('aria-hidden','true');
 el('conversationSidebarShade')?.setAttribute('aria-hidden','true');
 return true;
}
function focusables(root){
 return [...root.querySelectorAll('button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),summary,[tabindex]:not([tabindex="-1"])')]
  .filter(node=>node.getClientRects().length>0&&!node.closest('[hidden],[inert]'));
}
function focusFirst(root){focusables(root)[0]?.focus({preventScroll:true})}
function overlay(){
 const drawer=el('uiSettingsDrawer'),sidebar=el('conversationSidebar'),toggle=el('conversationSidebarToggle');
 const drawerOpen=!!drawer?.classList.contains('open'),sidebarOpen=document.body.classList.contains('conversation-sidebar-open');
 if(drawer){
  drawer.inert=!drawerOpen;drawer.setAttribute('aria-hidden',String(!drawerOpen));
  if(drawerOpen&&!drawer.dataset.dadsOpen){
   drawer.dataset.dadsOpen='1';previousFocus=document.activeElement;focusFirst(drawer);
  }else if(!drawerOpen&&drawer.dataset.dadsOpen){
   delete drawer.dataset.dadsOpen;
   if(previousFocus?.isConnected)previousFocus.focus({preventScroll:true});
   previousFocus=null;
  }
 }
 if(sidebar){
  const mobile=matchMedia('(max-width:700px)').matches;
  sidebar.inert=mobile&&!sidebarOpen;
  sidebar.setAttribute('aria-hidden',String(mobile&&!sidebarOpen));
  if(mobile&&sidebarOpen&&!sidebar.dataset.dadsOpen){
   sidebar.dataset.dadsOpen='1';previousFocus=document.activeElement;focusFirst(sidebar);
  }else if((!mobile||!sidebarOpen)&&sidebar.dataset.dadsOpen){
   delete sidebar.dataset.dadsOpen;
   if(previousFocus?.isConnected)previousFocus.focus({preventScroll:true});
   previousFocus=null;
  }
 }
 if(toggle){
  toggle.setAttribute('aria-expanded',String(sidebarOpen));
  toggle.setAttribute('aria-label',sidebarOpen?'会話一覧を閉じる':'会話一覧を開く');
 }
}
function mount(){
 // Re-apply controls after an autonomous thread UI reconstruction.
 if(ready)return controls();
 if(!document.body?.classList.contains('chat-layout'))return false;
 if(!controls())return false;
 ready=true;loadStyles();
 try{
  // Fresh installs should use the high-contrast light presentation.
  if(!localStorage.getItem('FFC_CONVERSATION_THEME')&&document.body.dataset.theme!=='light')
   el('conversationThemeToggle')?.click();
 }catch(e){}
 overlay();
 const drawer=el('uiSettingsDrawer');
 if(drawer)new MutationObserver(overlay).observe(drawer,{attributes:true,attributeFilter:['class']});
 new MutationObserver(overlay).observe(document.body,{attributes:true,attributeFilter:['class']});
 addEventListener('resize',overlay);
 document.addEventListener('keydown',e=>{
  if(e.key==='Escape'){
   if(document.body.classList.contains('conversation-sidebar-open'))el('conversationSidebarToggle')?.click();
   else if(drawer?.classList.contains('open'))el('uiSettingsClose')?.click();
   return;
  }
  if(e.key!=='Tab')return;
  const modal=drawer?.classList.contains('open')?drawer:
   document.body.classList.contains('conversation-sidebar-open')&&matchMedia('(max-width:700px)').matches?el('conversationSidebar'):null;
  if(!modal)return;
  const list=focusables(modal);
  if(!list.length)return;
  if(e.shiftKey&&document.activeElement===list[0]){list[list.length-1].focus();e.preventDefault()}
  else if(!e.shiftKey&&document.activeElement===list[list.length-1]){list[0].focus();e.preventDefault()}
 },true);
 addEventListener('ffc-threads-change',()=>{
  const t=window.FFC_THREADS?.state,thread=t?.threads?.find(x=>x.id===t.activeId);
  if(thread?.pending)status('処理中です。応答は会話欄に表示されます。');
  else if(thread?.messages?.length)status('会話が更新されました。次の依頼も入力できます。');
 });
 status('準備完了。機能を選び、依頼を入力して「送信」を押してください。');
 return true;
}
window.FFCDadsUI={VERSION,select,status,mount,statusSnapshot:()=>({
 mounted:ready,mode:active,buttons:document.querySelectorAll('.dadsTaskButtons button').length
})};
const observer=new MutationObserver(()=>{if(mount())observer.disconnect()});
if(document.documentElement)observer.observe(document.documentElement,{childList:true,subtree:true});
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount,{once:true});
mount();
})();