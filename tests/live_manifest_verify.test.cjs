const fs=require('node:fs');
const crypto=require('node:crypto');
const path=require('node:path');
const manifest=JSON.parse(fs.readFileSync('app/src/main/assets/live-manifest.json','utf8'));
if(manifest.schema!==1||manifest.bridgeSchema!==8||manifest.assetSchema!==12||manifest.minShellVersion>76)throw new Error('live manifest schema mismatch');
for(const [name,expected] of Object.entries(manifest.files)){
  const p=path.join('app/src/main/assets',name);
  if(!fs.existsSync(p))throw new Error('missing live asset '+name);
  const actual=crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
  if(actual!==expected)throw new Error(name+' SHA-256 mismatch: '+actual+' != '+expected);
}
const expectedNames=[
'index.html','native-bridge.js','webview-runtime.js','thread-router-core.js','capability-vocabulary-core.js','image-quality-core.js','feedback-core.js','autonomy-core.js','research-physics-core.js','ir-patch-core.js','proxy-agent-core.js','ai-compat-core.js','capability-tools.js','video-quality-core.js','video-export.js','a1111-video.js','research-physics-tools.js','fly-parallel-core.js','local-model.js','fly-parallel.js','proxy-agent.js','conversation-output-core.js','thread-router.js','conversation-export.js','dev-live.js','execution-controls.js','attachment-core.js','attachments.js','settings-organizer.js','artifact-workspace.js','workspace-productivity.js','fly-task-worker.js'
].sort();
const actualNames=Object.keys(manifest.files).sort();
if(JSON.stringify(expectedNames)!==JSON.stringify(actualNames))throw new Error('live asset set mismatch');
console.log('live-manifest: PASS',manifest.commit);
