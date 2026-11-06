(() => {
'use strict';
if(window.FFC_SOLZERO_WORKSPACE)return;
const VERSION='1.0',ID='ffcSolZeroWorkspace';
const get=id=>document.getElementById(id);
let opened=false,loaded=false,sourceButton=null,previousFocus=null;
function node(tag,cls,text){
 const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;
 return e;
}
function status(message,failed=false){
 const target=get('ffcSolZeroStatus');
 if(target){target.textContent=String(message||'');target.dataset.error=String(!!failed)}
}
function frameDocument(){
 const frame=get('ffcSolZeroFrame');
 if(!loaded||!frame)return null;
 try{
  const doc=frame.contentDocument;
  return doc&&doc.getElementById('input')&&doc.getElementById('chatForm')?doc:null;
 }catch{return null}
}
function activeDraft(){
 const rescue=get('bancRescueUI'),rescueText=get('bancRescueText'),normal=get('ffcThreadText');
 if(rescue&&!rescue.hidden&&rescueText)return rescueText;
 return normal||rescueText||null;
}
function handoff(){
 const source=activeDraft(),target=frameDocument()?.getElementById('input');
 if(!target){status('SolZeroの入力画面が準備できていません。',true);return false}
 if(!source||!source.value.trim()){status('BANC側の入力欄に文章がありません。',true);return false}
 if(target.value.trim()&&!window.confirm('SolZero側に未送信の文章があります。上書きしますか？'))return false;
 const copy=source.value.trim().slice(0,3000);
 target.value=copy;target.dispatchEvent(new Event('input',{bubbles:true}));
 target.focus();status('下書きをSolZeroにコピーしました。SolZeroの「送信」で実行してください。');
 return true;
}
function returnLast(){
 const doc=frameDocument(),dest=activeDraft();
 if(!doc){status('SolZeroの会話画面が準備できていません。',true);return false}
 if(!dest){status('BANCの入力画面がありません。自動復旧を利用してください。',true);return false}
 const items=doc.querySelectorAll('.message.assistant .bubble');
 const last=items.length?String(items[items.length-1].textContent||'').trim():'';
 if(!last){status('取り込むSolZeroの回答がありません。',true);return false}
 const prefix='[SolZeroの回答・未検証]\n';
 const room=Math.max(0,12000-dest.value.length-prefix.length-2);
 if(!room){status('BANCの下書き欄が満杯です。',true);return false}
 dest.value+=(dest.value.trim()?'\n\n':'')+prefix+last.slice(0,room);
 dest.dispatchEvent(new Event('input',{bubbles:true}));
 status('SolZeroの回答をBANCの下書きに取り込みました。自動送信はしません。');
 close();dest.focus({preventScroll:true});
 return true;
}
function sourceURL(){
 const u=new URL('solzero-chat.html',location.href);
 if(u.origin!==location.origin||!/^\/(assets|live)\//.test(u.pathname))throw Error('local SolZero path required');
 return u.href;
}
function createOverlay(){
 if(get(ID))return;
 const box=node('section','ffcSolZeroWorkspace');box.id=ID;box.hidden=true;
 box.setAttribute('role','dialog');box.setAttribute('aria-modal','true');box.setAttribute('aria-label','SolZeroオフライン作業画面');
 const header=node('header','ffcSolZeroHeader');
 const heading=node('div','ffcSolZeroHeading');
 heading.append(node('strong','', 'SolZero v0.14.1'),node('span','','独立したオフラインWSP・記憶・エージェント'));
 const actions=node('div','ffcSolZeroActions');
 const send=node('button','','BANCの下書きを渡す');send.type='button';send.id='ffcSolZeroHandoff';send.onclick=handoff;
 const receive=node('button','','回答をBANCへ戻す');receive.type='button';receive.id='ffcSolZeroReturn';receive.onclick=returnLast;
 const closeButton=node('button','','閉じる');closeButton.type='button';closeButton.id='ffcSolZeroClose';closeButton.onclick=close;
 actions.append(send,receive,closeButton);header.append(heading,actions);
 const s=node('p','ffcSolZeroStatus','準備完了');s.id='ffcSolZeroStatus';s.setAttribute('role','status');s.setAttribute('aria-live','polite');
 const frame=node('iframe');frame.id='ffcSolZeroFrame';frame.title='SolZero オフラインチャット';
 frame.setAttribute('referrerpolicy','no-referrer');frame.setAttribute('loading','lazy');
 frame.onload=()=>{
  loaded=!!frameDocumentReady(frame);
  status(loaded?'SolZeroを読み込みました。BANCとは記憶・重みが独立しています。':'SolZeroを読み込めませんでした。',!loaded);
 };
 frame.onerror=()=>{loaded=false;status('SolZero作業画面の読み込みに失敗しました。',true)};
 box.append(header,s,frame);
 document.body.appendChild(box);
}
function frameDocumentReady(frame){
 try{const doc=frame.contentDocument;return !!(doc&&doc.getElementById('chatForm')&&doc.getElementById('input'))}
 catch{return false}
}
function open(button){
 createOverlay();
 const overlay=get(ID),frame=get('ffcSolZeroFrame');
 if(!overlay||!frame)return false;
 previousFocus=document.activeElement;sourceButton=button||sourceButton;
 overlay.hidden=false;opened=true;
 if(!frame.getAttribute('src')){
  try{frame.setAttribute('src',sourceURL())}
  catch(e){status('SolZeroの読み込み場所が不正です。',true)}
 }
 const closeButton=get('ffcSolZeroClose');if(closeButton)closeButton.focus({preventScroll:true});
 return true;
}
function close(){
 const overlay=get(ID);if(overlay)overlay.hidden=true;
 opened=false;
 if(previousFocus&&previousFocus.isConnected)previousFocus.focus({preventScroll:true});
 previousFocus=null;
}
function makeButton(id){
 const b=node('button','ffcSolZeroTrigger','SolZero');
 b.id=id;b.type='button';b.setAttribute('aria-label','SolZeroオフライン作業画面を開く');
 b.setAttribute('aria-controls',ID);b.onclick=()=>open(b);return b;
}
function install(){
 const header=get('uiHub')?.querySelector('.uiHubTop .conversationHeaderActions:last-child');
 if(header&&!get('ffcSolZeroOpen'))header.insertBefore(makeButton('ffcSolZeroOpen'),header.firstChild);
 const rescue=get('bancRescueUI')?.querySelector('.bancRescueHeader');
 if(rescue&&!get('ffcSolZeroRescueOpen'))rescue.append(makeButton('ffcSolZeroRescueOpen'));
 return !!header||!!rescue;
}
function loadStyles(){
 if(get('ffcSolZeroStyles'))return;
 const link=node('link');link.id='ffcSolZeroStyles';link.rel='stylesheet';
 try{
  const url=new URL('solzero-workspace.css',location.href);
  if(url.origin!==location.origin||!/^\/(assets|live)\//.test(url.pathname))throw Error('not local');
  link.href=url.href;
  document.head.appendChild(link);
 }catch(e){status('画面スタイルの読み込みに失敗しました。',true)}
}
function init(){
 loadStyles();createOverlay();install();
 const observer=new MutationObserver(()=>install());
 observer.observe(document.body,{childList:true,subtree:true});
 document.addEventListener('keydown',event=>{
  if(event.key!=='Escape'||!opened)return;
  event.stopImmediatePropagation();event.preventDefault();close();
 },true);
 window.addEventListener('pagehide',()=>observer.disconnect(),{once:true});
}
window.FFC_SOLZERO_WORKSPACE={VERSION,open,close,handoff,returnLast,install,
 status:()=>({opened,loaded,source:'SolZero Chat 0.14.1 offline',independentWeights:true,independentMemory:true})};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();