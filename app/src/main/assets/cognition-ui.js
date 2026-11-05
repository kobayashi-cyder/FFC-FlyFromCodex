(() => {
'use strict';
function mount(){
 if(document.getElementById('bancCognitionCard'))return;
 const container=document.querySelector('main');if(!container)return;
 const details=document.createElement('details');
 details.id='bancCognitionCard';details.className='card';
 const heading=document.createElement('summary');heading.textContent='🧠 BANC認知実験 — 目標推論と複数手順の計画';
 heading.style.cssText='font-weight:700;cursor:pointer;padding:4px';
 details.appendChild(heading);
 const description=document.createElement('p');
 description.textContent='JSONで事実・目標・行動・推論規則を指定します。プレビューは副作用なし。実行には権限・コネクトーム出力・実際の観測値が必要で、未確認の成功は表示しません。万能AIの証明ではありません。';
 details.appendChild(description);
 const input=document.createElement('textarea');
 input.id='bancCognitionInput';input.rows=12;input.spellcheck=false;
 input.style.cssText='display:block;width:100%;font-family:monospace;min-height:230px;white-space:pre';
 const example={
  schema:1,facts:{request:'new',draft:'none',review:'pending'},goal:{review:'accepted'},
  actions:[
   {id:'write-draft',tool:'document.create.text',requires:{request:'new',draft:'none'},effects:{draft:'ready'},args:{goal:'下書きの作成'}},
   {id:'review-draft',tool:'content.understand',requires:{draft:'ready',review:'pending'},effects:{review:'accepted'},args:{source:'下書きの検証'}}
  ]
 };
 input.value=JSON.stringify(example,null,2);details.appendChild(input);
 const controls=document.createElement('div');controls.className='row';controls.style.marginTop='10px';
 const preview=document.createElement('button');preview.type='button';preview.textContent='計画を探索';
 const run=document.createElement('button');run.type='button';run.textContent='観測付き実行';run.title='実行権限が必要です';
 const output=document.createElement('pre');output.id='bancCognitionOutput';output.textContent='例は計画探索のデモです。実行可能な能力を保証するものではありません。';
 output.style.cssText='white-space:pre-wrap;word-break:break-word;max-height:350px;overflow:auto';
 controls.append(preview,run);details.append(controls,output);
 const render=value=>{try{output.textContent=JSON.stringify(value,null,2).slice(0,16000)}catch(e){output.textContent=String(e)}};
 function read(){return JSON.parse(input.value)}
 preview.addEventListener('click',()=>{
  try{render(window.FFC_PROXY_AGENT?.cognitionPlan?.(read())||{status:'unsupported',reason:'planner unavailable'})}
  catch(e){render({status:'invalid_request',detail:String(e.message||e)})}
 });
 run.addEventListener('click',async()=>{
  if(typeof window.confirm!=='function'||!window.confirm('確認：各ツールが端末上で実行されます。操作の副作用が発生する場合があります。続行しますか？'))return;
  run.disabled=true;preview.disabled=true;output.textContent='実行中（観測されない結果は成功としません）';
  try{
   const api=window.FFC_PROXY_AGENT;
   render(api?.cognitionSolve?await api.cognitionSolve(read(),{execute:true,threadCode:'COGNITION',allowSideEffects:false}):{status:'unsupported',reason:'cognition unavailable'});
  }catch(e){render({status:'failed',detail:String(e.message||e)})}
  finally{run.disabled=false;preview.disabled=false}
 });
 container.appendChild(details);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount,{once:true});else mount();
})();
