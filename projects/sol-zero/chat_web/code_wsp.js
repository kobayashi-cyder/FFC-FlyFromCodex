/** Offline finite code-pattern compiler using directly editable numeric WSP weights.
 * No pretrained models, remote API, eval, Function, or execution of generated code.
 * Unknown or ambiguous tasks abstain instead of inventing source.
 */
export const CODE_TASKS=Object.freeze(['sum','sort','unique','search','fibonacci']);
export const CODE_LANGUAGES=Object.freeze(['js','python']);
const FEATURES=Object.freeze([
 /(?:\\bsum\\b|\\btotal\\b|合計|総和|足し算|加算)/iu,
 /(?:\\bsort\\b|並べ替|ソート|昇順)/iu,
 /(?:\\bunique\\b|\\bdedupe\\b|重複|一意)/iu,
 /(?:\\bsearch\\b|\\bfind\\b|検索|位置を探|添字)/iu,
 /(?:fibonacci|フィボナッチ)/iu
]);
const TEMPLATES=Object.freeze({
  "js": {
    "sum": "export function sumNumbers(values) {\n  if (!Array.isArray(values) || !values.every(n => typeof n === 'number' && Number.isFinite(n))) throw new TypeError('finite number array required');\n  return values.reduce((total, n) => total + n, 0);\n}",
    "sort": "export function sortNumbers(values) {\n  if (!Array.isArray(values) || !values.every(n => typeof n === 'number' && Number.isFinite(n))) throw new TypeError('finite number array required');\n  return [...values].sort((a, b) => a - b);\n}",
    "unique": "export function uniqueValues(values) {\n  if (!Array.isArray(values)) throw new TypeError('array required');\n  return [...new Set(values)];\n}",
    "search": "export function findIndex(values, target) {\n  if (!Array.isArray(values)) throw new TypeError('array required');\n  return values.findIndex(value => Object.is(value, target));\n}",
    "fibonacci": "export function fibonacci(n) {\n  if (!Number.isInteger(n) || n < 0 || n > 500) throw new RangeError('integer n must be 0..500');\n  let a = 0n, b = 1n;\n  for (let i = 0; i < n; i++) [a, b] = [b, a + b];\n  return a;\n}"
  },
  "python": {
    "sum": "import math\n\ndef sum_numbers(values):\n    if not all(type(n) in (int, float) and math.isfinite(n) for n in values):\n        raise ValueError(\"finite number array required\")\n    return sum(values)",
    "sort": "import math\n\ndef sort_numbers(values):\n    if not all(type(n) in (int, float) and math.isfinite(n) for n in values):\n        raise ValueError(\"finite number array required\")\n    return sorted(values)",
    "unique": "def unique_values(values):\n    \"\"\"Remove duplicates while preserving order. Requires hashable elements.\"\"\"\n    return list(dict.fromkeys(values))",
    "search": "def find_index(values, target):\n    return next((i for i, value in enumerate(values) if value == target), -1)",
    "fibonacci": "def fibonacci(n):\n    if type(n) is not int or not 0 <= n <= 500:\n        raise ValueError(\"integer n must be 0..500\")\n    a, b = 0, 1\n    for _ in range(n):\n        a, b = b, a + b\n    return a"
  }
});
export class CodeWSP {
 constructor(){this.weights=CODE_TASKS.map((_,i)=>CODE_TASKS.map((_,j)=>i===j?4:0));this.history=[];}
 route(prompt){
  const text=String(prompt??'').slice(0,300),features=FEATURES.map(p=>p.test(text)?1:0);
  if(!features.some(Boolean))return null;
  const scores=this.weights.map(row=>row.reduce((total,w,j)=>total+w*features[j],0));
  const max=Math.max(...scores);
  if(max<1||scores.filter(x=>x===max).length!==1)return null;
  return {task:CODE_TASKS[scores.indexOf(max)],features,scores};
 }
 edit(task,feature,weight){
  const row=CODE_TASKS.indexOf(task),col=CODE_TASKS.indexOf(feature);
  if(row<0||col<0||!Number.isFinite(weight)||Math.abs(weight)>20)throw Error('WSPコード重みの指定が不正です');
  this.history.push({row,col,prior:this.weights[row][col]});if(this.history.length>40)this.history.shift();
  this.weights[row][col]=weight;
 }
 rollback(){const h=this.history.pop();if(!h)return false;this.weights[h.row][h.col]=h.prior;return true;}
 snapshot(){return {version:1,weights:this.weights.map(x=>[...x])};}
 restore(value){
  if(!value||value.version!==1||!Array.isArray(value.weights)||value.weights.length!==5||
    !value.weights.every(row=>Array.isArray(row)&&row.length===5&&row.every(n=>typeof n==='number'&&Number.isFinite(n)&&Math.abs(n)<=20)))return false;
  this.weights=value.weights.map(x=>[...x]);this.history=[];return true;
 }
}
export function generateCode(language,prompt,router=new CodeWSP()){
 const lang=({javascript:'js',js:'js',py:'python',python:'python'})[String(language??'').toLowerCase()];
 if(!lang)throw Error('対応言語: js / python');
 const route=router.route(prompt);if(!route)return null;
 return {language:lang,task:route.task,filename:route.task+(lang==='js'?'.mjs':'.py'),code:TEMPLATES[lang][route.task]};
}
