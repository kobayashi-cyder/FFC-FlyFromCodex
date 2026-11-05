(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.FFCCognitionMultimodal=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const VERSION='1.0',KINDS=['chat','code','image','video'];
const TOOL={chat:'chat.compose',code:'code.generate',image:'image.generate',video:'o3.generate'};
const text=v=>typeof v==='string'?v:'';
const fail=(code,why)=>({pass:false,level:'not_verified',code,reason:String(why||code).slice(0,500)});
const pass=(level,detail)=>({pass:true,level,detail});
function kind(tool){
 if(tool==='chat.compose')return'chat';
 if(/^code\.(generate|revise|debug|refactor|test|convert|optimize)$/.test(tool))return'code';
 if(/^image\.(generate|refine)$/.test(tool))return'image';
 if(tool==='o3.generate')return'video';
 return null;
}
function composite(textValue){
 const input=text(textValue).normalize('NFKC');
 if(!/(全部|すべて|一括|同時|四つ|4つ|それぞれ|each|all four|all of)/i.test(input)||!/(作|生成|書|出力|create|generate|make|produce)/i.test(input))return[];
 const patterns={chat:/(chat|チャット|会話|文章)/i,code:/(コード|プログラム|coding|code)/i,image:/(画像|イラスト|image|picture)/i,video:/(動画|映像|アニメ|video|movie)/i};
 const modes=KINDS.filter(k=>patterns[k].test(input));
 return modes.length>=2?modes:[];
}
function classify(prompt,externalTool){
 const multi=composite(prompt);
 if(multi.length)return{modes:multi,source:'explicit_multipart_request'};
 const direct=kind(externalTool);if(direct)return{modes:[direct],source:'existing_specialist_router',tools:{[direct]:externalTool}};
 return{modes:[],source:'unsupported'};
}
function task(prompt,selected,{ctx={},ir=null}={}){
 const modes=Array.isArray(selected?.modes)?selected.modes:[];
 if(!modes.length||modes.length>4||modes.some(m=>!KINDS.includes(m))||new Set(modes).size!==modes.length)throw new Error('unsupported cognition modes');
 const p=text(prompt);if(!p.trim()||p.length>12000)throw new Error('empty or too long request');
 const state=Object.fromEntries(modes.map(m=>[m,'pending']));
 const goal=Object.fromEntries(modes.map(m=>[m,'done']));
 const actions=modes.map(mode=>{
  const tool=selected?.tools?.[mode]||TOOL[mode];
  const args={goal:p,prompt:p,context:text(ctx.context).slice(-12000),source:text(ctx.source).slice(0,12000),threadCode:text(ctx.threadCode||'COGNITION')};
  if(modes.length===1&&ir)args.ir=ir;
  return{id:'deliver-'+mode,tool,requires:{[mode]:'pending'},effects:{[mode]:'done'},args,cost:1};
 });
 return{schema:1,facts:state,goal,actions,maxDepth:Math.min(8,modes.length+2),maxStates:128};
}
function audit(tool,result){
 const mode=kind(tool);if(!mode)return fail('unsupported_tool','unknown tool type');
 if(!result||result.ok!==true)return fail('tool_error',result?.error||'tool did not report success');
 const v=result.value;
 if(!v||typeof v!=='object')return fail('missing_artifact','tool returned no structured result');
 if(mode==='chat'){
  const body=text(v.reply);
  if(!body.trim())return fail('empty_reply','chat response is blank');
  return pass('nonempty_reply_only',{provider:text(v.provider||'internal'),characters:body.length,claim:'reply not independently fact-checked'});
 }
 if(mode==='code'){
  if(!text(v.text).trim())return fail('empty_code','no code emitted');
  if(v.validation?.pass!==true||v.review?.accepted!==true)return fail('code_validation_failed','code must pass local review and checks');
  if(/scaffold|unimplemented|placeholder/i.test(text(v.implementationScope)))return fail('scaffold_only','incomplete code scaffold');
  if(/BANC888 generated scaffold|request implementation unavailable/i.test(text(v.text)))return fail('scaffold_only','template is not a complete implementation');
  return pass('static_review_only',{language:text(v.language||'unknown'),characters:v.text.length,claim:'not proven to compile or pass runtime tests'});
 }
 if(mode==='image'){
  if(v.validation?.pass!==true)return fail('image_validation_failed','image candidate has not passed local quality gates');
  if(!v.validation?.scene&&!v.selection&&!v.image&&!v.canvas&&!v.validation?.objects)return fail('missing_image_artifact','no render evidence');
  return pass('procedural_render_quality',{score:v.validation.score,provider:'O2',claim:'local quality metrics do not prove photographic fidelity'});
 }
 const exported=v.exported;
 if(!exported||exported.ok!==true||exported.status!=='complete')return fail('mp4_not_complete',exported?.error||'video encoding is not complete');
 if(!Number.isFinite(exported.bytes)||exported.bytes<=0)return fail('empty_mp4','video output has no verified bytes');
 if(!Number.isInteger(exported.frameCount)||!Number.isInteger(exported.framesWritten)||exported.framesWritten!==exported.frameCount)return fail('incomplete_mp4','missing encoded frames');
 return pass('mp4_encoding_confirmed',{bytes:exported.bytes,frames:exported.framesWritten,provider:text(v.provider||exported.provider||'O3'),claim:'MP4 encoding success does not certify visual content'});
}
async function finish(tool,result,{videoExport=null,a1111Video=null}={}){
 if(kind(tool)!=='video'||result?.ok!==true)return{result,audit:audit(tool,result)};
 const value=result?.value;
 if(!value||!value.exported)return{result,audit:audit(tool,result)};
 const state=value.exported.status;
 if(state!=='generating'&&state!=='encoding')return{result,audit:audit(tool,result)};
 const video=value.exported.provider==='a1111'||value.provider==='a1111'?a1111Video:videoExport;
 if(!video||typeof video.whenComplete!=='function')return{result,audit:fail('missing_video_exporter','video exporter completion hook missing')};
 let settled;
 try{settled=await video.whenComplete()}catch(e){return{result,audit:fail('video_export_failed',String(e?.message||e))}}
 const merged={...value,exported:settled};
 if(value.exported.provider==='a1111'||value.provider==='a1111')merged.provider='a1111';
 const completed={...result,value:merged};
 return{result:completed,audit:audit(tool,completed)};
}
function report(outputs,trace){
 const parts=[];
 for(const row of outputs){
  const kindLabel={chat:'会話',code:'コード',image:'画像',video:'動画'}[row.mode]||row.mode;
  const check=row.verification;
  if(!check?.pass){parts.push(kindLabel+': 未完了（'+(check?.reason||'不明なエラー')+'）');continue}
  const value=row.value||{};
  let summary=kindLabel+': 完了 '+check.level;
  if(row.mode==='chat')summary+='\n'+text(value.reply).slice(0,16000);
  if(row.mode==='code')summary+='\n\n'+text(value.text).slice(0,24000);
  if(row.mode==='image')summary+='（O2による画像描画。写真生成モデルではありません）';
  if(row.mode==='video')summary+='（MP4 '+(value.exported?.bytes||0)+' bytes）';
  parts.push(summary);
 }
 if(!parts.length)return'生成できる機能が見つかりませんでした。';
 const missing=(trace||[]).filter(x=>!x.ok).length;
 return parts.join('\n\n')+(missing?'\n\n未検証または失敗した操作: '+missing:'');
}
return{VERSION,KINDS,TOOL,kind,composite,classify,task,audit,finish,report};
});
