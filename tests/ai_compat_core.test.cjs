const assert=require('node:assert/strict');
const {graph,runtime,C,A}=require('./fixtures/compat_runtime.cjs');
(async()=>{
 const parsed=A.normalizeRequest({messages:[{role:'user',content:'コードを作って'},{role:'assistant',content:'前の返答'},{role:'user',content:'こんにちは'}],source:'会議は10月3日です。',threadCode:'A'});
 assert.equal(parsed.text,'こんにちは');assert.ok(parsed.ctx.context.includes('コード'));assert.equal(parsed.ctx.source,'会議は10月3日です。');
 for(const request of [{messages:[]},{messages:[{role:'system',content:'instruction'}]},{messages:[{role:'user',content:[{type:'image'}]}]},{messages:[{role:'assistant',content:'end'}]},{messages:[{role:'user',content:'x'.repeat(12001)}]},{messages:[{role:'user',content:'hello'}],stream:true}])assert.throws(()=>A.normalizeRequest(request));
 const r=runtime(),reply=await r.api.request({messages:[{role:'user',content:'前のコードを作って'},{role:'assistant',content:'code'},{role:'user',content:'こんにちは'}],threadCode:'A'});
 assert.equal(reply.status,'done');assert.equal(reply.tool,'chat.compose');assert.equal(reply.choices[0].message.role,'assistant');
 assert.equal(reply.compatibility.instruction.selected,'chat');assert.equal(reply.compatibility.instruction.connectome.trace.length,6);assert.equal(reply.compatibility.plan.connectome.trace.length,6);
 assert.ok(reply.compatibility.information.connectome.length);assert.ok(r.calls.some(x=>x.tool==='chat.compose'));assert.ok(!r.calls.some(x=>x.tool==='code.generate'));
 const invalid=await r.api.request({messages:[{role:'user',content:'hello'}],tools:[]});assert.equal(invalid.status,'unsupported');assert.equal(invalid.choices.length,0);
 const code=await r.api.request({messages:[{role:'user',content:'Pythonの関数を書いて'}]});assert.equal(code.choices[0].message.content,'fixture code');
 const disconnected=runtime({...graph,edges:[]}),blocked=await disconnected.api.request({messages:[{role:'user',content:'本文を要約して'}],source:'会議は10月3日です。'});
 assert.equal(blocked.status,'blocked');assert.equal(blocked.choices.length,0);assert.ok(!disconnected.calls.some(x=>x.tool==='content.understand'));
 const source='会議は10月3日です。参加者は田中さんです。';const content=await r.api.request({messages:[{role:'user',content:'本文を要約して'}],source,threadCode:'B'});
 assert.equal(content.tool,'content.understand');assert.equal(r.calls.findLast(x=>x.tool==='content.understand').args.source,source);
 const kernel=new C.ConnectomeMultiplexer(graph);kernel.select([{tool:'compute.math'}],'A');
 const pending=kernel.lanes.get('A').last;
 const infos=A.selectInformation('田中さんの担当は？',{context:'人間: 鈴木さんは営業です。\n人間: 田中さんは開発です。\n人間: 山田さんは広報です。',source:''},rows=>kernel.evaluate(rows,'A'));
 assert.equal(infos.selected.length,1);assert.ok(infos.context.includes('田中'));assert.ok(!infos.context.includes('鈴木'));assert.equal(kernel.lanes.get('A').last,pending);assert.equal(kernel.reinforce('compute.math',1,{lane:'A'}),true);
 const many=Array.from({length:8},(_,i)=>'record '+i).join('\n');const context=A.selectInformation('unknown',{context:many,source:'verbatim'},rows=>kernel.evaluate(rows,'B'));
 assert.equal(context.fallback,true);assert.equal(context.selected.length,4);assert.equal(context.source,'verbatim');assert.ok(context.context.includes('record 7'));assert.ok(!context.context.includes('record 0'));
 const dead=A.selectInformation('田中',{context:'田中は開発です。'},rows=>new C.ConnectomeSelector({...graph,edges:[]}).select(rows));assert.equal(dead.selected.length,0);
 // Two applicable meanings can be separated by an output lesion. Merely
 // zeroing all outputs establishes circuit dependency, not wiring advantage.
 const option=(tool,d,confidence)=>({handled:true,plan:{tool,domain:d},proposal:{confidence,steps:[{tool,args:{}}]}});
 const options=[option('chat.compose','chat',1.1),option('code.generate','code',.8)];
 const prepare=data=>{const k=new C.ConnectomeSelector(data);return A.prepare('ambiguous',{},options,rows=>k.select(rows))};
 assert.equal(prepare(graph).plan.tool,'code.generate');
 assert.equal(prepare({...graph,edges:graph.edges.filter(e=>e.to!=='MNad21')}).plan.tool,'chat.compose');
 console.log('ai-compat: PASS (JSON protocol, latest-user classification, thread-scoped evidence, plan choice, output lesion, all-cut blocking, reward-credit isolation)');
})().catch(e=>{console.error(e);process.exitCode=1});
