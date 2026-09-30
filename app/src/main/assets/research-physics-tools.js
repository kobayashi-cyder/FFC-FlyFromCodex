(() => {
'use strict';
const R=window.FFCResearchPhysics,Agent=window.BANC888_FLY_AGENT;
if(!R||!Agent||window.FFC_RESEARCH_PHYSICS_TOOLS)return;
const st=Agent.state;
const reg=(name,description,capability,sideEffect,handler)=>{st.capabilities.add(capability);st.tools.set(name,{name,description,capability,sideEffect,handler})};
const parse=s=>{try{return JSON.parse(String(s||''))}catch{return{ok:false,error:'invalid native JSON'}}};
const networkAllowed=()=>{try{return !!window.FFC_PROXY_AGENT?.policy?.allows?.('network.read')}catch{return false}};
const compose=goal=>{try{const r=Agent.execute('chat.compose',{goal});return r&&r.ok?String(r.value?.reply||''):''}catch{return''}};
const esc=s=>String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

function wiki(query,limit=3){
 if(!networkAllowed())return{ok:false,error:'network.read blocked'};
 if(!window.AndroidResearch)return{ok:false,error:'native research bridge unavailable'};
 const x=parse(AndroidResearch.searchWikipedia(String(query||''),Math.max(1,Math.min(5,+limit||3))));
 if(!x.ok)return x;
 x.results=Array.isArray(x.results)?x.results:[];
 return x;
}
function crossref(query,limit=3){
 if(!networkAllowed())return{ok:false,error:'network.read blocked'};
 if(!window.AndroidResearch)return{ok:false,error:'native research bridge unavailable'};
 const x=parse(AndroidResearch.searchCrossref(String(query||''),Math.max(1,Math.min(5,+limit||3))));
 if(!x.ok)return x;
 x.results=Array.isArray(x.results)?x.results:[];
 return x;
}
function collect(ir){
 const result={ok:false,query:ir.query,sources:[],fetchedAt:new Date().toISOString(),provider:null};
 if((ir.sourceModes||[]).includes('crossref')){
   const x=crossref(ir.query,ir.maxSources||3);result.provider='crossref';
   for(const it of x.results||[])result.sources.push({kind:'crossref',title:it.title||'Crossref work',url:it.url||'',text:String(it.abstract||it.summary||'').slice(0,7000),published:it.published||null});
 }else{
   const x=wiki(ir.query,ir.maxSources||3);result.provider='wikipedia';
   for(const it of x.results||[])result.sources.push({kind:'wikipedia',title:it.title||'',url:it.url||'',text:String(it.extract||it.snippet||'').slice(0,7000),published:null});
 }
 result.ok=result.sources.length>0;if(!result.ok)result.error='no sources collected';return result;
}
function sourceList(col){return(col.sources||[]).map((s,i)=>'- [S'+(i+1)+'] '+s.title+' — '+s.url).join('\n')}
function evidence(col){return(col.sources||[]).map((s,i)=>'[S'+(i+1)+'] '+s.title+'\nURL: '+s.url+'\n'+String(s.text||'')).join('\n\n---\n\n')}
function exportDoc(format,title,body){
 if(format==='docx'&&window.AndroidFiles)return parse(AndroidFiles.createDocx(title,body,'BANC888_research_'+Date.now()+'.docx'));
 const ext=format==='markdown'?'md':format==='html'?'html':'txt',mime=format==='markdown'?'text/markdown':format==='html'?'text/html':'text/plain';
 let out=body;if(format==='html')out='<!doctype html><html lang="ja"><meta charset="utf-8"><body><pre style="white-space:pre-wrap">'+esc(body)+'</pre></body></html>';
 return window.AndroidFiles?parse(AndroidFiles.shareText(out,'BANC888_research_'+Date.now()+'.'+ext,mime)):{ok:false,error:'native file bridge unavailable'};
}
function validateResearch(body,col){const issues=[];if(!(col.sources||[]).length)issues.push('no-sources');if(String(body||'').length<300)issues.push('too-short');if((col.sources||[]).length&&!/\[S\d+\]/.test(body))issues.push('missing-citation');return{pass:issues.length===0,issues,sources:(col.sources||[]).length}}
function researchBody(ir,col,phys){
 const inst='以下のEvidenceだけを事実根拠として日本語資料を作成。根拠がない事実は断定しない。本文の事実には [S1] のような出典番号を付ける。'+(phys?'物理推定はInferenceとして分離し、仮定・式・不確かさを明記する。':'');
 let body=compose(inst+'\n\n要求:\n'+ir.request+'\n\nEvidence:\n'+evidence(col)+(phys?'\n\nInference:\n'+JSON.stringify(phys,null,2):''));
 if(body.length<300){
   body='# 調査資料\n\n## 調査対象\n'+ir.query+'\n\n## Evidence\n'+(col.sources||[]).map((s,i)=>'### [S'+(i+1)+'] '+s.title+'\n'+String(s.text||'').slice(0,1800)).join('\n\n');
 }
 if(phys)body+='\n\n## 物理推定（Inference）\n- モデル: '+phys.model+'\n- 式: '+(phys.equation||'条件不足')+'\n- 中央値: '+(phys.value==null?'定量条件不足':phys.value+' '+phys.unit)+'\n- 概算幅: '+(phys.low==null?'—':phys.low+' ～ '+phys.high+' '+phys.unit)+'\n- 仮定: '+(phys.assumptions||[]).join(' / ');
 body+='\n\n## 出典\n'+sourceList(col)+'\n\n> Web由来の事実と物理推定は別レイヤーです。推定値は仮定に依存します。\n';
 return body;
}
function researchDocument(a,withPhysics){
 const ir=a.ir?.research||a.ir||R.buildResearchIR(a.prompt||''),col=collect(ir),phys=withPhysics?R.estimate(a.ir?.physics||R.buildPhysicsIR(a.prompt||'')):null,format=a.ir?.format||ir.format||'docx';
 const body=researchBody(ir,col,phys),validation=validateResearch(body,col),exported=exportDoc(format,'調査・推定資料',body);
 return{format,ir,research:col,physics:phys,body,validation,exported};
}
function physicsDocument(a){
 const ir=a.ir||R.buildPhysicsIR(a.prompt||''),phys=R.estimate(ir),format=ir.format||'docx';
 const body='# 物理推定資料\n\n## 目的\n'+ir.request+'\n\n## 入力条件\n'+(ir.quantities||[]).map(x=>'- '+x.raw+' → SI '+x.si+' ('+x.dim+')').join('\n')+'\n\n## モデルと式\n- モデル: '+phys.model+'\n- 式: '+(phys.equation||'条件不足')+'\n\n## 推定結果\n- 中央値: '+(phys.value==null?'定量化に必要な条件が不足':phys.value+' '+phys.unit)+'\n- 概算幅: '+(phys.low==null?'—':phys.low+' ～ '+phys.high+' '+phys.unit)+'\n\n## 仮定\n'+(phys.assumptions||[]).map(x=>'- '+x).join('\n')+'\n\n## 限界\n'+(phys.notes||[]).map(x=>'- '+x).join('\n')+'\n\n> これは物理モデルに基づくInferenceであり、実測値ではありません。\n';
 return{format,ir,physics:phys,body,validation:{pass:phys.value!=null,issues:phys.value==null?['insufficient-conditions']:[]},exported:exportDoc(format,'物理推定資料',body)};
}
reg('research.collect','read-only topic research through bounded Wikipedia/Crossref providers','network.read','network',a=>collect(a.ir||R.buildResearchIR(a.prompt||'')));
reg('physics.estimate','SI-normalized physics estimation with equations assumptions and uncertainty','compute','none',a=>R.estimate(a.ir||R.buildPhysicsIR(a.prompt||'')));
for(const f of ['docx','markdown','html','text']){
 reg('document.research.'+f,'web evidence -> citation-aware document','document.write','native',a=>researchDocument({...a,ir:{...(a.ir||{}),format:f}},false));
 reg('document.physics.'+f,'physics inference -> documented assumptions/equations/result','document.write','native',a=>physicsDocument({...a,ir:{...(a.ir||{}),format:f}}));
 reg('document.research_physics.'+f,'web evidence + separate physics inference -> document','document.write','native',a=>researchDocument({...a,ir:{...(a.ir||{}),format:f}},true));
}
window.FFC_RESEARCH_PHYSICS_TOOLS={version:'1.0',collect,researchDocument,physicsDocument,estimate:R.estimate};
})();