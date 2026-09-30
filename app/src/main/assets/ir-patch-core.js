(function(root,factory){
 const api=factory(); if(typeof module==='object'&&module.exports)module.exports=api; root.FFCIrPatch=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
 'use strict';
 const clone=v=>v==null?v:JSON.parse(JSON.stringify(v));
 const split=p=>Array.isArray(p)?p:String(p||'').split('.').filter(Boolean);
 function get(obj,path){let x=obj;for(const k of split(path)){if(x==null)return undefined;x=x[k]}return x}
 function set(obj,path,value){const ks=split(path);if(!ks.length)throw new Error('path required');let x=obj;for(let i=0;i<ks.length-1;i++){const k=ks[i];if(!x[k]||typeof x[k]!=='object')x[k]={};x=x[k]}x[ks[ks.length-1]]=clone(value)}
 function del(obj,path){const ks=split(path);if(!ks.length)return;let x=obj;for(let i=0;i<ks.length-1;i++){x=x&&x[ks[i]];if(!x)return}if(x)delete x[ks[ks.length-1]]}
 function stable(v){if(Array.isArray(v))return'['+v.map(stable).join(',')+']';if(v&&typeof v==='object')return'{'+Object.keys(v).sort().map(k=>JSON.stringify(k)+':'+stable(v[k])).join(',')+'}';return JSON.stringify(v)}
 function hash(v){let h=2166136261,s=stable(v);for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}return(h>>>0).toString(16).padStart(8,'0')}
 const LIMIT={micro:1,meso:4,macro:16};
 function apply(ir,ops,scope='meso'){
  if(!ir||typeof ir!=='object')throw new Error('IR object required');
  const out=clone(ir),before=clone(ir),max=LIMIT[scope]||LIMIT.meso,list=Array.isArray(ops)?ops:[];
  if(list.length>max)throw new Error('patch exceeds '+scope+' limit '+max);
  const changed=[];
  for(const op of list){
   const path=String(op&&op.path||'');if(!path||path==='kind'||path.startsWith('kind.'))throw new Error('protected path');
   const prev=clone(get(out,path));const kind=op.op||'set';
   if(kind==='set')set(out,path,op.value);
   else if(kind==='merge'){const cur=get(out,path);set(out,path,Object.assign({},cur&&typeof cur==='object'?cur:{},clone(op.value||{})))}
   else if(kind==='append'){const cur=get(out,path);const a=Array.isArray(cur)?cur.slice():[];a.push(clone(op.value));set(out,path,a)}
   else if(kind==='remove')del(out,path);
   else if(kind==='replace'){const cur=String(get(out,path)||'');set(out,path,cur.split(String(op.from||'')).join(String(op.to||'')))}
   else throw new Error('unknown patch op '+kind);
   const next=clone(get(out,path));if(stable(prev)!==stable(next))changed.push({path,before:prev,after:next,op:kind});
  }
  out.kind=before.kind;return{ir:out,scope,changed,beforeHash:hash(before),afterHash:hash(out)};
 }
 function nonDefaultDiff(oldIr,newIr){
  const ops=[];if(!oldIr||!newIr||oldIr.kind!==newIr.kind)return ops;
  const candidate=['language','frameworks','recipes','constraints','quality','export','filename','action','size','aspect','composition','lighting','style','palette','negative','operation','rate','pitch','continuous','format','type','title','audience','tone','sections'];
  for(const k of candidate){const v=newIr[k],old=oldIr[k];if(v==null)continue;if(Array.isArray(v)&&!v.length)continue;if(typeof v==='string'&&(v===''||v==='normal'||v==='general'||v==='balanced'||v==='auto'))continue;if(stable(v)!==stable(old))ops.push({op:'set',path:k,value:v})}
  return ops;
 }
 function infer(previous,request,V,scope='meso'){
  if(!previous||!previous.kind||!V)return{ir:clone(previous),scope,changed:[],beforeHash:hash(previous),afterHash:hash(previous)};
  let fresh=null;
  if(previous.kind==='CodeIR'&&V.buildCodeIR)fresh=V.buildCodeIR(request);
  else if(previous.kind==='ImageIR'&&V.buildImageIR)fresh=V.buildImageIR(request);
  else if(previous.kind==='SpeechIR'&&V.buildVoiceIR)fresh=V.buildVoiceIR(request);
  else if(previous.kind==='DocumentIR'&&V.buildDocumentIR)fresh=V.buildDocumentIR(request);
  let ops=nonDefaultDiff(previous,fresh||{});
  const t=String(request||'').normalize('NFKC').toLowerCase();
  if(previous.kind==='ImageIR'){
   if(/もっと明る|明るく/.test(t))ops.push({op:'append',path:'lighting',value:'brighter'});
   if(/もっと暗|暗く/.test(t))ops.push({op:'append',path:'lighting',value:'darker'});
   if(/被写体.*そのまま|構図だけ/.test(t))ops=ops.filter(x=>['composition','aspect','size'].includes(x.path));
  }
  if(previous.kind==='DocumentIR'){
   const m=String(request||'').match(/(?:章|セクション)[「『"]?([^」』"]{2,40})[」』"]?(?:を)?追加/);if(m)ops.push({op:'append',path:'sections',value:m[1]});
  }
  if(previous.kind==='CodeIR'&&/言語はそのまま|同じ言語/.test(t))ops=ops.filter(x=>x.path!=='language');
  const uniq=[];const seen=new Set();for(let i=ops.length-1;i>=0;i--){if(!seen.has(ops[i].path)){seen.add(ops[i].path);uniq.unshift(ops[i])}}
  return apply(previous,uniq.slice(0,LIMIT[scope]||4),scope);
 }
 return{clone,get,apply,infer,hash,limits:Object.assign({},LIMIT)};
});