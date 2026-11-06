// SolZero original persona agent: explicit, bounded local actions only.
// No character/performer voice cloning, network calls, background task or hidden external action.
export const SOLZERO_VOICE_PRESET=Object.freeze({
  lang:'ja-JP', pitch:0.55, rate:1.1, volume:1,
  timbreNote:'低め・丸みのある日本語音声の目標。実音色は端末のTTS音声次第。'
});
const ACTIONS=new Set(['reason','create_media','add_task','add_note','verify']);
const safe=s=>String(s??'').trim().slice(0,400);
export class SolZeroPersonaAgent {
  constructor(){this.clear();}
  clear(){this.active=null;this.log=[];this.stopped=false;}
  remember(message){
    this.log.push({at:new Date().toISOString(),message:safe(message)});
    this.log=this.log.slice(-40);
  }
  makePlan(goal){
    goal=safe(goal).slice(0,180);
    if(!goal)throw Error('目標を入力してください。');
    const media=/(画像|イラスト|絵|写真|image|png)/i.test(goal)?'image':
      /(動画|アニメ|gif|video)/i.test(goal)?'gif':
      /(音声|音|効果音|audio|wav)/i.test(goal)?'audio':null;
    const taskLike=/(タスク|予定|todo|ToDo|やること)/i.test(goal);
    const action=media?'create_media':taskLike?'add_task':'add_note';
    const titles={reason:'目的を分析',create_media:'端末内でメディアを生成',add_task:'会話のタスクへ記録',add_note:'会話のメモへ記録',verify:'結果を検証'};
    const steps=['reason',action,'verify'].map(a=>({action:a,title:titles[a],status:'pending',output:''}));
    this.active={goal,media,steps,cursor:0,last:null,status:'planned'};
    this.stopped=false;this.remember('計画作成: '+goal);
    return this.describe();
  }
  describe(){
    if(!this.active)return '実行中の計画はありません。/agent plan 目標 で計画を作成できます。';
    const p=this.active;
    return '目標: '+p.goal+'\n状態: '+p.status+'\n'+p.steps.map((s,i)=>
      (i+1)+'. '+s.title+' ['+s.status+']'+(s.output?'\n   '+s.output.slice(0,180):'')).join('\n');
  }
  stop(){this.stopped=true;if(this.active&&this.active.status!=='completed')this.active.status='stopped';this.remember('停止');return 'エージェントの実行を停止しました。';}
  resume(){if(this.active&&this.active.status==='stopped'){this.stopped=false;this.active.status='planned';}}
  async step(services={},approved=false){
    const p=this.active;
    if(!p)return {ok:false,message:'まず /agent plan 目標 を実行してください。'};
    if(this.stopped)return {ok:false,message:'停止中です。/agent plan で新計画を作るか、/agent resume で再開してください。'};
    if(p.cursor>=p.steps.length)return {ok:false,message:'計画は完了しています。'};
    const item=p.steps[p.cursor];
    if(item.action!=='reason'&&!approved)return {ok:false,message:'この操作には実行承認が必要です。/agent step または /agent run を指定してください。'};
    p.status='running';item.status='running';
    try{
      let output='', evidence=null;
      if(item.action==='reason'){
        if(typeof services.reply!=='function')throw Error('会話エンジンが利用できません。');
        output=safe(services.reply(p.goal)).slice(0,350)||'目的を読み取りました。';
        evidence={kind:'reason',text:output};
      }else if(item.action==='create_media'){
        if(typeof services.generate!=='function')throw Error('生成機能が利用できません。');
        const asset=await services.generate(p.media,p.goal);
        if(!asset||!Number.isFinite(asset.bytes?.length)||asset.bytes.length<1)throw Error('生成ファイルを確認できません。');
        evidence={kind:'media',bytes:asset.bytes.length,name:safe(asset.name)};
        output='生成を確認: '+evidence.name+' ('+evidence.bytes+' bytes)';
      }else if(item.action==='add_task'){
        if(typeof services.addTask!=='function')throw Error('タスク保存機能が利用できません。');
        const stored=services.addTask(p.goal);
        if(!stored)throw Error('タスク保存を検証できません。');
        evidence={kind:'task',text:p.goal};output='タスクへの保存を確認しました。';
      }else if(item.action==='add_note'){
        if(typeof services.addNote!=='function')throw Error('メモ保存機能が利用できません。');
        const stored=services.addNote(p.goal);
        if(!stored)throw Error('メモ保存を検証できません。');
        evidence={kind:'note',text:p.goal};output='メモへの保存を確認しました。';
      }else if(item.action==='verify'){
        const good=p.last&&(
          (p.last.kind==='media'&&p.last.bytes>0)||
          ((p.last.kind==='task'||p.last.kind==='note')&&p.last.text.length>0));
        if(!good)throw Error('保存・生成の検証証拠がありません。');
        output='前工程の成果を確認しました。外部操作や実機検証は実行していません。';
        evidence=p.last;
      }else throw Error('未許可の操作です。');
      item.status='done';item.output=output;
      if(item.action!=='reason')p.last=evidence;
      p.cursor++;
      if(p.cursor===p.steps.length)p.status='completed';
      this.remember(item.title+': '+output);
      return {ok:true,message:output,completed:p.status==='completed',state:this.describe()};
    }catch(e){
      item.status='failed';item.output=safe(e?.message||e);
      p.status='failed';this.stopped=true;this.remember('失敗: '+item.output);
      return {ok:false,message:'処理を停止しました: '+item.output,state:this.describe()};
    }
  }
  async run(services={},approved=false,maxSteps=3){
    if(!approved)return {ok:false,messages:['実行承認が必要です。']};
    const results=[];
    for(let i=0;i<Math.max(0,Math.min(3,Math.floor(maxSteps)));i++){
      const r=await this.step(services,true);
      results.push(r.message);
      if(!r.ok||r.completed||this.stopped)break;
    }
    return {ok:results.length>0&&!this.stopped,messages:results,state:this.describe()};
  }
  snapshot(){return JSON.parse(JSON.stringify({active:this.active,log:this.log.slice(-40),stopped:this.stopped}));}
  restore(saved){
    this.clear();
    if(!saved||typeof saved!=='object')return false;
    const p=saved.active;
    if(p){
      if(typeof p.goal!=='string'||p.goal.length>180||!Array.isArray(p.steps)||p.steps.length!==3||
          !p.steps.every(s=>s&&ACTIONS.has(s.action)&&['pending','running','done','failed'].includes(s.status)&&typeof s.title==='string')||
          !Number.isInteger(p.cursor)||p.cursor<0||p.cursor>3)return false;
      const expected=['reason',p.media?'create_media':/(タスク|予定|todo|やること)/i.test(p.goal)?'add_task':'add_note','verify'];
      if(expected.some((a,i)=>p.steps[i].action!==a))return false;
      this.active={goal:p.goal,media:['image','gif','audio'].includes(p.media)?p.media:null,
        steps:p.steps.map(s=>({action:s.action,title:safe(s.title),status:s.status==='running'?'pending':s.status,output:safe(s.output)})),
        cursor:p.cursor,last:p.last&&typeof p.last==='object'?p.last:null,
        status:['planned','running','completed','stopped','failed'].includes(p.status)?p.status:'planned'};
    }
    this.log=Array.isArray(saved.log)?saved.log.slice(-40).filter(v=>v&&typeof v.message==='string').map(v=>({at:safe(v.at),message:safe(v.message)})):[];
    this.stopped=saved.stopped===true||this.active?.status==='failed';
    if(this.active?.status==='running')this.active.status='planned';
    return true;
  }
  persona(answer){
    const msg=String(answer??'');
    return '🐻 SolZero案内役: ふふっ、次の一手を見てみよう。\n'+msg;
  }
}
export class SolZeroVoice {
  constructor(synth=typeof speechSynthesis==='undefined'?null:speechSynthesis,
    Utterance=typeof SpeechSynthesisUtterance==='undefined'?null:SpeechSynthesisUtterance){
    this.synth=synth;this.Utterance=Utterance;this.enabled=false;
  }
  setEnabled(value){this.enabled=!!value; if(!this.enabled)this.cancel();return this.enabled;}
  cancel(){try{this.synth?.cancel();}catch{}}
  speak(message){
    if(!this.enabled||!this.synth||!this.Utterance)return false;
    const text=safe(message).slice(0,280);if(!text)return false;
    try{
      const speech=new this.Utterance(text);
      speech.lang=SOLZERO_VOICE_PRESET.lang;
      speech.pitch=SOLZERO_VOICE_PRESET.pitch;
      speech.rate=SOLZERO_VOICE_PRESET.rate;
      speech.volume=SOLZERO_VOICE_PRESET.volume;
      const voices=typeof this.synth.getVoices==='function'?this.synth.getVoices():[];
      speech.voice=voices.find(v=>v.lang==='ja-JP')||voices.find(v=>(v.lang||'').startsWith('ja'))||null;
      this.synth.cancel();this.synth.speak(speech);return true;
    }catch{return false;}
  }
}