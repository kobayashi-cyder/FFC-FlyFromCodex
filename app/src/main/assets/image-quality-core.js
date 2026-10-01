(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCImageQuality=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';

const KIND_WORDS={
  cat:['cat','cats','猫','ねこ','ネコ'],
  dog:['dog','dogs','犬','いぬ','イヌ'],
  bird:['bird','birds','鳥','とり','トリ'],
  person:['person','people','human','man','woman','child','人物','人','男','女','子ども','子供'],
  tree:['tree','trees','木','樹'],
  flower:['flower','flowers','花'],
  house:['house','home','家','住宅'],
  car:['car','cars','車','くるま'],
  desk:['desk','table','机','テーブル'],
  chair:['chair','椅子','いす'],
  sun:['sun','太陽'],
  moon:['moon','月'],
  cloud:['cloud','clouds','雲','くも'],
  mountain:['mountain','mountains','山'],
  river:['river','川'],
  building:['building','city','街','ビル','建物'],
  insect:['insect','bug','fly','昆虫','ハエ','虫'],
  connectome:['connectome','コネクトーム','回路']
};
const DECORATIVE=new Set(['sun','moon','cloud']);
const CATEGORY_PARTS={
  person:['head','torso','limbs'],
  cat:['head','body','tail'],
  dog:['head','body','tail'],
  bird:['head','body','wing'],
  car:['body','cabin','wheels']
};
const CATEGORY_GUIDANCE={
  person:'anatomically coherent person, one head, coherent torso, natural arms and legs, no extra limbs',
  cat:'recognizable cat, coherent face and body, natural legs and tail, no duplicated anatomy',
  dog:'recognizable dog, coherent muzzle and body, natural legs and tail, no duplicated anatomy',
  bird:'recognizable bird, coherent head and body, natural wing and beak placement',
  car:'coherent car body, plausible cabin, aligned wheels, no duplicated vehicle parts'
};
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim();
const uniq=a=>[...new Set(a.filter(Boolean))];

function hash32(s){
  let h=2166136261>>>0;
  for(const ch of String(s||'')){h^=ch.charCodeAt(0);h=Math.imul(h,16777619)>>>0}
  return h>>>0;
}
function requestedKinds(text){
  const t=norm(text),out=[];
  for(const [kind,words] of Object.entries(KIND_WORDS)){
    if(words.some(w=>t.includes(norm(w))))out.push(kind);
  }
  return uniq(out);
}
function expand(ir,count=8){
  const subject=String(ir?.subject||ir?.request||'').trim();
  const style=(ir?.style||[]).join(', ');
  const composition=(ir?.composition||[]).join(', ');
  const lighting=(ir?.lighting||[]).join(', ');
  const palette=(ir?.palette||[]).join(', ');
  const kinds=requestedKinds(subject),category=kinds.map(k=>CATEGORY_GUIDANCE[k]).filter(Boolean).join(', ');
  const base=[subject,style&&('style: '+style),composition&&('composition: '+composition),lighting&&('lighting: '+lighting),palette&&('palette: '+palette),category&&('category integrity: '+category)].filter(Boolean).join(' | ');
  const variants=[
    ['faithful','faithful subject, natural proportions, coherent composition, uncluttered background','photoish',5,1.2,1.0],
    ['clean','clear main subject, simple background, no unnecessary objects, strong silhouette','photoish',5,.6,.8],
    ['balanced','balanced composition, realistic spacing, visually stable geometry, clean edges','photoish',5,1.0,1.0],
    ['center','main subject clearly readable, centered visual hierarchy, restrained background','photoish',4,.8,.9],
    ['context','natural environmental context, plausible scale relationships, coherent depth','cinematic',5,1.8,1.2],
    ['detail','refined detail, coherent materials, consistent lighting, natural scale','photoish',6,1.0,1.1],
    ['simple','minimal clutter, one clear focal subject, calm composition, natural proportions','photoish',4,.4,.7],
    ['depth','clear foreground midground background separation, stable perspective, subject emphasis','cinematic',5,2.2,1.2],
    ['neutral','neutral camera, ordinary natural appearance, no surreal additions, clean composition','photoish',5,.7,.9],
    ['literal','literal interpretation of the request, no unrelated objects, readable subject identity','photoish',5,.5,.8],
    ['soft','soft natural lighting, plausible geometry, restrained detail, coherent scene','photoish',5,1.4,1.0],
    ['strict','strict request fidelity, simple scene, correct subject count, no decorative distractions','photoish',4,.3,.7]
  ];
  const n=clamp(Math.trunc(+count||8),4,12),seedBase=hash32(subject+'|'+JSON.stringify(ir||{}));
  return variants.slice(0,n).map((v,i)=>({
    id:'img-'+String(i+1).padStart(2,'0'),
    prompt:[base,v[1]].filter(Boolean).join(' | '),
    strategy:v[0],
    seed:String((seedBase+Math.imul(i+1,2654435761))>>>0),
    mode:v[2],
    detail:v[3],
    blur:v[4],
    atmosphere:v[5]
  }));
}
function evolve(evaluated,ir,generation=1,count=6){
  const ranked=[...(evaluated||[])].sort((a,b)=>(b?.quality?.score||0)-(a?.quality?.score||0));
  const parents=ranked.slice(0,Math.min(3,ranked.length));
  if(!parents.length)return[];
  const subject=String(ir?.subject||ir?.request||'').trim();
  const out=[],n=clamp(Math.trunc(+count||6),3,9);
  for(let i=0;i<n;i++){
    const p=parents[i%parents.length],baseSeed=hash32(String(p.seed||subject)+'|g'+generation+'|'+i);
    const r=(salt)=>((hash32(baseSeed+'|'+salt)%10001)/10000);
    const detail=clamp(Math.round((+p.detail||5)+(r('detail')-.5)*2),3,6);
    const blur=+clamp((+p.blur||1)+(r('blur')-.5)*1.4,0,3).toFixed(2);
    const atmosphere=+clamp((+p.atmosphere||1)+(r('atmo')-.5)*.7,.35,1.7).toFixed(2);
    const mode=(generation>1&&r('mode')>.72)?'cinematic':(p.mode||'photoish');
    const problems=[...(p?.quality?.hardIssues||[]),...(p?.quality?.issues||[])];
    const repairs=[];
    if(problems.some(x=>String(x).startsWith('subject-missing')||x==='request-coverage'))repairs.push('make the requested subject unmistakably present and dominant');
    if(problems.some(x=>String(x).startsWith('category-structure')))repairs.push('repair anatomy and all required structural parts with natural proportions');
    if(problems.some(x=>x==='entity-out-of-bounds'||String(x).startsWith('category-crop')))repairs.push('zoom out enough to keep the entire subject inside frame');
    if(problems.some(x=>x==='implausible-scale'))repairs.push('correct object scale and physical proportions');
    if(problems.some(x=>x==='flat-render'||x==='low-visual-structure'))repairs.push('increase local shape definition material contrast and readable edges');
    if(problems.some(x=>x==='weak-composition'))repairs.push('rebalance focal placement and visual hierarchy');
    if(problems.some(x=>x==='clutter'||x==='scene-clutter'||x==='stacked-duplicates'))repairs.push('remove duplicates and unnecessary background objects');
    const mutation=[
      'evolution generation '+generation,
      ...repairs,
      r('crop')>.5?'preserve full subject inside frame':'strong readable silhouette',
      r('space')>.5?'natural spacing and perspective':'balanced subject scale',
      r('material')>.5?'refined material texture and local contrast':'coherent soft lighting',
      'preserve anatomy and object identity'
    ].join(', ');
    out.push({
      id:'g'+generation+'-'+String(i+1).padStart(2,'0'),
      parentId:p.id||null,
      prompt:[p.prompt||subject,mutation].filter(Boolean).join(' | '),
      strategy:'evolve:'+String(p.strategy||'best'),
      seed:String(baseSeed>>>0),mode,detail,blur,atmosphere,generation
    });
  }
  return out;
}
function sceneOf(candidate){
  return candidate?.value?.sceneDetail||candidate?.sceneDetail||candidate?.scene||null;
}
function visualPartPass(p){
  if(!p||(+p.samples||0)<8)return false;
  const std=+p.stdLuma||0,edge=+p.edgeDensity||0,range=+p.dynamicRange||0;
  return std>=2.5||edge>=.004||range>=12;
}
function categoryIntegrity(scene,targets,hard,issues){
  const checks=[],visuals=scene?.entityVisual||{},W=+scene?.width||0,H=+scene?.height||0;
  let score=1,checked=0;
  const bbox={
    person:[.11,.20,.11,.18],cat:[.12,.13,.10,.15],dog:[.12,.13,.10,.15],
    bird:[.09,.10,.08,.12],car:[.12,.10,.10,.12]
  };
  for(const e of targets){
    const kind=String(e.kind||''),parts=CATEGORY_PARTS[kind];if(!parts)continue;
    checked++;
    const row={id:e.id||kind,kind,parts:{},pass:true};
    const v=visuals[e.id]||visuals[kind]||null;
    if(!v){
      issues.push('category-visual-unavailable:'+kind);
      row.pass=false;row.reason='visual-unavailable';score=Math.min(score,.72);checks.push(row);continue;
    }
    let okCount=0;
    for(const part of parts){
      const ok=visualPartPass(v.parts?.[part]);
      row.parts[part]={pass:ok,metrics:v.parts?.[part]||null};
      if(ok)okCount++;else{hard.push('category-structure:'+kind+':'+part);row.pass=false}
    }
    const frac=parts.length?okCount/parts.length:1;
    score=Math.min(score,frac);
    const b=bbox[kind];
    if(b&&W&&H){
      const x=+e.x||0,y=+e.y||0,sc=+e.scale||1;
      const halfW=W*b[0]*sc,top=H*b[1]*sc,bottom=H*b[3]*sc;
      if(x-halfW<0||x+halfW>W||y-top<0||y+bottom>H){
        hard.push('category-crop:'+kind);row.pass=false;score=Math.min(score,.4);
      }
    }
    checks.push(row);
  }
  return{score:checked?score:1,checks,checked};
}
function evaluate(candidate,ir){
  const scene=sceneOf(candidate),issues=[],hard=[],requested=requestedKinds(ir?.subject||ir?.request||'');
  const ents=Array.isArray(scene?.entities)?scene.entities:[],generated=ents.map(e=>String(e?.kind||'')).filter(Boolean);
  const W=+scene?.width||+candidate?.value?.scene?.width||0,H=+scene?.height||+candidate?.value?.scene?.height||0;
  const objects=+candidate?.value?.objects||ents.length||0;

  if(!scene||objects<=0)hard.push('empty-scene');
  if(W<128||H<128)hard.push('invalid-dimensions');
  const nonDecorative=generated.filter(k=>!DECORATIVE.has(k));
  if(!requested.length && nonDecorative.every(k=>k==='connectome'))hard.push('unsupported-subject');
  for(const k of requested)if(!generated.includes(k))hard.push('subject-missing:'+k);

  let boundsBad=0,scaleBad=0,duplicatePenalty=0;
  const seen=new Map();
  for(const e of ents){
    const x=+e.x,y=+e.y,scale=+e.scale;
    if(Number.isFinite(x)&&Number.isFinite(y)&&W&&H&&(x<-.08*W||x>1.08*W||y<-.08*H||y>1.12*H))boundsBad++;
    if(Number.isFinite(scale)&&(scale<.35||scale>2.2))scaleBad++;
    const key=String(e.kind||'')+'|'+Math.round((x||0)/30)+'|'+Math.round((y||0)/30);
    seen.set(key,(seen.get(key)||0)+1);
  }
  duplicatePenalty=[...seen.values()].reduce((n,v)=>n+Math.max(0,v-2),0);
  if(boundsBad)hard.push('entity-out-of-bounds');
  if(scaleBad)hard.push('implausible-scale');
  if(objects>22)issues.push('scene-clutter');
  if(duplicatePenalty>2)issues.push('stacked-duplicates');

  const rm=scene?.renderMetrics||null;
  if(rm){
    if((+rm.samples||0)<100)hard.push('render-sample-too-small');
    if((+rm.stdLuma||0)<7)hard.push('flat-render');
    if((+rm.clippedRatio||0)>.965)hard.push('render-clipping');
    if((+rm.edgeDensity||0)<.002)issues.push('low-visual-structure');
  }

  const targets=ents.filter(e=>requested.includes(e.kind));
  const category=categoryIntegrity(scene,targets,hard,issues);
  let subjectCoverage=requested.length?requested.filter(k=>generated.includes(k)).length/requested.length:(nonDecorative.length?1:0);
  let compositionScore=1;
  if(targets.length&&W&&H){
    const avgX=targets.reduce((a,e)=>a+(+e.x||W/2),0)/targets.length;
    const avgY=targets.reduce((a,e)=>a+(+e.y||H/2),0)/targets.length;
    const dx=Math.abs(avgX-W/2)/(W/2),dy=Math.abs(avgY-H*.56)/(H*.56);
    compositionScore=clamp(1-(dx*.42+dy*.28),0,1);
  }
  const clutterScore=clamp(1-Math.max(0,objects-10)/16-duplicatePenalty*.08,0,1);
  const geometryScore=hard.some(x=>x==='entity-out-of-bounds'||x==='implausible-scale')?0:1;
  const styleWanted=(ir?.style||[])[0]||'',mode=String(scene?.mode||candidate?.mode||'');
  const styleScore=!styleWanted?1:(styleWanted==='photo'?+(mode==='photoish'||mode==='cinematic'):1);
  const renderScore=rm?clamp(((+rm.stdLuma||0)-7)/28,0,1)*.55+clamp((+rm.edgeDensity||0)/.09,0,1)*.30+clamp(1-(+rm.clippedRatio||0)/.35,0,1)*.15:1;
  const fidelity=subjectCoverage;
  const categoryScore=category.score;
  const score=Math.round(100*(
    fidelity*.36+
    geometryScore*.14+
    compositionScore*.14+
    clutterScore*.08+
    styleScore*.06+
    renderScore*.07+
    categoryScore*.10+
    (objects>0?1:0)*.05
  ));
  if(fidelity<1)issues.push('request-coverage');
  if(compositionScore<.45)issues.push('weak-composition');
  if(clutterScore<.45)issues.push('clutter');
  const pass=hard.length===0&&score>=72;
  return{
    pass,score,hardIssues:hard,issues:uniq(issues),metrics:{
      subjectCoverage:+fidelity.toFixed(3),
      geometry:+geometryScore.toFixed(3),
      composition:+compositionScore.toFixed(3),
      clarity:+clutterScore.toFixed(3),
      style:+styleScore.toFixed(3),
      render:+renderScore.toFixed(3),
      categoryIntegrity:+categoryScore.toFixed(3),
      categoryChecks:category.checks,
      objects,
      renderMetrics:rm
    }
  };
}
function rank(candidates,ir){
  const evaluated=(candidates||[]).map(c=>({...c,quality:evaluate(c,ir)}));
  const passed=evaluated.filter(c=>c.quality.pass).sort((a,b)=>b.quality.score-a.quality.score||String(a.id).localeCompare(String(b.id)));
  const rejected=evaluated.filter(c=>!c.quality.pass).sort((a,b)=>b.quality.score-a.quality.score);
  return{selected:passed[0]||null,passed,rejected,evaluated};
}
return{KIND_WORDS,CATEGORY_PARTS,CATEGORY_GUIDANCE,requestedKinds,expand,evolve,evaluate,rank,hash32,visualPartPass,categoryIntegrity};
});