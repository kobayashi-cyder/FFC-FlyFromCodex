const assert=require('node:assert/strict');
const Core=require('../app/src/main/assets/thread-router-core.js');
assert.equal(Core.excelCode(1),'A');
assert.equal(Core.excelCode(26),'Z');
assert.equal(Core.excelCode(27),'AA');
assert.equal(Core.excelCode(52),'AZ');
assert.equal(Core.excelCode(53),'BA');
assert.equal(Core.codeToId('AA'),27);
const now=Date.now();
let state={activeId:1,threads:[
  {id:1,listenerEnabled:true,termFreq:{株:5,'株価':4},lastActive:now,createdAt:now},
  {id:2,listenerEnabled:true,termFreq:{画像:5,'生成':4},lastActive:now,createdAt:now}
]};
let r=Core.route(state,'画像をもう少し明るく生成して',now);
assert.equal(r.primaryId,2);
r=Core.route(state,'スレッドAに送って。株価の話を続ける',now);
assert.deepEqual(r.threadIds,[1]);
state.threads[1].listenerEnabled=false;
r=Core.route(state,'画像生成の続き',now);
assert.equal(r.primaryId,1);
assert.equal(Core.parseCommand('新しいスレッドを作って',state).type,'create');
assert.equal(Core.parseCommand('スレッドAAに切り替えて',state).id,27);
console.log('thread-router-core: PASS');
