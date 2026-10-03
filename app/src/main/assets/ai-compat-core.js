(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.FFCAICompat=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const VERSION='1.0',MAX_MESSAGES=32,MAX_INPUT=12000,MAX_CONTEXT=36000,MAX_SOURCE=24000;
const text=x=>typeof x==='string'?x:'';
const normalized=x=>text(x).normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim();
function failure(code,message){const e=new Error(message);e.code=code;throw e}
// A documented local JSON subset, not a claim of complete provider API parity.
function normalizeRequest(request){
 if(!request||typeof request!=='object'||Array.isArray(request))failure('invalid_request','JSON object required');
 if(request.stream===true)failure('unsupported_feature','streaming is not supported');
 for(const k of ['tools','tool_choice','response_format','temperature','max_tokens','model'])if(request[k]!==undefined)failure('unsupported_feature','unsupported field: '+k);
 if(!Array.isArray(request.messages)||!request.messages.length||request.messages.length>MAX_MESSAGES)failure('invalid_messages','messages must contain 1 to 32 text messages');
 let total=0;
 const messages=request.messages.map(m=>{
  if(!m||!['user','assistant'].includes(m.role)||typeof m.content!=='string')failure('unsupported_message','only user/assistant text messages are supported');
  if(m.content.length>MAX_INPUT)failure('input_too_large','each message is limited to 12000 characters');
  total+=m.content.length;return{role:m.role,content:m.content};
 });
 if(total>MAX_CONTEXT)failure('input_too_large','message text exceeds 36000 characters');
 const last=messages[messages.length-1];
 if(last.role!=='user'||!last.content.trim())failure('invalid_messages','the last message must be a nonempty user request');
 if(request.source!==undefined&&typeof request.source!=='string')failure('invalid_source','source must be text');
 if(text(request.source).length>MAX_SOURCE)failure('source_too_large','source exceeds 24000 characters');
 if(request.threadCode!==undefined&&(typeof request.threadCode!=='string'||!request.threadCode||request.threadCode.length>256))failure('invalid_thread','invalid threadCode');
 return{text:last.content,ctx:{threadCode:request.threadCode||'COMPAT',context:messages.slice(0,-1).map(m=>(m.role==='user'?'人間':'ハエ')+': '+m.content).join('\n'),source:text(request.source),attachedNow:!!request.source}};
}
function domain(option){
 const p=option.plan||{},tool=option.proposal?.steps?.[0]?.tool||p.tool||'';
 if(p.domain)return p.domain;
 if(tool==='content.understand')return'content';
 if(tool==='compute.math')return'math';
 if(tool==='chat.compose')return'chat';
 return tool.split('.')[0]||'unknown';
}
function terms(query){
 const q=normalized(query).replace(/要約|まとめて|教えて|ください|お願いします|本文|資料|内容|質問|答えて|前の|さっき|について|とは|ですか|ますか|する|して|summari[sz]e|please|tell me|what is/g,' ');
 const tokens=q.match(/[a-z0-9_]{2,}|[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]{2,}/gu)||[];
 const all=[];for(const token of tokens){if(/^[a-z0-9_]+$/.test(token))all.push(token);else for(let i=0;i<token.length-1;i++)all.push(token.slice(i,i+2))}
 return[...new Set(all)].slice(0,64);
}
function selectInformation(query,ctx,select){
 const input=text(ctx.context).slice(-MAX_CONTEXT),keys=terms(query);
 // Context is already scoped to one thread by the caller. No global history read.
 const lines=input.split(/\n+/).map((value,index)=>({index,text:value.trim()})).filter(x=>x.text).slice(-48);
 const ranked=lines.map(x=>({...x,relevance:keys.filter(k=>normalized(x.text).includes(k)).length}));
 const bestRelevance=Math.max(0,...ranked.map(x=>x.relevance));
 let eligible=ranked.filter(x=>x.relevance>0&&x.relevance>=Math.max(1,bestRelevance*.5));
 const fallback=!eligible.length;if(fallback)eligible=ranked.slice(-4);
 const chosen=[],traces=[];
 while(eligible.length&&chosen.length<6){
  const top=Math.max(...eligible.map(x=>x.relevance));
  const ties=eligible.filter(x=>x.relevance===top);
  const pick=select(ties.map(x=>({tool:'content.understand',excitation:1,confidence:1,recordIndex:x.index,routeId:/ため|ので|because/i.test(x.text)?'o1p2':/とは|です|\bis\b|\bmeans\b/i.test(x.text)?'o1p1':'o1p3'})));
  if(!pick)break;
  const found=eligible.find(x=>x.index===pick.recordIndex);if(!found)break;
  chosen.push(found);traces.push(pick.connectome);eligible=eligible.filter(x=>x.index!==found.index);
 }
 chosen.sort((a,b)=>a.index-b.index);
 return{context:chosen.map(x=>x.text).join('\n'),source:text(ctx.source),inputRecords:lines.length,selected:chosen.map(x=>({index:x.index,text:x.text,relevance:x.relevance})),fallback,connectome:traces,sourcePolicy:'explicit source is preserved; history is selected within current thread'};
}
function prepare(query,ctx,options,select){
 if(!Array.isArray(options)||!options.length)return null;
 // Meaning/IR candidates come from existing engineering adapters. Circuit
 // output chooses among applicable domains; this is not learned human semantics.
 const groups=new Map();
 for(const o of options){const d=domain(o),old=groups.get(d);if(!old||(+o.proposal.confidence||0)>(+old.proposal.confidence||0))groups.set(d,o)}
 const instruction=select([...groups].map(([d,o])=>({tool:o.proposal.steps[0].tool,excitation:1,confidence:o.proposal.confidence??1,domain:d})));
 if(!instruction)return{blocked:true,reason:'instruction has no active connectome output',compatibility:{schema:1,instruction:{candidates:groups.size,selected:null},information:null,plan:null}};
 const information=selectInformation(query,ctx,select);
 const applicable=options.filter(o=>domain(o)===instruction.domain);
 const plan=select(applicable.map((o,i)=>({tool:o.proposal.steps[0].tool,excitation:1,confidence:o.proposal.confidence??1,proposalIndex:i})));
 if(!plan)return{blocked:true,reason:'plan has no active connectome output'};
 const option=applicable[plan.proposalIndex];
 const compatibility={schema:1,version:VERSION,scope:'engineered adapters with BANC aggregate circuit decisions',instruction:{selected:instruction.domain,tool:instruction.tool,candidates:groups.size,inputAdapter:'existing vocabulary, IR and acquired-skill adapters',connectome:instruction.connectome},information,plan:{candidates:applicable.length,tools:option.proposal.steps.map(s=>s.tool),connectome:plan.connectome}};
 return{...option,plan:{...option.plan,connectome:plan.connectome,connectomeCandidates:options.length},proposal:{...option.proposal,steps:option.proposal.steps.map(s=>({...s,args:{...s.args,context:information.context}}))},compatibility};
}
function audit(c){
 if(!c)return null;
 return{schema:1,instruction:c.instruction?.selected||null,tool:c.instruction?.tool||null,selectedRecords:c.information?.selected?.map(x=>x.index)||[],inputRecords:c.information?.inputRecords||0,contextFallback:!!c.information?.fallback,planTools:c.plan?.tools||[],routes:[c.instruction?.connectome?.route,...(c.information?.connectome||[]).map(x=>x.route),c.plan?.connectome?.route].filter(Boolean),provenance:c.instruction?.connectome?.provenance||null};
}
function response(result){
 const ok=result?.handled&&result.status==='done';
 const value=result?.value,content=typeof value?.text==='string'?value.text:typeof value?.body==='string'?value.body:String(result?.finalText||'');
 return{object:'banc888.compat.response',schema:1,status:result?.status||(result?.handled?'failed':'unsupported'),choices:ok?[{index:0,message:{role:'assistant',content},finish_reason:'stop'}]:[],tool:result?.tool||null,compatibility:result?.compatibility||null,validation:value?.validation||null,error:ok?null:{code:result?.status||'unsupported',message:result?.error||result?.finalText||'unsupported request'}};
}
return{VERSION,normalizeRequest,terms,selectInformation,prepare,audit,response,limits:{messages:MAX_MESSAGES,inputCharacters:MAX_INPUT,contextCharacters:MAX_CONTEXT,sourceCharacters:MAX_SOURCE}};
});
