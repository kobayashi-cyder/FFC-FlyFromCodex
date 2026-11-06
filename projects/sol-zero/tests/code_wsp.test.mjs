import test from 'node:test';
import assert from 'node:assert/strict';
import {CODE_TASKS,CodeWSP,generateCode} from '../chat_web/code_wsp.js';

test('code WSP uses finite editable weights and rejects ambiguous prompts',()=>{
 const w=new CodeWSP();
 assert.equal(w.route('昇順にソート').task,'sort');
 assert.equal(w.route('ソートして重複も消す'),null);
 assert.equal(w.route('unknown task'),null);
 assert.deepEqual(w.route('fibonacci').features,[0,0,0,0,1]);
 const old=w.snapshot();
 w.edit('sum','sort',12);assert.equal(w.route('sort').task,'sum');
 assert.equal(w.rollback(),true);assert.deepEqual(w.snapshot(),old);
 assert.throws(()=>w.edit('sum','sort',Infinity));
 assert.throws(()=>w.edit('fake','sort',1));
 assert.equal(new CodeWSP().restore({version:1,weights:[[1]]}),false);
 const restored=new CodeWSP();assert.equal(restored.restore(old),true);
 assert.deepEqual(restored.snapshot(),old);
});
test('supported code templates are bounded and language specific',()=>{
 const w=new CodeWSP();
 for(const lang of ['js','python'])for(const task of CODE_TASKS){
  const item=generateCode(lang,task,w);
  assert.equal(item.language,lang);assert.equal(item.task,task);assert.ok(item.code.length>65);
  assert.match(item.filename,/\.(mjs|py)$/);
  assert.doesNotMatch(item.code,/eval\s*\(|new Function\s*\(/);
 }
 assert.equal(generateCode('js','unrecognized',w),null);
 assert.equal(generateCode('py','fibonacci',w).language,'python');
 assert.throws(()=>generateCode('ruby','sum',w));
});
test('templates have the correct functions and numeric bounds',()=>{
 assert.match(generateCode('js','合計').code,/reduce/);
 assert.match(generateCode('js','sort').code,/sort\(\(a, b\)/);
 assert.match(generateCode('python','重複').code,/dict\.fromkeys/);
 assert.match(generateCode('python','フィボナッチ').code,/0 <= n <= 500/);
});
