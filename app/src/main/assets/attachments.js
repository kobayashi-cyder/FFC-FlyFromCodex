(() => {
'use strict';
if(window.FFCAttachments)return;
const core=window.FFCAttachmentCore,drafts=new Map();let pickerThread=null,loading=false;
const current=()=>window.FFC_THREADS?.state.activeId;
const peek=id=>(drafts.get(id)||[]).map(f=>({...f}));
function notice(text){const e=document.getElementById('ffcAttachmentStatus');if(e)e.textContent=text}
function render(){const holder=document.getElementById('ffcAttachmentChips');if(!holder)return;holder.replaceChildren();for(const [index,file] of peek(current()).entries()){const b=document.createElement('button');b.type='button';b.textContent=file.name+(file.truncated?'（先頭のみ）':'')+' ×';b.setAttribute('aria-label',file.name+'を添付から削除');b.onclick=()=>{const id=current(),files=peek(id);files.splice(index,1);drafts.set(id,files);render()};holder.append(b)}const button=document.getElementById('ffcAttachButton');if(button)button.disabled=loading}
function consume(id){const files=peek(id);drafts.delete(id);render();notice('');return files}
function read(file){return new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>{try{resolve(core.decode(file,new Uint8Array(reader.result)))}catch(e){reject(e)}};reader.onerror=()=>reject(new Error('ファイルを読み込めませんでした。'));reader.onabort=()=>reject(new Error('ファイルの読み込みを停止しました。'));reader.readAsArrayBuffer(file)})}
async function attachFiles(files,id=current()){
 if(loading)throw new Error('添付を読み込み中です。');if(!id)throw new Error('会話がまだ準備できていません。');
 if(drafts.size>=16&&!drafts.has(id))throw new Error('未送信の添付がある会話は16本までです。先に送信または削除してください。');
 const existing=peek(id),chosen=Array.from(files);if(existing.length+chosen.length>core.MAX_FILES)throw new Error('添付は1回4ファイルまでです。');
 loading=true;render();notice('添付を読み込んでいます…');
 try{const added=[];for(const file of chosen){if(file.size>core.MAX_BYTES)throw new Error(core.name(file.name)+'：128KB以内のファイルを選んでください。');added.push(await read(file))}const all=[...existing,...added];if(all.reduce((n,f)=>n+f.text.length,0)>24000)throw new Error('添付本文は合計24,000文字までです。ファイルを分けて送信してください。');drafts.set(id,all);notice('添付しました。送信すると、この会話の処理に使います。');return peek(id)}finally{loading=false;render()}
}
function mount(){const tools=document.querySelector('.conversationComposerTools'),composer=document.querySelector('.ffcThreadInput');if(!tools||!composer||document.getElementById('ffcAttachButton'))return;
 const input=document.createElement('input');input.id='ffcAttachmentInput';input.type='file';input.multiple=true;input.hidden=true;input.accept='.txt,.md,.csv,.tsv,.json,.jsonl,.ndjson,.xml,.yaml,.yml,.html,.css,.js,.jsx,.ts,.tsx,.py,.java,.kt,.kts,.c,.h,.cpp,.hpp,.cs,.go,.rs,.rb,.php,.sh,.sql,.log';
 const button=document.createElement('button');button.id='ffcAttachButton';button.type='button';button.textContent='添付';button.setAttribute('aria-label','文章やコードを添付');button.onclick=()=>{pickerThread=current();input.click()};input.onchange=async()=>{const files=Array.from(input.files||[]),id=pickerThread??current();try{if(files.length)await attachFiles(files,id)}catch(e){notice(e.message)}finally{input.value=''}};tools.prepend(button,input);
 const chips=document.createElement('div');chips.id='ffcAttachmentChips';const status=document.createElement('div');status.id='ffcAttachmentStatus';status.setAttribute('role','status');composer.prepend(chips,status);
 if(!document.getElementById('ffcAttachmentStyle')){const style=document.createElement('style');style.id='ffcAttachmentStyle';style.textContent='body.chat-layout .ffcThreadInput{grid-template-areas:"files files" "status status" "text text" "tools send"}body.chat-layout #ffcThreadText{grid-area:text}body.chat-layout .conversationComposerTools{grid-area:tools}body.chat-layout #ffcThreadSend{grid-area:send}#ffcAttachmentChips{grid-area:files}#ffcAttachmentStatus{grid-area:status}#ffcAttachmentChips:empty,#ffcAttachmentStatus:empty{display:none}#ffcAttachmentChips,#ffcAttachmentStatus{grid-column:1/3;overflow-wrap:anywhere}#ffcAttachmentChips{display:flex;gap:4px;flex-wrap:wrap;max-height:96px;overflow:auto}#ffcAttachmentChips button{min-height:44px;max-width:100%;padding:6px 9px;font-size:12px;text-align:left;overflow-wrap:anywhere}#ffcAttachmentStatus{font-size:11px;color:var(--muted)}#ffcAttachmentInput[hidden]{display:none!important}';document.head.append(style)}render();
}
window.FFCAttachments={peek,consume,attachFiles,isLoading:()=>loading,format:core.format};
window.addEventListener('ffc-threads-change',render);window.addEventListener('ffc:conversation-ui-ready',mount);new MutationObserver(mount).observe(document.body,{childList:true,subtree:true});mount();
})();
