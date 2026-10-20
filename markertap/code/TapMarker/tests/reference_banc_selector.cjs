const fs=require('fs');
const C=require('./reference-banc888/proxy-agent-core.js');
const A=require('./reference-banc888/autonomy-core.js');
const data=JSON.parse(fs.readFileSync('assets/banc888-recovery-provenance.json','utf8'));
const rows=[];
for(let route=0;route<4;route++)for(const drive of [.2,.4,.6,.9,1.2]){
 const selector=new C.ConnectomeSelector(data);
 const value=selector.propagate({tool:'native-reference',routeId:'o1p'+route,excitation:drive,confidence:1});
 rows.push(['OUTPUT',route,drive,value.signedOutput].join('\t'));
}
const selector=new C.ConnectomeSelector(data);
selector.select([{tool:'credit',routeId:'o1p2',excitation:.9,confidence:1}]);
selector.reinforce('credit',.2);
for(let edge=0;edge<16;edge++)rows.push(['GAIN',edge,selector.gains[edge]].join('\t'));
const learner=A.create();learner.syncTools([{name:'control.claim',executable:true}]);
const steps=[{tool:'control.claim',args:{label:'ゲット'}}];
const first=learner.recordEpisode('報酬を受け取る',steps,{success:true,reward:.2});
const second=learner.recordEpisode('報酬を受け取る',steps,{success:true,reward:.2});
if(first!==null||!second||learner.status().learnedSkills!==1)throw new Error('original BANC888 acquisition contract changed');
rows.push(['ACQUIRED',learner.status().learnedSkills,second.successes,second.attempts].join('\t'));
fs.writeFileSync('tests/banc-selector-reference.tsv',rows.join('\n')+'\n');
console.log('Original BANC888: '+20+' route outputs, 16 reward gains and 2-episode acquisition reference generated');
