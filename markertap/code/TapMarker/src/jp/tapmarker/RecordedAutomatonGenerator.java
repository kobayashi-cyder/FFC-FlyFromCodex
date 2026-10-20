package jp.tapmarker;

import java.util.*;

/** Bounded video observer. Pixels establish appearances; only reviewed annotations establish input. */
public final class RecordedAutomatonGenerator {
 public static final int MAX_FRAMES=600,MAX_STATES=32,UNREVIEWED=-1;
 public static final String PREFIX="record:";
 public static final class State {
  public int index,visits,action=UNREVIEWED,destination=-1;
  public long firstMs,lastMs,duration=300;
  public ObservationLearner.Frame frame,peer;
  public float[] path=new float[0];
 }
 public static final class Edge {
  public int from,to,count;public long firstMs;
  Edge(int a,int b,long at){from=a;to=b;count=1;firstMs=at;}
 }
 public static final class Model {
  public final List<State> states=new ArrayList<>();public final List<Edge> edges=new ArrayList<>();
  public int sampled,unstable,initial;public long startMs,endMs;
  public int[] destinations(int source){ArrayList<Integer> out=new ArrayList<>();for(Edge e:edges)if(e.from==source)out.add(e.to);int[] r=new int[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;}
  public void annotate(int index,int action,float[] path,long duration,int destination){
   State s=states.get(index);if(action!=PhaseEngine.WAIT&&action!=PhaseEngine.CENTER&&action!=PhaseEngine.CURVE)throw new IllegalArgumentException("タップ・スワイプ・操作なしを選んでください");
   if(action!=PhaseEngine.WAIT){if(path==null||!ObservationLearner.validPoints(path)||action==PhaseEngine.CENTER&&path.length!=2||action==PhaseEngine.CURVE&&path.length<4)throw new IllegalArgumentException("画面内の操作位置を指定してください");boolean found=false;for(int d:destinations(index))if(d==destination)found=true;if(!found)throw new IllegalArgumentException("録画で確認できた遷移先を選んでください");if(duration<1||duration>10000)throw new IllegalArgumentException("操作時間は1〜10000msです");}
   s.action=action;s.path=action==PhaseEngine.WAIT?new float[0]:path.clone();s.duration=duration;s.destination=action==PhaseEngine.WAIT?-1:destination;
  }
 }
 public static final class Result {public PhaseEngine engine;public int pending,regions,verificationStates;public String explanation;}
 private final Model model=new Model();private ObservationLearner.Frame previous;
 private long previousMs=-1;private int committed=-1,pending=-1,pendingCount;
 public static long interval(long start,long end,long requested){if(start<0||end<=start||requested<250)throw new IllegalArgumentException("録画範囲・取得間隔を確認してください");return Math.max(requested,(end-start+MAX_FRAMES-2)/(MAX_FRAMES-1));}
 static boolean steady(ObservationLearner.Frame a,ObservationLearner.Frame b){
  int eligible=0,stable=0,detail=0;float[][] x=a.tiles(),y=b.tiles();
  for(int row=0;row<16;row++)for(int col=0;col<8;col++){float[] bounds={col/8f,row/16f,(col+1)/8f,(row+1)/16f};if(!ObservationLearner.usable(a,bounds)||!ObservationLearner.usable(b,bounds))continue;eligible++;int i=row*8+col;if(FlyCircuit.distance(x[i],y[i])<=.008f){stable++;if(ObservationLearner.variance(x[i])>=.001f)detail++;}}
  return eligible>=8&&stable>=eligible*.75f&&detail>=3;
 }
 public void add(long at,ObservationLearner.Frame frame){
  if(frame==null||at<0||at<=previousMs)throw new IllegalArgumentException("録画の時刻とフレームを確認してください");if(model.sampled>=MAX_FRAMES)throw new IllegalStateException("録画は600フレームまでです。範囲を短くしてください");
  if(model.sampled++==0)model.startMs=at;model.endMs=at;
  if(previous==null||!steady(previous,frame)){model.unstable++;previous=frame;previousMs=at;pending=-1;pendingCount=0;return;}
  int index=-1;for(State s:model.states)if(ObservationLearner.same(s.frame,s.peer,frame,null)&&ObservationLearner.same(frame,previous,s.frame,null)){index=s.index;break;}
  if(index<0){if(model.states.size()>=MAX_STATES)throw new IllegalStateException("表示候補が32を超えました。録画を短くするか、変動する操作パネルを除外してください");State s=new State();s.index=model.states.size();s.frame=frame;s.peer=previous;s.firstMs=at;s.lastMs=at;model.states.add(s);index=s.index;}
  State state=model.states.get(index);state.lastMs=at;
  if(pending==index)pendingCount++;else{pending=index;pendingCount=1;}
  // Two steady pairs prevent a single decoded transition frame from becoming an action state.
  if(pendingCount>=2&&committed!=index){if(state.visits==0){state.frame=frame;state.peer=previous;}state.visits++;if(committed<0)model.initial=index;else{Edge found=null;for(Edge e:model.edges)if(e.from==committed&&e.to==index)found=e;if(found==null)model.edges.add(new Edge(committed,index,at));else found.count++;}committed=index;}
  previous=frame;previousMs=at;
 }
 public Model finish(){return finish(true);}
 public Model finishForRepair(){return finish(false);}
 private Model finish(boolean requireTransitions){
  if(committed<0)throw new IllegalStateException("安定した表示を取得できません。範囲や間隔を変え、同じ画面が3フレーム以上続く録画を選んでください");
  Model out=new Model();out.sampled=model.sampled;out.unstable=model.unstable;out.startMs=model.startMs;out.endMs=model.endMs;int[] ids=new int[model.states.size()];Arrays.fill(ids,-1);
  for(State s:model.states)if(s.visits>0){ids[s.index]=out.states.size();State c=new State();c.index=out.states.size();c.frame=s.frame;c.peer=s.peer;c.visits=s.visits;c.firstMs=s.firstMs;c.lastMs=s.lastMs;out.states.add(c);}
  out.initial=ids[model.initial];for(Edge e:model.edges){Edge c=new Edge(ids[e.from],ids[e.to],e.firstMs);c.count=e.count;out.edges.add(c);}
  if(requireTransitions&&(out.states.size()<2||out.edges.isEmpty()))throw new IllegalStateException("区別できる画面遷移がありません。操作前後を含む録画を選んでください");return out;
 }
 static PhaseEngine.Phase phase(PhaseEngine e,String id,String name,int index){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=PREFIX+id;p.name=name;p.graphX=40+(index%2)*290;p.graphY=65+(index/2)*150;e.phases.put(p.id,p);return p;}
 static PhaseEngine.Watch watch(PhaseEngine e,PhaseEngine.Phase p,ObservationLearner.Signature sig,String[] regions,String id,String name){
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=PREFIX+id;w.name=name;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.WAIT;w.threshold=.006f;w.waitMs=0;w.regionId=regions[0];w.reference=sig.refs.get(0);w.guardIds=Arrays.copyOfRange(regions,1,regions.length);w.guardReferences=new float[w.guardIds.length][];for(int i=1;i<regions.length;i++)w.guardReferences[i-1]=sig.refs.get(i);p.watches.add(w);return w;
 }
 public static Result compile(Model model){
  if(model==null||model.states.size()<2)throw new IllegalArgumentException("録画の解析結果がありません");
  Result result=new Result();PhaseEngine e=new PhaseEngine();e.windowRelative=true;e.strictRuntime=true;
  List<ObservationLearner.Frame> frames=new ArrayList<>();for(State s:model.states){frames.add(s.frame);frames.add(s.peer);}
  ObservationLearner.Signature[] signatures=new ObservationLearner.Signature[model.states.size()];String[][] regions=new String[model.states.size()][];PhaseEngine.Phase[] states=new PhaseEngine.Phase[model.states.size()];
  for(State s:model.states){int i=s.index;float[] input=s.action==PhaseEngine.CENTER||s.action==PhaseEngine.CURVE?s.path:null;signatures[i]=ObservationLearner.signature(s.frame,s.peer,input,frames,3);if(!ObservationLearner.matches(signatures[i],s.peer))throw new IllegalStateException("状態"+(i+1)+"の判定領域が安定していません。範囲を短くして再生成してください");regions[i]=new String[signatures[i].bounds.size()];for(int j=0;j<regions[i].length;j++){PhaseEngine.Region r=new PhaseEngine.Region();r.id=PREFIX+"roi:"+i+":"+j;r.name="録画状態"+(i+1)+" / 判定領域"+(j+1);r.bounds=signatures[i].bounds.get(j).clone();regions[i][j]=r.id;e.regions.put(r.id,r);}states[i]=phase(e,"state:"+i,"録画状態"+(i+1),i);}
  // Every source signature must reject every distinct recorded display in both stable samples.
  for(int i=0;i<signatures.length;i++)for(int j=0;j<signatures.length;j++)if(i!=j&&(ObservationLearner.matches(signatures[i],model.states.get(j).frame)||ObservationLearner.matches(signatures[i],model.states.get(j).peer)))throw new IllegalStateException("録画状態"+(i+1)+"と"+(j+1)+"の表示が曖昧です。操作範囲を分けて録画し直してください");
  for(State s:model.states){int[] outgoing=model.destinations(s.index);PhaseEngine.Phase p=states[s.index];if(outgoing.length==0){p.terminal=true;p.name+="：録画終端";continue;}
   if(s.action==UNREVIEWED){
    result.pending++;p.name+="：操作未登録";
    // The editable placeholder already checks the SOURCE display and retains post-input verification.
    // Changing WAIT to a reviewed gesture therefore cannot accidentally tap the destination screen.
    PhaseEngine.Phase verify=phase(e,"verify:"+s.index,"状態"+(s.index+1)+"：次の表示確認",model.states.size()+result.verificationStates++);
    PhaseEngine.Watch placeholder=watch(e,p,signatures[s.index],regions[s.index],"input:"+s.index,"操作未登録：元の表示を確認");placeholder.nextPhase=verify.id;placeholder.waitMs=300;
    for(int d:outgoing){PhaseEngine.Watch confirm=watch(e,verify,signatures[d],regions[d],"confirm:"+s.index+":"+d,"録画した次の表示を確認");confirm.nextPhase=states[d].id;}
    continue;
   }
   boolean operation=s.action==PhaseEngine.CENTER||s.action==PhaseEngine.CURVE;
   if(operation){model.annotate(s.index,s.action,s.path,s.duration,s.destination);PhaseEngine.Phase verify=phase(e,"verify:"+s.index,"状態"+(s.index+1)+"：操作後の表示確認",model.states.size()+result.verificationStates++);PhaseEngine.Watch w=watch(e,p,signatures[s.index],regions[s.index],"input:"+s.index,s.action==PhaseEngine.CENTER?"確認済みの位置を1回タップ":"確認済みのスワイプ");w.action=s.action;w.nextPhase=verify.id;w.duration=s.duration;w.waitMs=300;if(s.action==PhaseEngine.CENTER)w.touchPoint=s.path.clone();else{w.curve=s.path.clone();w.relativeCurve=true;}PhaseEngine.Watch confirm=watch(e,verify,signatures[s.destination],regions[s.destination],"confirm:"+s.index,"録画した遷移先の表示を確認");confirm.nextPhase=states[s.destination].id;}
   for(int d:outgoing)if(!operation||d!=s.destination){PhaseEngine.Watch w=watch(e,p,signatures[d],regions[d],"edge:"+s.index+":"+d,s.action==UNREVIEWED?"操作未登録：次の表示を待つ":"操作なし：次の表示を待つ");w.nextPhase=states[d].id;}
  }
  e.initial=states[model.initial].id;e.current=e.initial;e.recordingReviewRequired=result.pending>0;String issue=e.validate();if(!issue.isEmpty())throw new IllegalStateException(issue);ReferencePool.share(e);result.engine=e;result.regions=e.regions.size();result.explanation=model.sampled+"フレームから"+model.states.size()+"状態 / "+model.edges.size()+"種類の遷移を抽出\n同じ表示・同じ遷移を統合し、状態ごとに判定領域を共有します。\n下書きも元の表示と次の表示を分け、編集時に入力先を確認しやすくしています。\n確認済みの操作は、操作後の表示確認を経て進みます。\n"+(result.pending>0?result.pending+"状態で操作が未登録です。保存後も確認が完了するまで自動開始できません。":"操作確認済みです。保存後、実画面で手動開始して確認してください。")+"\n録画終端はデイリー完了を意味しません。短い表示や未録画の分岐は追加学習が必要です。";return result;
 }
}
