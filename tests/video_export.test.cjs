const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
function runtime(overrides={}){
 const calls=[],timers=[],ctx={},window={addEventListener(){}};let rendered=0;
 const native={begin:(w,h,fps,frames,name)=>{calls.push(['begin',w,h,fps,frames,name]);return JSON.stringify({ok:true,jobId:'job-1',name})},append:(id,i,jpeg)=>{calls.push(['append',id,i,jpeg]);return JSON.stringify({ok:true})},finish:id=>{calls.push(['finish',id]);return JSON.stringify({ok:true,name:'movie.mp4',bytes:1024,mime:'video/mp4'})},cancel:id=>{calls.push(['cancel',id]);return JSON.stringify({ok:true})},share:id=>{calls.push(['share',id]);return JSON.stringify({ok:true})},...overrides};
 const document={getElementById:()=>null,createElement:()=>({width:0,height:0,getContext:()=>ctx,toDataURL:()=>{calls.push(['jpeg']);return'data:image/jpeg;base64,ZmFrZQ=='}})};
 window.AndroidVideo=native;
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/video-export.js','utf8'),{window,document,AndroidVideo:native,setTimeout,Map,Promise});
 const api=window.FFCVideoExport;api.setSource(()=>({width:768,height:512,durationMs:1000,render:(t,c)=>{assert.equal(c,ctx);calls.push(['render',t]);rendered++}}));
 return{api,calls,timers,async flush(){await api.whenComplete()},get rendered(){return rendered}};
}
const watchdog=setTimeout(()=>{throw new Error('video export test did not complete')},3000);
(async()=>{
 const r=runtime();const begun=r.api.start({fps:4,filename:'movie.mp4'});assert.equal(begun.ok,true);assert.equal(begun.frameCount,4);assert.equal(r.rendered,0);assert.equal(r.api.share().ok,false);assert.equal(r.api.start().ok,false);
 await r.flush();assert.equal(r.api.status().status,'complete');assert.equal(r.api.status().mime,'video/mp4');assert.equal(r.rendered,4);
 const frames=r.calls.filter(c=>c[0]==='append');assert.deepEqual(frames.map(c=>c[2]),[0,1,2,3]);assert.equal(r.calls.filter(c=>c[0]==='finish').length,1);
 assert.deepEqual(r.calls.slice(1,-1).map(c=>c[0]),Array.from({length:4},()=>['render','jpeg','append']).flat());
 assert.equal(r.api.share().ok,true);assert.equal('completion' in r.api.status(),false);
 const cancel=runtime();cancel.api.start();cancel.api.cancel();await cancel.flush();assert.equal(cancel.api.status().status,'cancelled');assert.equal(cancel.rendered,0);assert.equal(cancel.calls.some(c=>c[0]==='finish'),false);
 const broken=runtime({append:()=>JSON.stringify({ok:false,error:'codec failure'})});broken.api.start();await broken.flush();assert.equal(broken.api.status().status,'failed');assert.equal(broken.rendered,1);assert.equal(broken.calls.some(c=>c[0]==='cancel'),true);
 const invalid=runtime();for(const o of [{fps:31},{fps:0},{durationMs:16000},{durationMs:NaN}])assert.equal(invalid.api.start(o).ok,false);assert.equal(invalid.calls.length,0);
 invalid.api.setSource(()=>({width:767,height:512,durationMs:1000,render(){}}));assert.equal(invalid.api.start().ok,false);
 const denied=runtime({begin:()=>JSON.stringify({ok:false,error:'native bridge denied'})});assert.equal(denied.api.start().ok,false);assert.equal(denied.rendered,0);
 clearTimeout(watchdog);
 console.log('video-export: PASS (bounded settings, sequential frame acknowledgements, completion/share gate, cancellation, codec failure)');
})().catch(e=>{console.error(e);process.exitCode=1});
