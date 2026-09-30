(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCThreadCore=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const STOP=new Set(['これ','それ','あれ','ここ','そこ','ため','こと','もの','よう','です','ます','する','して','した','ある','いる','から','まで','ので','でも','あと','では','the','and','for','with','this','that','from','into','thread','スレッド']);
  const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
  function excelCode(n){
    n=Math.trunc(+n||0);if(n<1)return'';let s='';
    while(n>0){n--;s=String.fromCharCode(65+n%26)+s;n=Math.floor(n/26)}
    return s;
  }
  function codeToId(code){
    code=String(code||'').trim().toUpperCase();if(!/^[A-Z]+$/.test(code))return null;
    let n=0;for(const ch of code)n=n*26+(ch.charCodeAt(0)-64);return n||null;
  }
  function normalize(text){return String(text||'').normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim()}
  function extractTerms(text){
    const t=normalize(text), out=new Map();
    const add=(v,w=1)=>{v=String(v||'').trim();if(v.length<2||STOP.has(v))return;out.set(v,(out.get(v)||0)+w)};
    for(const m of t.match(/[a-z0-9_][a-z0-9_.+#-]{1,}/g)||[])add(m,1.2);
    for(const run of t.match(/[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}ー]{2,}/gu)||[]){
      if(run.length<=8)add(run,1.35);
      const max=Math.min(run.length,18);
      for(let i=0;i<max-1;i++)add(run.slice(i,i+2),.55);
      for(let i=0;i<max-2;i++)add(run.slice(i,i+3),.75);
    }
    return [...out.entries()].sort((a,b)=>b[1]-a[1]).slice(0,64);
  }
  function learn(thread,text,role='user'){
    thread.termFreq=thread.termFreq&&typeof thread.termFreq==='object'?thread.termFreq:{};
    for(const k of Object.keys(thread.termFreq))thread.termFreq[k]*=.985;
    const mul=role==='user'?1:.55;
    for(const [term,w] of extractTerms(text))thread.termFreq[term]=clamp((thread.termFreq[term]||0)+w*mul,0,12);
    const entries=Object.entries(thread.termFreq).filter(([,v])=>v>.08).sort((a,b)=>b[1]-a[1]).slice(0,96);
    thread.termFreq=Object.fromEntries(entries);return thread.termFreq;
  }
  function explicitTargets(text,threads){
    const raw=String(text||''), upper=raw.toUpperCase(), ids=[];
    if(/(?:全|すべて|全部)(?:の)?スレッド/.test(raw))return threads.filter(t=>t.listenerEnabled!==false).map(t=>t.id);
    for(const t of threads){const c=excelCode(t.id);if(!c)continue;
      const re1=new RegExp('(?:スレッド|THREAD)\\s*'+c+'(?:\\b|[へにを:：])','i');
      const re2=new RegExp('^\\s*'+c+'\\s*[:：]','i');
      if(re1.test(upper)||re2.test(upper))ids.push(t.id);
    }
    return ids;
  }
  function scoreThread(thread,text,state,now=Date.now()){
    if(thread.listenerEnabled===false)return -Infinity;
    const exp=explicitTargets(text,[thread]);if(exp.length)return 10;
    const terms=extractTerms(text), tf=thread.termFreq||{};let overlap=0,total=0,best=0;
    for(const [term,w] of terms){total+=w;const known=tf[term]||0;overlap+=Math.min(w,known);best=Math.max(best,Math.min(1,known/2)*Math.min(1,w))}
    const lexical=Math.max(total?overlap/total:0,best*.82);
    const age=Math.max(0,now-(thread.lastActive||thread.createdAt||now));
    const recency=Math.exp(-age/(12*60*1000));
    const active=state.activeId===thread.id?1:0;
    const pending=thread.pending?1:0;
    const queued=Array.isArray(thread.queue)?Math.min(1,thread.queue.length/3):0;
    return lexical*.62+recency*.13+active*.16+pending*.07-queued*.04;
  }
  function route(state,text,now=Date.now()){
    const threads=(state.threads||[]).filter(t=>t.closed!==true&&t.listenerEnabled!==false);
    if(!threads.length)return{threadIds:[],primaryId:null,confidence:0,reason:'no-listeners',ambiguous:true,scores:[]};
    const explicit=explicitTargets(text,threads);
    if(explicit.length)return{threadIds:explicit,primaryId:explicit[0],confidence:1,reason:explicit.length>1?'explicit-multicast':'explicit',ambiguous:false,scores:explicit.map(id=>({id,score:10}))};
    if(threads.length===1)return{threadIds:[threads[0].id],primaryId:threads[0].id,confidence:1,reason:'single-listener',ambiguous:false,scores:[{id:threads[0].id,score:1}]};
    const scores=threads.map(t=>({id:t.id,score:scoreThread(t,text,state,now)})).sort((a,b)=>b.score-a.score||a.id-b.id);
    let chosen=scores[0];const second=scores[1];
    const active=threads.find(t=>t.id===state.activeId);
    const weak=chosen.score<.22, close=second&&chosen.score-second.score<.055;
    if((weak||close)&&active){const a=scores.find(x=>x.id===active.id);if(a&&chosen.score-a.score<.12)chosen=a}
    const margin=second?Math.max(0,chosen.score-second.score):chosen.score;
    const confidence=clamp(.42+chosen.score*.55+margin*.65,0,1);
    return{threadIds:[chosen.id],primaryId:chosen.id,confidence,reason:weak?'active-fallback':close?'context-close':'context',ambiguous:(weak||close)&&confidence<.58,scores};
  }
  function parseCommand(text,state){
    const s=String(text||'').normalize('NFKC').trim(), u=s.toUpperCase();
    if(/(?:新しい|新規)(?:の)?スレッド|スレッド(?:を)?(?:作って|作成|追加)|NEW\s+THREAD/i.test(s))return{type:'create'};
    if(/(?:スレッド一覧|全スレッド(?:を)?(?:見せ|表示)|LIST\s+THREADS)/i.test(s))return{type:'list'};
    let m=u.match(/(?:スレッド|THREAD)\s*([A-Z]+).*?(?:切り替|移動|開いて|表示)/i)||u.match(/^([A-Z]+)\s*(?:に|へ).*?(?:切り替|移動)/i);
    if(m)return{type:'switch',id:codeToId(m[1])};
    m=u.match(/(?:スレッド|THREAD)\s*([A-Z]+).*?リスナー.*?(停止|OFF|オフ|切)/i);if(m)return{type:'listener',id:codeToId(m[1]),enabled:false};
    m=u.match(/(?:スレッド|THREAD)\s*([A-Z]+).*?リスナー.*?(開始|ON|オン|入)/i);if(m)return{type:'listener',id:codeToId(m[1]),enabled:true};
    return null;
  }
  return{excelCode,codeToId,normalize,extractTerms,learn,explicitTargets,scoreThread,route,parseCommand};
});
