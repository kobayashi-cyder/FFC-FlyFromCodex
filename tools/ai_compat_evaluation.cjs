const {performance}=require('node:perf_hooks');
const {graph,runtime}=require('../tests/fixtures/compat_runtime.cjs');
const cases=[
 ['本文を要約して','content.understand'],['資料の内容を理解して','content.understand'],['添付資料を読んで','content.understand'],['summarize the source','content.understand'],
 ['PythonでCSVを読むコードを書いて','code.generate'],['JavaScriptの関数を実装して','code.generate'],['コードのバグを直して','code.debug'],['Pythonのソースを修正して','code.revise'],
 ['Markdownで議事録を作成して','document.create.markdown'],['Markdown形式の報告書を書いて','document.create.markdown'],['仕様書を作って','document.create.docx'],['テキスト文書を作成して','document.create.text'],
 ['計算: 23 + 19','compute.math'],['calculate 7 * 8','compute.math'],['計算: 100 / 4','compute.math'],['計算: 17 - 9','compute.math'],
 ['こんにちは','chat.compose'],['おはようございます','chat.compose'],['ありがとう','chat.compose'],['今日はどう？','chat.compose']
];
(async()=>{
 const source='会議は10月3日です。田中さんは開発を担当します。',contexts='人間: 鈴木さんは営業です。\n人間: 田中さんは開発です。\n人間: 山田さんは広報です。';
 const run=data=>{const r=runtime(data),results=cases.map(([query,expected],i)=>{const t=performance.now(),out=r.api.execute(query,{threadCode:'EVAL',source,context:contexts});return{query,expected,selected:out.tool||null,status:out.status,pass:out.status==='done'&&out.tool===expected,ms:performance.now()-t,stages:out.compatibility?{instruction:!!out.compatibility.instruction?.connectome,information:out.compatibility.information?.selected?.length||0,plan:!!out.compatibility.plan?.connectome}:null}});return{results,correct:results.filter(x=>x.pass).length,total:results.length,executedTools:r.calls.filter(x=>x.tool!=='feedback.ingest').length}};
 const intact=run(graph),disconnected=run({...graph,edges:[]}),times=intact.results.map(x=>x.ms).sort((a,b)=>a-b);
 console.log(JSON.stringify({schema:1,measuredAt:new Date().toISOString(),scope:'20 authored routing cases; real packaged circuit and proxy; deterministic fixture executors, not task-output quality or unseen-language evaluation',runtime:process.version,graph:{nodes:graph.nodes.length,edges:graph.edges.length,provenance:graph.source},intact,allEdgesCut:disconnected,hostLatency:{samples:times.length,p50Ms:times[Math.floor(times.length*.5)],p95Ms:times[Math.floor(times.length*.95)]},interpretation:'All-cut dependency does not establish superiority over random wiring. No Android latency, battery or total-process RAM claim.'},null,2));
 if(intact.correct!==intact.total||disconnected.executedTools!==0)process.exitCode=1;
})();
