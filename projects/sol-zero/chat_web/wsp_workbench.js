/** Composable, bounded, offline problem workbench. Zero pretrained weights.
 * Only explicit safe data-flow operators; no eval, arbitrary JS, network, or I/O.
 * An existing WSP graph is callable as one operator, but this is not general AI.
 */
export const WORKBENCH_LIMITS=Object.freeze({
  request:3000, inputs:12, steps:20, cases:8, array:64, text:180, magnitude:1e12
});
const NAME=/^[A-Za-z][A-Za-z0-9_]{0,23}$/;
const KEYS=new Set(['inputs','steps','result','tests']);
const STEP_KEYS=new Set(['id','op','args']);
const ARITY=Object.freeze({
  add:2,sub:2,mul:2,div:2,mod:2,pow:2,round:2,clamp:3,
  sum:1,mean:1,min:1,max:1,sort:1,unique:1,count:1,
  filter_ge:2,map_affine:3,concat:2,contains:2,lower:1,
  upper:1,split:2,join:2,eq:2,gt:2,choose:3,wsp:1
});
function fail(message){throw Error('ワークフロー拒否：'+message);}
function object(v){return v!==null&&typeof v==='object'&&!Array.isArray(v)&&
  (Object.getPrototypeOf(v)===Object.prototype||Object.getPrototypeOf(v)===null);}
function keys(v,allowed){return Object.keys(v).every(key=>allowed.has(key));}
function name(v){return typeof v==='string'&&NAME.test(v)&&
  !['constructor','prototype','__proto__'].includes(v);}
function bound(v){
  if(typeof v==='number'){
    if(!Number.isFinite(v)||Math.abs(v)>WORKBENCH_LIMITS.magnitude)fail('数値の範囲外');
    return v;
  }
  if(typeof v==='boolean')return v;
  if(typeof v==='string'){
    if(v.length>WORKBENCH_LIMITS.text)fail('文字列の上限超過');
    return v;
  }
  if(Array.isArray(v)){
    if(v.length>WORKBENCH_LIMITS.array||v.some(x=>Array.isArray(x)))fail('配列の形または件数が不正');
    return v.map(x=>{
      if(x===null||typeof x==='object')fail('配列要素には数値・文字列・真偽値のみ使用できます');
      return bound(x);
    });
  }
  fail('データ型は数値・文字列・真偽値・1次元配列のみ対応');
}
function num(v){
  if(typeof v!=='number'||!Number.isFinite(v))fail('数値型が必要です');
  return v;
}
function txt(v){
  if(typeof v!=='string')fail('文字列型が必要です');
  return v;
}
function list(v){
  if(!Array.isArray(v))fail('配列型が必要です');
  return v;
}
function numericList(v){
  return list(v).map(num);
}
function numericResult(v){return bound(num(v));}
function same(a,b){
  return JSON.stringify(a)===JSON.stringify(b);
}
function strictInput(raw,expected){
  if(!object(raw)||Object.keys(raw).length!==expected.length||
    Object.keys(raw).some(k=>!expected.includes(k)))fail('入力変数が仕様と一致しません');
  const values=new Map();
  for(const key of expected)values.set(key,bound(raw[key]));
  return values;
}
function resolve(value,vars){
  if(typeof value==='string'&&value.startsWith('$')){
    const key=value.slice(1);
    if(!name(key)||!vars.has(key))fail('未定義または未来の参照：'+value.slice(0,30));
    return vars.get(key);
  }
  return bound(value);
}
function apply(op,args,context){
  const a=args[0],b=args[1],c=args[2];
  switch(op){
    case 'add':return numericResult(num(a)+num(b));
    case 'sub':return numericResult(num(a)-num(b));
    case 'mul':return numericResult(num(a)*num(b));
    case 'div':if(num(b)===0)fail('ゼロ除算');return numericResult(num(a)/b);
    case 'mod':if(num(b)===0)fail('ゼロ除算');return numericResult(num(a)%b);
    case 'pow':if(!Number.isInteger(num(b))||Math.abs(b)>8)fail('累乗は整数指数±8以内');return numericResult(num(a)**b);
    case 'round':{
      const digits=num(b);if(!Number.isInteger(digits)||digits<0||digits>6)fail('丸め桁数は0〜6');
      return numericResult(Number(num(a).toFixed(digits)));
    }
    case 'clamp':if(num(b)>num(c))fail('範囲の下限が上限を超えています');
      return numericResult(Math.min(c,Math.max(b,num(a))));
    case 'sum':return numericResult(numericList(a).reduce((x,y)=>x+y,0));
    case 'mean':{
      const xs=numericList(a);if(!xs.length)fail('平均には1件以上必要');
      return numericResult(xs.reduce((x,y)=>x+y,0)/xs.length);
    }
    case 'min':{const xs=numericList(a);if(!xs.length)fail('最小値には1件以上必要');return Math.min(...xs);}
    case 'max':{const xs=numericList(a);if(!xs.length)fail('最大値には1件以上必要');return Math.max(...xs);}
    case 'sort':return numericList(a).sort((x,y)=>x-y);
    case 'unique':return [...new Set(list(a))];
    case 'count':return list(a).length;
    case 'filter_ge':return numericList(a).filter(x=>x>=num(b));
    case 'map_affine':return numericList(a).map(x=>numericResult(x*num(b)+num(c)));
    case 'concat':return bound(txt(a)+txt(b));
    case 'contains':return txt(a).includes(txt(b));
    case 'lower':return bound(txt(a).toLowerCase());
    case 'upper':return bound(txt(a).toUpperCase());
    case 'split':{
      if(!txt(b)||b.length>12)fail('区切り文字は1〜12文字');
      return bound(txt(a).split(b));
    }
    case 'join':return bound(list(a).map(txt).join(txt(b)));
    case 'eq':return same(a,b);
    case 'gt':return num(a)>num(b);
    case 'choose':if(typeof a!=='boolean')fail('条件は真偽値が必要');return a?b:c;
    case 'wsp':{
      if(!context||!context.graph||typeof context.graph.run!=='function')
        fail('WSP数値回路が接続されていません');
      const input=numericList(a);
      if(!input.length||input.length>12||input.some(v=>Math.abs(v)>1e6))
        fail('WSP入力は1〜12個、±1,000,000以内');
      const output=context.graph.run(input);
      if(!output||!object(output.outputs))fail('WSP回路の戻り値が不正');
      const flattened=Object.values(output.outputs).flat();
      return bound(flattened);
    }
    default:fail('未対応の演算：'+String(op).slice(0,32));
  }
}
function execute(spec,inputNames,rawInputs,context,traceEnabled){
  const vars=strictInput(rawInputs,inputNames),trace=[];
  for(let i=0;i<spec.steps.length;i++){
    const step=spec.steps[i],args=step.args.map(v=>resolve(v,vars));
    const value=bound(apply(step.op,args,context));
    vars.set(step.id,value);
    if(traceEnabled)trace.push({id:step.id,op:step.op,value});
  }
  const result=resolve(spec.result,vars);
  return {result,trace};
}
function validateProgram(spec){
  if(!object(spec)||!keys(spec,KEYS)||!object(spec.inputs)||!Array.isArray(spec.steps)||
     spec.steps.length<1||spec.steps.length>WORKBENCH_LIMITS.steps)
    fail('入力・処理手順・結果の形式が不正');
  const inputNames=Object.keys(spec.inputs);
  if(!inputNames.length||inputNames.length>WORKBENCH_LIMITS.inputs||
     !inputNames.every(name))fail('入力名または入力数が不正');
  const available=new Set(inputNames);
  for(const step of spec.steps){
    if(!object(step)||!keys(step,STEP_KEYS)||!name(step.id)||available.has(step.id)||
       !Object.hasOwn(ARITY,step.op)||!Array.isArray(step.args)||
       step.args.length!==ARITY[step.op])fail('手順のID・演算・引数が不正');
    for(const arg of step.args){
      if(typeof arg==='string'&&arg.startsWith('$')&&
         (!name(arg.slice(1))||!available.has(arg.slice(1))))
        fail('未来参照または未定義の引数：'+arg.slice(0,30));
      bound(arg);
    }
    available.add(step.id);
  }
  if(typeof spec.result!=='string'||!spec.result.startsWith('$')||
     !available.has(spec.result.slice(1)))fail('結果参照が未定義');
  if(spec.tests!==undefined){
    if(!Array.isArray(spec.tests)||spec.tests.length>WORKBENCH_LIMITS.cases)
      fail('独立テスト件数は8件以下');
    const seen=new Set([JSON.stringify(inputNames.map(k=>spec.inputs[k]))]);
    for(const t of spec.tests){
      if(!object(t)||!keys(t,new Set(['inputs','expected','tolerance']))||
         !Object.hasOwn(t,'expected'))fail('テスト形式が不正');
      const vars=strictInput(t.inputs,inputNames);
      bound(t.expected);
      if(t.tolerance!==undefined&&
         (typeof t.tolerance!=='number'||t.tolerance<0||t.tolerance>1||!Number.isFinite(t.tolerance)))
        fail('許容誤差が不正');
      if(t.tolerance!==undefined&&typeof t.expected!=='number')
        fail('許容誤差は数値出力のみ対応');
      const signature=JSON.stringify(inputNames.map(k=>vars.get(k)));
      if(seen.has(signature))fail('評価例は本番入力・他の評価例と重複不可');
      seen.add(signature);
    }
  }
  return inputNames;
}
export function solveWorkflow(raw,context={}){
  let spec=raw;
  if(typeof raw==='string'){
    if(raw.length>WORKBENCH_LIMITS.request)fail('手順JSONは3000文字以内');
    try{spec=JSON.parse(raw);}catch{fail('JSON構文を読み取れません');}
  }
  let size;
  try{size=JSON.stringify(spec).length;}catch{fail('JSON形式が不正');}
  if(size>WORKBENCH_LIMITS.request)fail('手順JSONは3000文字以内');
  const inputNames=validateProgram(spec);
  const base=execute(spec,inputNames,spec.inputs,context,true);
  const results=[];
  for(const t of spec.tests||[]){
    try{
      const actual=execute(spec,inputNames,t.inputs,context,false).result;
      const pass=t.tolerance===undefined?same(actual,t.expected):
        typeof actual==='number'&&Math.abs(actual-t.expected)<=t.tolerance;
      results.push({pass,actual,expected:t.expected});
    }catch(e){results.push({pass:false,error:String(e.message).slice(0,100),expected:t.expected});}
  }
  const passed=results.filter(t=>t.pass).length;
  return {result:base.result,trace:base.trace,checks:{
    passed,total:results.length,verified:results.length>0&&passed===results.length,results
  }};
}
function printable(v,max=110){
  const s=JSON.stringify(v);return s.length>max?s.slice(0,max-3)+'...':s;
}
export function workflowMessage(raw,context={}){
  try{
    const result=solveWorkflow(raw,context),lines=result.trace.slice(-12).map(
      t=>'・'+t.id+' ('+t.op+') = '+printable(t.value,90));
    const status=result.checks.total===0?'独立評価例なし（未検証）':
      '別入力評価 '+result.checks.passed+'/'+result.checks.total+
      (result.checks.verified?' 合格':' 不合格');
    return 'WSP連携ワークフロー：\n'+lines.join('\n')+
      '\n最終結果：'+printable(result.result,550)+'\n検証：'+status+
      (result.checks.verified?'\n※利用者指定の期待値との一致であり、正答性の一般保証ではありません。':
      '\n※この結果を正解と断定しません。別入力テストで確認してください。');
  }catch(e){return 'ワークフロー処理できません：'+String(e.message).slice(0,140);}
}
export function workflowHelp(){
  return '複数の演算をつなげて実行します（ゼロから実装・オフライン・任意コード実行なし）。\n'+
    '例：/solve {"inputs":{"prices":[100,200],"rate":1.1},"steps":[{"id":"subtotal","op":"sum","args":["$prices"]},{"id":"total","op":"mul","args":["$subtotal","$rate"]}],"result":"$total","tests":[{"inputs":{"prices":[200,300],"rate":1.2},"expected":600}]}\n'+
    '演算：'+Object.keys(ARITY).join('、')+'\n'+
    '引数の参照は "$変数名"。一つ前の手順を次へ渡せます。独立評価例の期待値は自分で設定してください。\n'+
    '自由文から自動計画する機能ではありません。未定義操作や外部アプリ操作は実行できません。';
}
