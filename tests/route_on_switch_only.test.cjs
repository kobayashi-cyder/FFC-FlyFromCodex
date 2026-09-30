const assert=require('node:assert/strict');
function transition(previousId,route){
  const switched=!!(route.primaryId&&previousId!==route.primaryId);
  return {switched,includeRoute:switched,fromId:previousId,toId:route.primaryId||previousId};
}
let x=transition(1,{primaryId:1});assert.equal(x.switched,false);assert.equal(x.includeRoute,false);
x=transition(1,{primaryId:2});assert.equal(x.switched,true);assert.equal(x.includeRoute,true);assert.equal(x.fromId,1);assert.equal(x.toId,2);
x=transition(27,{primaryId:27});assert.equal(x.includeRoute,false);
console.log('route-on-switch-only: PASS');