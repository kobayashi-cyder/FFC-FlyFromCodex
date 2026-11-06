import test from 'node:test';
import assert from 'node:assert/strict';
import {SolZeroEngine,ENGINE_VERSION} from '../chat_web/engine.js';
test('chat exposes validated deterministic coding and accurate media status',()=>{
 const e=new SolZeroEngine();
 assert.equal(ENGINE_VERSION,'0.13.0');
 const js=e.reply('/code javascript factorial');
 assert.match(js,/function factorial/);
 assert.match(js,/factorial\.js/);
 const py=e.reply('/code python fibonacci');
 assert.match(py,/def fibonacci/);
 assert.match(e.reply('/code python unknown'),/対応アルゴリズム/);
 assert.match(e.reply('/code help'),/二分探索/);
 assert.match(e.reply('/help'),/\/code/);
 assert.match(e.reply('画像を作成したい'),/手続き的生成/);
});
