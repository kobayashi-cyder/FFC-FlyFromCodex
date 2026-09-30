const assert=require('node:assert/strict');
const R=require('../app/src/main/assets/research-physics-core.js');
let x=R.classify('ウェブで太陽光発電について調べてDOCX資料にして');assert.equal(x.tool,'document.research.docx');
x=R.classify('物理的に10 kWを2 h運転した電力量を推定して資料化');assert.equal(x.tool,'document.physics.docx');
x=R.classify('ウェブで仕様を調べて物理推定もしてDOCXにまとめて');assert.equal(x.tool,'document.research_physics.docx');
let ir=R.buildPhysicsIR('1 kWを2 h使う電力量を推定');let e=R.estimate(ir);assert.equal(e.equation,'E = P × t');assert.ok(Math.abs(e.value-7200000)<1);
ir=R.buildPhysicsIR('2 kgの物体が3 m/sで動く運動エネルギー');e=R.estimate(ir);assert.equal(e.unit,'J');assert.equal(e.value,9);
console.log('research-physics-core: PASS');