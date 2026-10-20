package jp.tapmarker;

import java.util.*;

/** Compiles demonstrations, never explores an unobserved action. All coordinates are window local. */
public final class ObservationLearner {
 public static final int LIMIT=64;
 public static final String PREFIX="learn:";
 public static final class Frame {
  public final int width,height;final int[] pixels;public float[] excluded;private float[][] tiles;
  public Frame(int w,int h,int[] p){if(w<1||h<1||p.length!=w*h)throw new IllegalArgumentException("frame");width=w;height=h;pixels=p.clone();}
  public static Frame capture(int w,int h,RegionSampler.Pixels source){
   int cw=Math.min(192,w),ch=Math.max(1,Math.min(480,Math.round(cw*h/(float)w)));int[] p=new int[cw*ch];
   for(int y=0;y<ch;y++)for(int x=0;x<cw;x++){int red=0,green=0,blue=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int c=source.get(Math.min(w-1,(int)((x+(dx+.5f)/3)*w/cw)),Math.min(h-1,(int)((y+(dy+.5f)/3)*h/ch)));red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;}p[y*cw+x]=0xff000000|((red/9)<<16)|((green/9)<<8)|(blue/9);}
   return new Frame(cw,ch,p);
  }
  float[][] tiles(){if(tiles!=null)return tiles;tiles=new float[128][48];for(int ty=0;ty<16;ty++)for(int tx=0;tx<8;tx++)for(int y=0;y<4;y++)for(int x=0;x<4;x++){
   float red=0,green=0,blue=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int px=Math.min(width-1,(int)((tx+(x+(dx+.5f)/3)/4)*width/8)),py=Math.min(height-1,(int)((ty+(y+(dy+.5f)/3)/4)*height/16)),c=pixels[py*width+px];red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;}int i=(y*4+x)*3;tiles[ty*8+tx][i]=red/(9*255);tiles[ty*8+tx][i+1]=green/(9*255);tiles[ty*8+tx][i+2]=blue/(9*255);
  }return tiles;}
  public float[] sample(float[] region){return RegionSampler.sample(width,height,region,(x,y)->pixels[y*width+x]);}
 }
 public static final class Step {
  public Frame before,peer,after,afterPeer;
  public int action=PhaseEngine.CENTER;
  public float[] path=new float[0],markerPoints=new float[0];
  public String[] markerIds=new String[0];
  public long duration=60;
 }
 public static final class Result {
  public PhaseEngine engine;public boolean loops;public int steps,regions,states,confirmationStates,ambiguousStates,passiveTransitions;public String explanation;
 }
 public final List<Step> steps=new ArrayList<>();
 public void add(Step step){
  if(steps.size()>=LIMIT)throw new IllegalStateException("学習は64操作までです。終了・作成してください");
  if(step.before==null||step.after==null)throw new IllegalArgumentException("操作前後の画面が必要です");
  if(step.action==PhaseEngine.CENTER&&(step.path.length<2||!validPoints(step.path)))throw new IllegalArgumentException("タップ位置");
  if(step.action==PhaseEngine.CURVE&&(step.path.length<4||!validPoints(step.path)))throw new IllegalArgumentException("曲線");
  if(step.action==PhaseEngine.MARKERS&&(step.markerIds.length==0||step.markerPoints.length!=step.markerIds.length*2||!validPoints(step.markerPoints)))throw new IllegalArgumentException("マーカー");
  if(step.action!=PhaseEngine.CENTER&&step.action!=PhaseEngine.CURVE&&step.action!=PhaseEngine.MARKERS)throw new IllegalArgumentException("未対応の実演");
  steps.add(step);
 }
 static boolean validPoints(float[] p){if(p.length%2!=0)return false;for(float v:p)if(!Float.isFinite(v)||v<0||v>=1)return false;return true;}
 static final class Signature {List<float[]> bounds=new ArrayList<>(),refs=new ArrayList<>();}
 static float variance(float[] f){double value=0;for(int color=0;color<3;color++){double mean=0;for(int i=color;i<f.length;i+=3)mean+=f[i];mean/=f.length/3;for(int i=color;i<f.length;i+=3)value+=(f[i]-mean)*(f[i]-mean);}return (float)(value/f.length);}
 static float[] around(float x,float y){float halfW=.09f,halfH=.0375f;float left=Math.max(.001f,Math.min(1-2*halfW-.001f,x-halfW)),top=Math.max(.001f,Math.min(1-2*halfH-.001f,y-halfH));return new float[]{left,top,left+2*halfW,top+2*halfH};}
 static boolean usable(Frame f,float[] r){float[] a=f.excluded;return a==null||r[2]<=a[0]||r[0]>=a[2]||r[3]<=a[1]||r[1]>=a[3];}
 static float centerY(float[] r){return (r[1]+r[3])/2;}
 static Signature signature(Frame frame,Frame peer,float[] target,List<Frame> others){return signature(frame,peer,target,others,2);}
 static Signature signature(Frame frame,Frame peer,float[] target,List<Frame> others,int minimumAnchors){
  ArrayList<float[]> candidates=new ArrayList<>();if(target!=null&&target.length>=2)candidates.add(around(target[0],target[1]));
  for(int y=0;y<10;y++)for(int x=0;x<5;x++)candidates.add(around(.1f+x*.2f,.07f+y*.09f));for(int y=0;y<16;y++)for(int x=0;x<8;x++)candidates.add(new float[]{x/8f,y/16f,(x+1)/8f,(y+1)/16f});
  Signature signature=new Signature();boolean[] selected=new boolean[candidates.size()];ArrayList<Frame> distinct=new ArrayList<>();for(Frame other:others)if(other!=frame&&!same(frame,peer,other,null))distinct.add(other);boolean[] covered=new boolean[distinct.size()];
  // Textured, steady anchors outrank counters and animation. Keep vertically separate evidence.
  for(int pass=0;pass<6;pass++){int best=-1;float bestScore=-1;for(int i=0;i<candidates.size();i++){
    if(selected[i])continue;float[] roi=candidates.get(i);if(!usable(frame,roi)||peer!=null&&!usable(peer,roi))continue;float[] ref=frame.sample(roi);float drift=peer==null?0:FlyCircuit.distance(ref,peer.sample(roi));float detail=variance(ref);
    if(drift>.008f)continue;
    boolean overlaps=false;for(float[] prior:signature.bounds)if(Math.abs(centerY(prior)-centerY(roi))<.14f){overlaps=true;break;}if(overlaps&&pass==1)continue;
    float contrast=0;for(Frame other:others)contrast=Math.max(contrast,Math.min(.10f,FlyCircuit.distance(ref,other.sample(roi))));
    int gain=0;for(int j=0;j<distinct.size();j++)if(!covered[j]&&FlyCircuit.distance(ref,distinct.get(j).sample(roi))>.006f)gain++;if(detail<.001f&&gain==0)continue;float score=detail+contrast*.7f-drift*4+gain*2;
    if(i==0&&target!=null)score+=pass==0?10000:.045f;
    if(score>bestScore){best=i;bestScore=score;}
   }
   if(best<0)break;selected[best]=true;float[] roi=candidates.get(best);signature.bounds.add(roi);signature.refs.add(frame.sample(roi));boolean all=true;for(int j=0;j<distinct.size();j++){if(FlyCircuit.distance(signature.refs.get(signature.refs.size()-1),distinct.get(j).sample(roi))>.006f)covered[j]=true;all&=covered[j];}if(all&&signature.bounds.size()>=minimumAnchors)break;
  }
  for(boolean distinguished:covered)if(!distinguished)throw new IllegalStateException("表示を区別できる安定した領域が不足しています。画面が落ち着いた状態で学習し直してください");
  if(signature.bounds.isEmpty())throw new IllegalStateException("安定した表示を記録できませんでした。画面が落ち着いてから学習してください");
  return signature;
 }
 static boolean matches(Signature signature,Frame frame){for(int i=0;i<signature.bounds.size();i++)if(FlyCircuit.distance(signature.refs.get(i),frame.sample(signature.bounds.get(i)))>.006f)return false;return true;}
 static PhaseEngine.Phase phase(PhaseEngine engine,String id,String name,int index){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=PREFIX+id;p.name=name;p.graphX=40+(index%2)*290;p.graphY=65+(index/2)*150;engine.phases.put(p.id,p);return p;}
 static PhaseEngine.Watch watch(PhaseEngine engine,PhaseEngine.Phase phase,Signature signature,String id,String name){
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=PREFIX+id;w.name=name;w.condition=PhaseEngine.MATCH;w.waitMs=0;w.threshold=.006f;
  w.guardIds=new String[Math.max(0,signature.bounds.size()-1)];w.guardReferences=new float[w.guardIds.length][];
  for(int i=0;i<signature.bounds.size();i++){PhaseEngine.Region r=new PhaseEngine.Region();r.id=w.id+":roi:"+i;r.name=name+" / 領域"+(i+1);r.bounds=signature.bounds.get(i).clone();engine.regions.put(r.id,r);if(i==0){w.regionId=r.id;w.reference=signature.refs.get(i).clone();}else{w.guardIds[i-1]=r.id;w.guardReferences[i-1]=signature.refs.get(i).clone();}}
  phase.watches.add(w);return w;
 }
 /** Symmetric pixel equivalence over steady tiles, independently of the demonstrated action. */
 public static boolean same(Frame a,Frame peerA,Frame b,Frame peerB){
  if(a.width==b.width&&a.height==b.height&&Arrays.equals(a.pixels,b.pixels))return true;int evidence=0;float[][] ta=a.tiles(),tb=b.tiles(),pa=peerA==null?null:peerA.tiles(),pb=peerB==null?null:peerB.tiles();
  for(int y=0;y<16;y++)for(int x=0;x<8;x++){
   float[] roi=new float[]{x/8f,y/16f,(x+1)/8f,(y+1)/16f};if(!usable(a,roi)||!usable(b,roi)||peerA!=null&&!usable(peerA,roi)||peerB!=null&&!usable(peerB,roi))continue;float[] fa=ta[y*8+x],fb=tb[y*8+x];
   if(pa!=null&&FlyCircuit.distance(fa,pa[y*8+x])>.008f||pb!=null&&FlyCircuit.distance(fb,pb[y*8+x])>.008f)continue;
   evidence++;if(FlyCircuit.distance(fa,fb)>.006f)return false;
  }
  return evidence>=3;
 }
 static boolean sameAction(Step a,Step b){
  if(a.action!=b.action)return false;
  if(a.action==PhaseEngine.MARKERS)return Arrays.equals(a.markerIds,b.markerIds)&&closePoints(a.markerPoints,b.markerPoints);
  return closePoints(a.path,b.path)&&(a.action!=PhaseEngine.CURVE||Math.abs(a.duration-b.duration)<300);
 }
 static boolean closePoints(float[] a,float[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i])>.015f)return false;return true;}
 static final class Cluster {List<Frame> frames=new ArrayList<>(),peers=new ArrayList<>();PhaseEngine.Phase phase;}
 static int cluster(List<Cluster> clusters,Frame frame,Frame peer){
  for(int i=0;i<clusters.size();i++){Cluster c=clusters.get(i);boolean identical=true;for(int j=0;j<c.frames.size();j++)if(!same(c.frames.get(j),c.peers.get(j),frame,peer)){identical=false;break;}if(identical){c.frames.add(frame);c.peers.add(peer);return i;}}
  Cluster c=new Cluster();c.frames.add(frame);c.peers.add(peer);clusters.add(c);return clusters.size()-1;
 }
 static final class Outcome {int destination;Frame frame;Signature signature;PhaseEngine.Watch watch;}
 static final class Edge {int source;Step step;List<Step> members=new ArrayList<>();List<Signature> inputs=new ArrayList<>();PhaseEngine.Phase verify;List<Outcome> outcomes=new ArrayList<>();}
 static void operation(PhaseEngine.Watch w,Step step,PhaseEngine.Phase verify){
  w.action=step.action;w.nextPhase=verify.id;w.duration=Math.max(1,Math.min(10000,step.duration));
  if(step.action==PhaseEngine.CENTER)w.touchPoint=new float[]{step.path[0],step.path[1]};else if(step.action==PhaseEngine.CURVE){w.curve=step.path.clone();w.relativeCurve=true;}else{w.markerIds=step.markerIds.clone();w.markerPoints=step.markerPoints.clone();}
 }
 static boolean matches(PhaseEngine engine,PhaseEngine.Watch w,Frame display){
  if(FlyCircuit.distance(w.reference,display.sample(engine.regions.get(w.regionId).bounds))>w.threshold)return false;
  for(int k=0;k<w.guardIds.length;k++)if(FlyCircuit.distance(w.guardReferences[k],display.sample(engine.regions.get(w.guardIds[k]).bounds))>w.threshold)return false;
  return true;
 }
 public Result compile(boolean repeat){
  if(steps.isEmpty())throw new IllegalStateException("まだ操作を記録していません。対象画面を操作してください");
  PhaseEngine engine=new PhaseEngine();engine.windowRelative=true;engine.strictRuntime=true;
  ArrayList<Frame> frames=new ArrayList<>();for(Step step:steps){frames.add(step.before);frames.add(step.after);}
  ArrayList<Signature> before=new ArrayList<>(),after=new ArrayList<>();for(Step step:steps){before.add(signature(step.before,step.peer,step.action==PhaseEngine.MARKERS?step.markerPoints:step.path,frames));after.add(signature(step.after,step.afterPeer,null,frames));}
  ArrayList<Cluster> clusters=new ArrayList<>();int[] sources=new int[steps.size()],destinations=new int[steps.size()];
  for(int i=0;i<steps.size();i++)sources[i]=cluster(clusters,steps.get(i).before,steps.get(i).peer);
  int sourceStates=clusters.size();for(int i=0;i<steps.size();i++)destinations[i]=cluster(clusters,steps.get(i).after,steps.get(i).afterPeer);
  int lastState=destinations[steps.size()-1];boolean closes=repeat&&lastState<sourceStates;
  for(int i=0;i<clusters.size();i++)clusters.get(i).phase=phase(engine,"state:"+i,"状態"+(i+1),i*2);
  ArrayList<Edge> edges=new ArrayList<>();int index=0;
  for(int i=0;i<steps.size();i++){
   Step step=steps.get(i);Edge edge=null;for(Edge candidate:edges)if(candidate.source==sources[i]){boolean equivalent=true;for(Step member:candidate.members)if(!sameAction(member,step)){equivalent=false;break;}if(equivalent){edge=candidate;break;}}
   if(edge==null){edge=new Edge();edge.source=sources[i];edge.step=step;edge.verify=phase(engine,"verify:"+index,"状態"+(sources[i]+1)+"：操作後の確認"+(index+1),index*2+1);edges.add(edge);index++;}
   edge.members.add(step);boolean covered=false;for(Signature sig:edge.inputs)if(matches(sig,step.before)){covered=true;break;}
   if(!covered){Signature sig=before.get(i);PhaseEngine.Watch input=watch(engine,clusters.get(sources[i]).phase,sig,"input:"+i,"記録表示で"+(step.action==PhaseEngine.MARKERS?"マーカー連打":step.action==PhaseEngine.CURVE?"曲線操作":"タップ"));operation(input,edge.step,edge.verify);edge.inputs.add(sig);}
   int destination=destinations[i];Signature target=after.get(i);boolean stop=i==steps.size()-1&&!repeat;
   // Same visual state, action and result cannot mean both continue and stop without extra evidence.
   for(Outcome outcome:edge.outcomes)if(outcome.destination==destination&&stop)throw new IllegalStateException("同じ操作・同じ結果に「続行」と「停止」が重なっています。繰り返しを選ぶか、区別できる完了表示で学習を終えてください");
   boolean duplicate=false;for(Outcome outcome:edge.outcomes)if(outcome.destination==destination&&matches(outcome.signature,step.after)&&matches(target,outcome.frame)){duplicate=true;break;}
   if(!duplicate){PhaseEngine.Watch confirm=watch(engine,edge.verify,target,"confirm:"+i,"状態"+(destination+1)+"の表示を確認");confirm.action=stop?PhaseEngine.STOP:PhaseEngine.WAIT;confirm.nextPhase=stop?"":clusters.get(destination).phase.id;Outcome outcome=new Outcome();outcome.destination=destination;outcome.frame=step.after;outcome.signature=target;outcome.watch=confirm;edge.outcomes.add(outcome);}
  }
  int passive=0;
  // Preserve input-free changes between the actual result and the next demonstration.
  for(int i=0;i+1<steps.size();i++)if(destinations[i]!=sources[i+1]){
   PhaseEngine.Phase source=clusters.get(destinations[i]).phase,target=clusters.get(sources[i+1]).phase;Signature sig=before.get(i+1);boolean covered=false;
   for(PhaseEngine.Watch w:source.watches)if(w.action==PhaseEngine.WAIT&&w.nextPhase.equals(target.id)&&matches(engine,w,steps.get(i+1).before)){covered=true;break;}
   if(!covered){PhaseEngine.Watch w=watch(engine,source,sig,"passive:"+i,"次の記録表示への自然な変化");w.action=PhaseEngine.WAIT;w.nextPhase=target.id;passive++;}
  }
  PhaseEngine.Phase finalState=clusters.get(lastState).phase;if(finalState.watches.isEmpty()){finalState.name="状態"+(lastState+1)+"：学習した手順の完了";finalState.terminal=true;}
  int ambiguous=0;for(Cluster c:clusters){boolean conflict=false;for(Frame display:c.frames)for(int i=0;i<c.phase.watches.size();i++)for(int j=i+1;j<c.phase.watches.size();j++){
   PhaseEngine.Watch a=c.phase.watches.get(i),b=c.phase.watches.get(j);if(!AutomatonGraph.sameEffect(engine,c.phase,a,b)&&matches(engine,a,display)&&matches(engine,b,display))conflict=true;
  }if(conflict){ambiguous++;c.phase.name+="：条件の区別が必要";}}
  engine.initial=clusters.get(sources[0]).phase.id;engine.current=engine.initial;String error=engine.validate();if(!error.isEmpty())throw new IllegalStateException(error);
  Result result=new Result();result.engine=engine;result.loops=closes;result.steps=steps.size();result.states=clusters.size();result.confirmationStates=edges.size();result.regions=engine.regions.size();result.ambiguousStates=ambiguous;result.passiveTransitions=passive;
  result.explanation=closes?"同じ表示を同じ状態に統合し、実際の操作前後を確認して繰り返します":"同じ表示を同じ状態に統合しました。未記録の操作は実行しません";
  if(passive>0)result.explanation+="\n操作を挟まずに変わった表示も、"+passive+"本の確認用の矢印として保持しました";
  if(ambiguous>0)result.explanation+="\n"+ambiguous+"状態で同じ表示に異なる操作が記録されています。自律では保留します。編集で表示の区別条件を追加してください";
  return result;
 }
}
