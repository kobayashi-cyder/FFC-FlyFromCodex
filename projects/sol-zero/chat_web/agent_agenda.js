// Sol-Zero local agenda: no network, no background execution, no arbitrary actions.
// Existing PersonaAgent remains the only authority for whitelisted local steps.
const MAX_GOALS=24, MAX_HISTORY=60, MAX_BATCH=2;
const PRIORITY=Object.freeze({high:3,normal:2,low:1});
const clip=(value,length)=>String(value??'').trim().slice(0,length);
const ISO=()=>new Date().toISOString();
const supportedGoal=goal=>/(画像|イラスト|絵|写真|image|png|動画|アニメ|gif|video|音声|音|効果音|audio|wav)/iu.test(goal)||/^(タスク|予定|todo|メモ)\s+\S/iu.test(goal);
let sequence=0;
export class SolZeroAgenda {
  constructor(){this.queue=[];this.history=[];this.paused=false;this.running=false;}
  record(action,detail=''){
    this.history.push({at:ISO(),action:clip(action,24),detail:clip(detail,200)});
    this.history=this.history.slice(-MAX_HISTORY);
  }
  add(goal,priority='normal',chatId=null){
    goal=clip(goal,180);
    if(!goal)throw Error('目標を入力してください。');
    if(!supportedGoal(goal))throw Error('未対応の目標です。画像・音声・動画生成、または「タスク ...」「メモ ...」を指定してください。');
    if(!Object.hasOwn(PRIORITY,priority))throw Error('優先度は high、normal、low のいずれかです。');
    if(chatId!==null&&(typeof chatId!=='string'||chatId.length>100))throw Error('会話IDが不正です。');
    if(this.queue.length>=MAX_GOALS)throw Error('目標の上限です。完了済みを整理してください。');
    const item={id:'g'+Date.now().toString(36)+'-'+(++sequence),goal,priority,chatId,status:'pending',created:ISO(),finished:null,detail:''};
    this.queue.push(item);this.record('queued',goal);
    return item;
  }
  get(id){return this.queue.find(q=>q.id===id)||null;}
  pending(){
    return this.queue.filter(g=>g.status==='pending').sort((a,b)=>PRIORITY[b.priority]-PRIORITY[a.priority]||
      a.created.localeCompare(b.created)||this.queue.indexOf(a)-this.queue.indexOf(b));
  }
  cancel(id){
    const item=this.get(id);
    if(!item)return false;
    if(item.status==='running')throw Error('実行中は停止操作を使ってください。');
    if(item.status!=='pending')return false;
    item.status='cancelled';item.finished=ISO();this.record('cancelled',item.goal);return true;
  }
  clearCompleted(){
    if(this.running)throw Error('実行中は整理できません。');
    const len=this.queue.length;
    this.queue=this.queue.filter(q=>['pending','running','interrupted'].includes(q.status));
    this.record('cleanup',String(len-this.queue.length)+'件整理');return len-this.queue.length;
  }
  pause(agent){
    this.paused=true;
    if(agent?.active?.status==='running'||agent?.active?.status==='planned')agent.stop();
    this.record('pause','ユーザー操作');return true;
  }
  resume(){this.paused=false;this.record('resume');return true;}
  acknowledge(agent){
    if(this.running)throw Error('実行中は中断を確認できません。');
    if(!this.queue.some(x=>x.status==='interrupted'||x.status==='failed')&&
        !['failed','stopped'].includes(agent?.active?.status))throw Error('確認を要する中断がありません。');
    agent?.clear?.(); // Clear only the active plan; agenda history and failed items remain.
    this.paused=false;
    this.record('acknowledged','失敗済み目標は再実行せず、後続の未処理目標だけ許可');
    return true;
  }

  suggest(conversation){
    const tasks=Array.isArray(conversation?.tasks)?conversation.tasks:[];
    const already=new Set(this.queue.filter(x=>x.status==='pending').map(x=>x.goal));
    const pending=tasks.filter(t=>t&&t.done!==true&&typeof t.text==='string')
      .map(t=>clip(t.text,160)).filter(t=>t&&!already.has(t)).slice(0,5);
    return pending.length?'未完了タスクからの候補（未登録・未実行）:\n'+pending.map((t,i)=>String(i+1)+'. '+t).join('\n'):
      '未完了の追加候補はありません。';
  }
  status(){
    const ready=this.pending(),totals={};
    for(const q of this.queue)totals[q.status]=(totals[q.status]||0)+1;
    const items=this.queue.map(x=>x.id+' ['+x.status+'/'+x.priority+'] '+x.goal+(x.detail?'\n  '+x.detail:''));
    return '🐻 SolZero業務ボード: '+(this.paused?'一時停止':'待機')+' / 未処理 '+ready.length+'件 / 完了 '+(totals.completed||0)+'件\n'+
       (items.length?items.join('\n'):'キューは空です。')+
       '\n※ユーザーが実行を承認したときだけ動きます。';
  }
  async runNext(agent,services,{approved=false,checkpoint=()=>true}={}){
    if(!approved)return {ok:false,message:'実行は未承認です。/agent queue run で明示承認してください。'};
    if(this.paused)return {ok:false,message:'一時停止中です。/agent queue resume で再開してください。'};
    if(this.running)return {ok:false,message:'既に実行中です。'};
    const item=this.pending()[0];
    if(!item)return {ok:false,message:'実行可能な目標がありません。'};
    if(!supportedGoal(item.goal)){
      item.status='failed';item.detail='この目標は実行可能なローカル操作に対応していません。';item.finished=ISO();
      this.record('unsupported',item.goal);checkpoint();
      return {ok:false,itemId:item.id,message:item.detail,status:item.status};
    }
    if(agent?.active&&agent.active.status!=='completed'&&agent.active.cursor<agent.active.steps.length){
      return {ok:false,message:'未完了の単独エージェント計画があります。/agent status で確認してください。'};
    }
    this.running=true;
    item.status='running';this.record('started',item.goal);
    try{
      const scopedServices=typeof services==='function'?services(item):services;
      if(!scopedServices||typeof scopedServices!=='object')throw Error('会話スコープを特定できません。');
      agent.makePlan(item.goal);
      if(checkpoint()===false)throw Error('実行前の状態保存に失敗しました。');
      const result=[];
      for(let i=0;i<3;i++){
        if(this.paused){item.status='interrupted';item.detail='停止されたため再実行しません。必要なら内容を確認して新規登録してください。';break;}
        const step=await agent.step(scopedServices,true);
        result.push(step.message);
        if(checkpoint()===false)throw Error('保存に失敗しました。副作用の再実行を防ぐため停止します。');
        if(!step.ok){item.status='failed';item.detail=clip(step.message,200);break;}
        if(step.completed){item.status='completed';item.detail='3工程で完了、検証済み';item.finished=ISO();break;}
      }
      if(item.status==='running'){item.status='interrupted';item.detail='完了を証明できないため自動再開しません。';}
      this.record(item.status,item.goal);
      return {ok:item.status==='completed',itemId:item.id,message:result.join('\n')||item.detail,status:item.status};
    }catch(err){
      item.status='interrupted';
      item.detail=clip(err?.message||err,200);
      this.paused=true;
      agent?.stop?.();
      this.record('interrupted',item.detail);
      return {ok:false,itemId:item.id,message:'処理中断: '+item.detail,status:'interrupted'};
    }finally{
      this.running=false;
      checkpoint();
    }
  }
  async runBatch(agent,services,{approved=false,maxGoals=1,checkpoint=()=>true}={}){
    if(!approved)return {ok:false,results:[],message:'実行の承認が必要です。'};
    const max=Math.max(1,Math.min(MAX_BATCH,Math.trunc(Number(maxGoals)||1)));
    const results=[];
    for(let i=0;i<max;i++){
      const r=await this.runNext(agent,services,{approved:true,checkpoint});
      results.push(r);
      if(!r.ok||this.paused)break;
    }
    return {ok:results.length>0&&results.every(r=>r.ok),results,
      message:results.map(r=>'['+(r.itemId||'-')+'] '+(r.status||'中断')+': '+r.message).join('\n')};
  }
  snapshot(){
    return {queue:this.queue.map(x=>({...x})),history:this.history.slice(-MAX_HISTORY).map(x=>({...x})),paused:this.paused};
  }
  restore(value){
    this.queue=[];this.history=[];this.paused=false;this.running=false;
    if(!value||typeof value!=='object')return false;
    if(!Array.isArray(value.queue)||value.queue.length>MAX_GOALS||!Array.isArray(value.history)||value.history.length>MAX_HISTORY)return false;
    const ids=new Set();
    for(const x of value.queue){
      if(!x||typeof x.id!=='string'||x.id.length>80||ids.has(x.id)||typeof x.goal!=='string'||x.goal.length>180||
          !Object.hasOwn(PRIORITY,x.priority)||(x.chatId!=null&&(typeof x.chatId!=='string'||x.chatId.length>100))||!['pending','running','interrupted','failed','completed','cancelled'].includes(x.status))return false;
      ids.add(x.id);
      this.queue.push({id:x.id,goal:x.goal,priority:x.priority,chatId:x.chatId??null,status:x.status==='running'?'interrupted':x.status,
        created:clip(x.created,40),finished:typeof x.finished==='string'?clip(x.finished,40):null,
        detail:x.status==='running'?'前回セッションは中断。副作用を自動再実行しません。':clip(x.detail,200)});
    }
    if(!value.history.every(x=>x&&typeof x.action==='string'&&typeof x.detail==='string'))return false;
    this.history=value.history.map(x=>({at:clip(x.at,40),action:clip(x.action,24),detail:clip(x.detail,200)}));
    this.paused=value.paused===true||this.queue.some(x=>x.status==='interrupted');
    return true;
  }
}