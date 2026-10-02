const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const src=fs.readFileSync('app/src/main/assets/workspace-productivity.js','utf8');
const store=new Map();
const localStorage={getItem:k=>store.has(k)?store.get(k):null,setItem:(k,v)=>store.set(k,String(v))};
const document={body:{},head:{append(){}},getElementById(){return null},querySelector(){return null},querySelectorAll(){return[]},createElement(){return{dataset:{},style:{},append(){},remove(){},setAttribute(){},querySelector(){return null}}}};
class MutationObserver{observe(){}}
const window={addEventListener(){},FFC_THREADS:{state:{activeId:1,threads:[
 {id:1,name:'日本語会話',messages:[{id:1,role:'user',text:'A1: 本文検索ワード',meta:{attachments:[{name:'資料名_日本語.md',text:'添付本文',size:12,type:'text/markdown'}]}},{id:2,role:'assistant',text:'生成しました',meta:{generation:{tool:'document.generate',output:{body:'# 議事録',format:'markdown',filename:'minutes.md'}}}}]},
 {id:2,messages:[{id:3,role:'user',text:'A2: 別会話',meta:{attachments:[{name:'other.txt',text:'別',size:3,type:'text/plain'}]}}]}
]},core:{excelCode:id=>'A'+id}}};
const ctx={window,document,localStorage,MutationObserver,TextEncoder,Blob,URL:{createObjectURL(){return'blob:x'},revokeObjectURL(){}},navigator:{},setTimeout,clearTimeout,console};
vm.runInNewContext(src,ctx,{filename:'workspace-productivity.js'});
assert(window.FFCProductivity,'FFCProductivity missing');
assert.equal(window.FFCProductivity.listAttachments('active').length,1);
assert.equal(window.FFCProductivity.listAttachments('all').length,2);
assert.equal(window.FFCProductivity.listArtifacts('active').length,1);
assert.equal(window.FFCProductivity.listArtifacts('active')[0].name,'minutes.md');
assert.equal(window.FFCProductivity.setDraft(1,'再起動後の下書き'),true);
assert.equal(JSON.parse(store.get('FFC_THREAD_DRAFTS_V1'))['1'],'再起動後の下書き');
assert(src.includes('会話・本文・添付名を検索'));
assert(src.includes('全会話（明示的に選択）'));
assert(src.includes('.ffcSearchRow button{min-width:44px;min-height:44px'));
assert(src.includes('.ffcWorkspaceItem button{min-height:44px'));
assert(src.includes('@media(max-width:360px)'));
const attachments=fs.readFileSync('app/src/main/assets/attachments.js','utf8');
assert(attachments.includes('reuse(files,id=current())'));
assert(attachments.includes('128KB以内の資料だけ再添付できます'));
console.log('workspace-productivity: PASS');
