(function(root,factory){
  'use strict';
  const api=factory(root);
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCConversationExport=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(root){
  'use strict';
  const SCHEMA='BANC888-conversations',VERSION=1,STORE='FFC_THREAD_ROUTER_V1';
  const MAX_THREADS=256,MAX_MESSAGES=120,MAX_TEXT=512*1024,MAX_EXPORT_TEXT=4*1024*1024;
  let lastSaved=null;
  // Only own data fields are read. Exporting never walks native bridges, tokens,
  // arbitrary metadata, or scene/canvas binary buffers.
  function own(o,k){if(!o||typeof o!=='object')return undefined;const d=Object.getOwnPropertyDescriptor(o,k);return d&&'value'in d?d.value:undefined}
  function str(v,max=2000){return typeof v==='string'?v.slice(0,max):typeof v==='number'&&Number.isFinite(v)?String(v):''}
  function num(v){return typeof v==='number'&&Number.isFinite(v)?v:null}
  function bool(v){return typeof v==='boolean'?v:null}
  function list(v,limit=64){if(!Array.isArray(v))return[];const n=Math.min(num(own(v,'length'))||0,limit),a=[];for(let i=0;i<n;i++){const x=own(v,String(i));if(x!==undefined)a.push(x)}return a}
  function strings(v){return list(v).filter(x=>typeof x==='string').map(x=>x.slice(0,2000))}
  function parse(v){if(typeof v==='string'){try{return JSON.parse(v)}catch(e){return null}}return v&&typeof v==='object'?v:null}
  function iso(v){if(typeof v!=='number'&&typeof v!=='string')return null;const t=typeof v==='number'?v:Date.parse(v);if(!Number.isFinite(t)||t<=0)return null;try{return new Date(t).toISOString()}catch(e){return null}}
  function code(id){let n=typeof id==='number'&&Number.isSafeInteger(id)?id:0,s='';if(n<1)return'';while(n>0){n--;s=String.fromCharCode(65+n%26)+s;n=Math.floor(n/26)}return s}
  function selectFields(o,keys){const out={};for(const k of keys){const v=own(o,k);if(typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v))out[k]=v;else if(typeof v==='string'&&!/^data:|^blob:/i.test(v)&&!/-----BEGIN (?:[A-Z ]*PRIVATE KEY)-----/.test(v))out[k]=v.slice(0,2000)}return out}
  function validation(v){if(!v||typeof v!=='object')return null;const out=selectFields(v,['pass','score','candidateCount','passedCount','rejectedCount','generationCount','evolutionGain']);out.issues=strings(own(v,'issues'));out.hardIssues=strings(own(v,'hardIssues'));out.tests=list(own(v,'tests'),32).map(x=>typeof x==='string'?x.slice(0,2000):selectFields(x,['name','pass','passed','status']));return out}
  function circuit(v){if(!v||typeof v!=='object')return null;const out=selectFields(v,['tool','routeId','score','topologyHash','nodeCount','edgeCount','ticks','lane']);const source=own(v,'source');if(source&&typeof source==='object')out.source=selectFields(source,['kind','name','url','sha256','nodeCount','edgeCount']);return out}
  function review(v){if(!v||typeof v!=='object')return null;const out=selectFields(v,['accepted','score','count','candidateCount']);const c=circuit(own(v,'connectome'));if(c)out.connectome=c;const best=own(v,'best');if(best)out.selected=selectFields(best,['index','score','stage','strategy','scope']);return out}
  function fileResult(v){if(!v||typeof v!=='object')return null;const out=selectFields(v,['ok','jobId','status','frames','frameCount','fps','width','height','durationMs','name','bytes','shared','codec','mime','error','provider']);const uri=own(v,'fileUri')||own(v,'uri');if(typeof uri==='string'&&/^content:\/\/[^?\s]+$/.test(uri))out.fileUri=uri.slice(0,2000);const q=own(v,'quality');if(q){out.quality=selectFields(q,['kind','requests','retries','improved']);const scores=own(q,'scores');if(Array.isArray(scores))out.quality.scores=scores.slice(0,450).filter(x=>typeof x==='number'&&Number.isFinite(x));}return out}
  function projectOutput(v,tool){
    if(!v||typeof v!=='object')return null;
    const out=selectFields(v,['format','title','language','filename','edited','scope','sourceStatus','prompt','durationMs','fps','width','height','tracks','trackCount']);
    for(const k of ['text','body','summary','reply']){const s=own(v,k);if(typeof s==='string'&&!/^data:|^blob:/i.test(s)){out[k]=s.slice(0,MAX_TEXT);if(s.length>MAX_TEXT)out.truncated=true}}
    const valid=validation(own(v,'validation')),rev=review(own(v,'review'));
    if(valid)out.validation=valid;if(rev)out.review=rev;
    const search=own(v,'search');if(search)out.search=selectFields(search,['policy','maxCandidates','qualityTarget','stopReason','renderCalls','assessmentCalls','duplicateCount','cacheHit','generated','evaluated','unique','earlyStopped']);
    const selection=own(v,'selection');if(selection)out.selection=selectFields(selection,['id','parentId','generation','strategy','score']);
    const evidence=list(own(v,'evidence'),32);if(evidence.length)out.evidence=evidence.map(x=>selectFields(x,['index','text','sentence','score']));
    const tracks=own(v,'tracks');if(Array.isArray(tracks))out.trackCount=Math.min(num(own(tracks,'length'))||0,10000);
    const exported=fileResult(own(v,'exported')||own(v,'mp4'));if(exported)out.exported=exported;
    // Image values contain potentially large canvas/data URLs. Keep scene facts,
    // selection, and quality measurements instead of duplicating binary output.
    const inner=own(v,'value');if(inner&&/image|o2\./i.test(tool||''))out.image=selectFields(inner,['objects','width','height','seed','mode','format']);
    return out;
  }
  function captureGeneration(packet){
    if(!packet||typeof packet!=='object')return null;
    const out=selectFields(packet,['tool','status','error','learnedSkill']);
    const value=own(packet,'output')||own(packet,'value'),output=projectOutput(value,out.tool);
    if(output)out.output=output;
    const steps=list(own(packet,'steps'),32);if(steps.length)out.steps=steps.map(s=>{const row=selectFields(s,['tool','reward']);const c=circuit(own(s,'connectome'));if(c)row.connectome=c;const result=own(s,'result');row.ok=result?bool(own(result,'ok')):bool(own(s,'ok'));return row});
    return Object.keys(out).length?out:null;
  }
  function latestJob(jobId,lookup){
    if(!jobId)return null;let v=null;
    try{
      if(typeof lookup==='function')v=parse(lookup(jobId));
      else if(typeof root.AndroidVideo?.status==='function')v=parse(root.AndroidVideo.status(jobId));
      else if(typeof root.FFCVideoExport?.status==='function')v=parse(root.FFCVideoExport.status());
    }catch(e){}
    // The latest video may belong to a different conversation. Never attach it
    // unless the job identity matches this particular assistant message.
    return str(own(v,'jobId'))===jobId?fileResult(v):null;
  }
  function metadata(meta,opt){
    if(!meta||typeof meta!=='object')return null;const out=selectFields(meta,['tool','capability','reason','route','confidence','voice','error']);
    const attachments=list(own(meta,'attachments'),4);if(attachments.length)out.attachments=attachments.map(f=>({...selectFields(f,['name','type','size','truncated']),text:str(own(f,'text'),12000)}));
    const g=captureGeneration(own(meta,'generation'));if(g){const file=own(g.output,'exported'),jobId=str(own(file,'jobId'));const status=latestJob(jobId,opt.videoStatus);if(status)g.output.exported={...file,...status};out.generation=g}
    return Object.keys(out).length?out:null;
  }
  function readState(){
    const state=own(root.FFC_THREADS,'state');if(state&&Array.isArray(own(state,'threads')))return state;
    try{const s=parse(root.localStorage?.getItem(STORE));if(s&&Array.isArray(own(s,'threads')))return s}catch(e){}
    try{const legacy=parse(root.localStorage?.getItem('banc888_v32_chat_state')),history=own(legacy,'history');if(Array.isArray(history))return{activeId:'legacy',threads:[{id:'legacy',title:'以前の会話',messages:history}]}}catch(e){}
    return{activeId:null,threads:[]};
  }
  function buildPayload(opt={}){
    const state=own(opt,'state')||readState(),scope=own(opt,'scope')==='all'?'all':'current',activeId=own(state,'activeId');
    const rawThreads=own(state,'threads'),threads=list(rawThreads,MAX_THREADS);
    const selected=scope==='all'?threads:list(rawThreads,10000).filter(t=>own(t,'id')===activeId).slice(0,1);
    if(scope==='all'&&own(opt,'includeLegacy')!==false&&!selected.some(t=>own(t,'id')==='legacy')){try{const old=parse(root.localStorage?.getItem('banc888_v32_chat_state')),history=own(old,'history');if(Array.isArray(history)&&history.length)selected.push({id:'legacy',title:'以前の会話',messages:history})}catch{}}
    let budget=MAX_EXPORT_TEXT,truncated=scope==='all'&&(num(own(own(state,'threads'),'length'))||0)>MAX_THREADS;
    const conversations=selected.map(t=>{
      const raw=list(own(t,'messages'),MAX_MESSAGES),id=own(t,'id'),messages=raw.map((m,i)=>{
        const text=own(m,'text')??own(m,'content'),content=str(text,Math.max(0,Math.min(MAX_TEXT,budget)));budget-=content.length;
        const clipped=typeof text==='string'&&text.length>content.length;if(clipped)truncated=true;
        const role=str(own(m,'role'),32),msg={id:typeof own(m,'id')==='number'||typeof own(m,'id')==='string'?own(m,'id'):i+1,role:role==='bot'?'assistant':['user','assistant','system','tool'].includes(role)?role:'unknown',content,createdAt:iso(own(m,'time')??own(m,'t')??own(m,'createdAt'))};
        const meta=metadata(own(m,'meta')||own(m,'metadata'),opt);if(meta){const size=JSON.stringify(meta).length;if(size<=budget){msg.metadata=meta;budget-=size}else{truncated=true;msg.metadata={truncated:true};msg.truncated=true}}if(clipped)msg.truncated=true;return msg;
      });
      if((num(own(own(t,'messages'),'length'))||0)>MAX_MESSAGES)truncated=true;
      return{id:typeof id==='number'||typeof id==='string'?id:null,code:code(id),title:str(own(t,'title'),80)||messages.find(m=>m.role==='user')?.content.slice(0,80)||'新しい会話',createdAt:iso(own(t,'createdAt')),updatedAt:iso(own(t,'lastActive')),listenerEnabled:own(t,'listenerEnabled')!==false,messages};
    });
    const exportedAt=iso(own(opt,'now'))||new Date().toISOString();
    return{schema:SCHEMA,schemaVersion:VERSION,app:'BANC888',exportedAt,scope,activeConversationId:typeof activeId==='number'||typeof activeId==='string'?activeId:null,conversationCount:conversations.length,messageCount:conversations.reduce((n,t)=>n+t.messages.length,0),truncated,conversations};
  }
  function status(text){for(const id of ['ffcLogStatus','conversationJsonStatus']){const el=root.document?.getElementById(id);if(el)el.textContent=text}const share=root.document?.getElementById('conversationJsonShare');if(share)share.disabled=!lastSaved}
  function stamp(){return new Date().toISOString().replace(/[:.]/g,'-')}
  function save(text,name,mime){
    if(typeof root.AndroidFiles?.saveText==='function'){
      try{const result=parse(root.AndroidFiles.saveText(text,name,mime));if(result&&own(result,'ok')===true){lastSaved={name:str(own(result,'name'))||name,mime,result:fileResult(result)};return{ok:true,mode:'android-save',result:lastSaved.result}}if(own(result,'error')!=='native bridge unavailable')return{ok:false,error:str(own(result,'error'))||'ファイルを保存できませんでした。'}}catch(e){return{ok:false,error:'ファイルを保存できませんでした。'}}
    }
    try{const url=root.URL.createObjectURL(new root.Blob([text],{type:mime})),a=root.document.createElement('a');a.href=url;a.download=name;root.document.body?.appendChild(a);a.click();a.remove?.();root.setTimeout(()=>root.URL.revokeObjectURL(url),1000);lastSaved={text,name,mime};return{ok:true,mode:'download'}}catch(e){return{ok:false,error:'この環境ではファイルを保存できませんでした。'}}
  }
  function exportJson(opt={}){const payload=buildPayload(opt),result=save(JSON.stringify(payload,null,2),'BANC888_conversation_'+payload.scope+'_'+stamp()+'.json','application/json');status(result.ok?(payload.truncated?'会話JSONを保存しました。一部は保存上限により省略されています。':'会話JSONを保存しました。'):'JSON保存失敗: '+result.error);return result}
  function ndjson(opt={}){return buildPayload(opt).conversations.flatMap(t=>t.messages.map(m=>JSON.stringify({schema:SCHEMA,schemaVersion:VERSION,conversationId:t.id,...m}))).join('\n')+'\n'}
  function exportNdjson(opt={}){const result=save(ndjson(opt),'BANC888_conversation_'+stamp()+'.ndjson','application/x-ndjson');status(result.ok?'会話NDJSONを保存しました。':'NDJSON保存失敗: '+result.error);return result}
  async function copyJson(opt={}){try{await root.navigator.clipboard.writeText(JSON.stringify(buildPayload(opt),null,2));status('会話JSONをコピーしました。');return{ok:true,mode:'clipboard'}}catch(e){status('クリップボードを利用できません。JSON保存を使ってください。');return{ok:false,error:'clipboard-unavailable'}}}
  async function shareLast(){
    if(!lastSaved){const r=exportJson();if(!r.ok)return r}
    if(typeof root.AndroidFiles?.shareSaved==='function'){try{const r=parse(root.AndroidFiles.shareSaved(lastSaved.name,lastSaved.mime));status(own(r,'ok')===true?'共有先を選んでください。':'JSON共有を開始できませんでした。');return r||{ok:false,error:'share-failed'}}catch(e){return{ok:false,error:'share-failed'}}}
    try{const file=new root.File([lastSaved.text],lastSaved.name,{type:lastSaved.mime});if(root.navigator?.canShare?.({files:[file]})){await root.navigator.share({files:[file]});return{ok:true,mode:'share'}}}catch(e){return{ok:false,error:'share-cancelled'}}
    status('JSONをダウンロードしました。保存したファイルから共有できます。');return{ok:true,mode:'download'};
  }
  function bind(){
    if(!root.document?.addEventListener)return;
    root.addEventListener?.('ffc:conversation-ui-ready',()=>{const button=root.document.getElementById('conversationJsonShare');if(button)button.disabled=!lastSaved});
    root.document.addEventListener('click',e=>{
      const id=e.target?.closest?.('button')?.id||e.target?.id;
      const actions={conversationJsonExport:()=>exportJson(),conversationJsonExportAll:()=>exportJson({scope:'all'}),conversationJsonShare:shareLast,ffcExportActiveJson:()=>exportJson(),ffcExportAllJson:()=>exportJson({scope:'all'}),ffcExportNdjson:()=>exportNdjson({scope:'all'}),ffcCopyJson:()=>copyJson({scope:'all'})};
      if(actions[id]){e.preventDefault();e.stopImmediatePropagation();actions[id]()}
    },true);
    // Keep existing developer-console export entry points on the same safe
    // schema rather than exposing their previous arbitrary metadata copies.
    for(const target of [root.FFC_THREADS,root.BANC888_CONVERSATION_LOGS])if(target){target.snapshot=opt=>buildPayload({...opt,scope:opt?.scope==='active'?'current':opt?.scope||'all'});target.json=opt=>JSON.stringify(target.snapshot(opt),null,2);target.exportJSON=opt=>exportJson({...opt,scope:opt?.scope==='active'?'current':opt?.scope||'all'});target.exportNDJSON=exportNdjson;target.ndjson=ndjson;target.copyJSON=copyJson}
  }
  bind();
  return{schema:SCHEMA,schemaVersion:VERSION,buildPayload,captureGeneration,export:exportJson,shareLast,copyJSON:copyJson,exportNDJSON:exportNdjson};
});
