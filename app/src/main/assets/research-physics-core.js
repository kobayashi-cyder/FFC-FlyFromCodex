(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.FFCResearchPhysics=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim();
function format(t){if(/docx|\bword\b|ワード/.test(t))return'docx';if(/markdown|\bmd\b/.test(t))return'markdown';if(/html/.test(t))return'html';if(/txt|テキスト/.test(t))return'text';return'docx'}
function isDoc(t){return /資料|文書|レポート|報告書|仕様書|提案書|手順書|docx|\bword\b|markdown|まとめて|資料化/.test(t)}
function researchIntent(t){return /ウェブ|web|ネット|インターネット|検索|調べて|調査|出典|根拠|最新|情報収集|文献|論文|wikipedia|crossref/.test(t)}
function physicsIntent(t){return /物理的|物理モデル|推定|概算|フェルミ|見積|計算して|計算する|算出|求めて|理論上|どのくらい.*(?:出る|必要|なる|使う)/.test(t)}
function cleanQuery(text){
 return String(text||'').replace(/(?:ウェブ|web|ネット|インターネット|検索|調べて|調査して|資料|文書|docx|word|にして|作って|まとめて|物理的に|推定して)/gi,' ').replace(/\s+/g,' ').trim().slice(0,180)||String(text||'').slice(0,180);
}
function buildResearchIR(text){
 const t=norm(text);return{kind:'ResearchIR',query:cleanQuery(text),format:format(t),maxSources:/詳しく|徹底|詳細/.test(t)?5:3,sourceModes:[/論文|文献|研究/.test(t)?'crossref':'wikipedia'],citationRequired:true,request:String(text||'')};
}
const U={
 w:[1,'power'],kw:[1e3,'power'],mw:[1e6,'power'],
 wh:[3600,'energy'],kwh:[3.6e6,'energy'],j:[1,'energy'],kj:[1e3,'energy'],mj:[1e6,'energy'],
 v:[1,'voltage'],a:[1,'current'],ah:[3600,'charge'],kg:[1,'mass'],g:[.001,'mass'],
 m:[1,'length'],cm:[.01,'length'],mm:[.001,'length'],s:[1,'time'],sec:[1,'time'],min:[60,'time'],h:[3600,'time'],hr:[3600,'time'],
 'm/s':[1,'speed'],'km/h':[1/3.6,'speed'],pa:[1,'pressure'],kpa:[1e3,'pressure'],mpa:[1e6,'pressure']
};
function quantities(text){
 const re=/(-?\d+(?:\.\d+)?)\s*(kwh|wh|mj|kj|j|mw|kw|w|ah|v|a|kg|g|km\/h|m\/s|mpa|kpa|pa|cm|mm|m|sec|min|hr|h)\b/gi,out=[];let m;
 while((m=re.exec(String(text||'')))){const u=m[2].toLowerCase(),d=U[u];if(d)out.push({raw:m[0],value:+m[1],unit:u,si:+m[1]*d[0],dim:d[1]})}
 return out.slice(0,24);
}
const one=(q,d)=>q.find(x=>x.dim===d);
function buildPhysicsIR(text){
 const t=norm(text);return{kind:'PhysicsIR',request:String(text||''),quantities:quantities(text),model:/太陽光/.test(t)?'solar':/バッテリー|電圧|電流|抵抗|電力|電力量/.test(t)?'electrical':/運動エネルギー|速度/.test(t)?'kinetic':/位置エネルギー|高さ/.test(t)?'potential':/力|加速度/.test(t)?'mechanics':'fermi',uncertainty:/厳密|正確/.test(t)?'narrow':'normal',validation:['units','equation','range','sanity']};
}
function estimate(ir){
 const q=ir.quantities||[],P=one(q,'power'),T=one(q,'time'),V=one(q,'voltage'),A=one(q,'current'),M=one(q,'mass'),S=one(q,'speed'),L=one(q,'length'),E=one(q,'energy'),Q=one(q,'charge');
 const o={model:ir.model,equation:null,value:null,unit:null,assumptions:[],inputs:q,low:null,high:null,notes:[]};
 if(ir.model==='electrical'){if(V&&A){o.equation='P = V × I';o.value=V.si*A.si;o.unit='W'}else if(P&&T){o.equation='E = P × t';o.value=P.si*T.si;o.unit='J'}else if(V&&Q){o.equation='E = V × Q';o.value=V.si*Q.si;o.unit='J'}else if(E&&T){o.equation='P = E / t';o.value=E.si/T.si;o.unit='W'}}
 if(ir.model==='kinetic'&&M&&S){o.equation='E_k = 1/2 m v²';o.value=.5*M.si*S.si*S.si;o.unit='J'}
 if(ir.model==='potential'&&M&&L){o.equation='E_p = m g h';o.value=M.si*9.80665*L.si;o.unit='J';o.assumptions.push('g=9.80665 m/s²')}
 if(ir.model==='solar'&&P&&T){o.equation='E ≈ P_rated × t × η';o.value=P.si*T.si*.8;o.unit='J';o.assumptions.push('システム総合効率 η=0.8 を仮定')}
 if(o.value!=null&&Number.isFinite(o.value)){const f=ir.uncertainty==='narrow'?.1:.2;o.low=o.value*(1-f);o.high=o.value*(1+f);o.notes.push('low/high は未モデル化要因を含む概算幅')}
 else{o.assumptions.push('数値条件が不足しているため定量値は未確定');o.notes.push('寸法・時間・効率・境界条件を追加すれば定量化可能')}
 return o;
}
function classify(text){
 const raw=String(text||''),t=norm(raw),r=researchIntent(t),p=physicsIntent(t),doc=isDoc(t),f=format(t);
 if(r&&p&&doc)return{handled:true,domain:'research-physics',tool:'document.research_physics.'+f,action:'document',ir:{kind:'ResearchPhysicsIR',research:buildResearchIR(raw),physics:buildPhysicsIR(raw),format:f,request:raw},confidence:.95};
 if(r&&doc)return{handled:true,domain:'research',tool:'document.research.'+f,action:'document',ir:buildResearchIR(raw),confidence:.93};
 if(p&&doc)return{handled:true,domain:'physics',tool:'document.physics.'+f,action:'document',ir:{...buildPhysicsIR(raw),format:f},confidence:.92};
 if(r)return{handled:true,domain:'research',tool:'research.collect',action:'research',ir:buildResearchIR(raw),confidence:.86};
 if(p)return{handled:true,domain:'physics',tool:'physics.estimate',action:'estimate',ir:buildPhysicsIR(raw),confidence:.86};
 return{handled:false,confidence:0};
}
return{classify,buildResearchIR,buildPhysicsIR,estimate,quantities};
});