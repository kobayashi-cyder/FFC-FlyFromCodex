const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/proxy-agent-core.js');
const P=require('../app/src/main/assets/ir-patch-core.js');
const V=require('../app/src/main/assets/capability-vocabulary-core.js');
const ex=new C.Executive({policy:new C.ToolPolicy(new Set(['code.write','image.write','document.write','voice.input']))});
let p=V.classify('Pythonでコードを書いて');let v=ex.evaluate({steps:[{tool:p.tool}]},n=>({capability:'code.write'}));assert.equal(v.accepted,true);
v=ex.evaluate({steps:[{tool:'network.fetch'}]},n=>({capability:'network'}));assert.equal(v.status,C.Result.BLOCKED);
let old=V.buildImageIR('猫の画像、16:9、写真風');let diff=P.infer(old,'構図だけ正方形にして',V,'meso');assert.equal(diff.ir.kind,'ImageIR');assert.equal(diff.ir.aspect,'1:1');
console.log('proxy-apk-integration: PASS');