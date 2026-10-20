package jp.tapmarker;
import java.util.*;

public final class LearnedReflowTest {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static final int W=240,H=440;static final float[] A={.12f,.15f,.42f,.26f},B={.55f,.63f,.85f,.74f};
 static RegionSampler.Pixels screen(float[] a,float[] b){return (x,y)->{float u=x/(float)W,v=y/(float)H;for(int i=0;i<2;i++){float[] r=i==0?a:b;if(r!=null&&u>=r[0]&&u<r[2]&&v>=r[1]&&v<r[3]){int c=VisualLocatorTest.pattern((u-r[0])/(r[2]-r[0]),(v-r[1])/(r[3]-r[1]));return i==0?c:c^0x006030d0;}}return 0xff18202c;};}
 public static void main(String[] args){
  PhaseEngine e=new PhaseEngine();PhaseEngine.Region a=new PhaseEngine.Region(),b=new PhaseEngine.Region();a.id="action";a.bounds=A.clone();b.id="guard";b.bounds=B.clone();e.regions.put(a.id,a);e.regions.put(b.id,b);
  PhaseEngine.Watch watch=new PhaseEngine.Watch();watch.id="tap";watch.regionId=a.id;watch.condition=PhaseEngine.MATCH;watch.action=PhaseEngine.CENTER;watch.threshold=.012f;watch.reference=RegionSampler.sample(W,H,A,screen(A,B));watch.guardIds=new String[]{b.id};watch.guardReferences=new float[][]{RegionSampler.sample(W,H,B,screen(A,B))};watch.touchPoint=new float[]{.27f,.205f};
  PhaseEngine.Phase phase=new PhaseEngine.Phase();phase.id="start";phase.watches.add(watch);e.phases.put(phase.id,phase);e.initial=phase.id;e.strictRuntime=true;e.enter(e.initial,0);
  float[] movedA={.63f,.70f,.93f,.81f},movedB={.08f,.38f,.38f,.49f};RegionSampler.Pixels moved=screen(movedA,movedB);LearnedLayout layout=new LearnedLayout();Map<String,float[]> samples=layout.sample(e,W,H,moved,1000);
  check(layout.independent&&!samples.isEmpty(),"recorded target and guard reacquired at independent positions");check(e.confirmsAction(watch,e.current,e.epoch,samples),"actual engine accepts the two fresh visual identities");
  float[] p=LearnedInput.local(watch,a,layout,W,H);check(p!=null&&p[0]>movedA[0]*W&&p[0]<movedA[2]*W&&p[1]>movedA[1]*H&&p[1]<movedA[3]*H,"touch remaps inside the actual action object");
  watch.action=PhaseEngine.MARKERS;watch.markerIds=new String[]{"deleted-overlay"};watch.markerPoints=watch.touchPoint.clone();check(LearnedInput.local(watch,a,layout,W,H)!=null,"saved repeated-touch path does not require the marker overlay");
  watch.action=PhaseEngine.CENTER;watch.touchPoint=new float[]{.95f,.95f};check(LearnedInput.local(watch,a,layout,W,H)==null,"reflow cannot infer a different unseen target from an unrelated landmark");watch.touchPoint=new float[]{.27f,.205f};
  samples=layout.sample(e,W,H,screen(null,movedB),4000);check(!e.confirmsAction(watch,e.current,e.epoch,samples),"missing target cannot use old guard or old coordinates");check(layout.pointFor(a.id,watch.touchPoint,W,H)==null,"disappearance revokes the previous point");
  a.windowLocal=b.windowLocal=true;Map<String,float[]> mixed=new HashMap<>();RecordedRepairSampling.sample(e,mixed,e.activeRegions(),W,H,moved,null);LearnedLayout repair=new LearnedLayout();RecordedRepairSampling.adapt(e,mixed,W,H,moved,repair,5000);float[] repaired=RecordedRepairSampling.point(e,watch,mixed,W,H);check(repaired!=null&&repaired[0]>movedA[0]*W&&repaired[1]>movedA[1]*H,"mixed preset/recording repair exports the independently located point");check(!mixed.containsKey(RecordedRepairSampling.TRANSFORM),"independent object positions cannot export a stale whole-screen transform");
  watch.touchPoint=new float[]{.97f,.96f};repair=new LearnedLayout();mixed=new HashMap<>();RecordedRepairSampling.sample(e,mixed,e.activeRegions(),W,H,moved,null);RecordedRepairSampling.adapt(e,mixed,W,H,moved,repair,7000);check(RecordedRepairSampling.point(e,watch,mixed,W,H)==null&&!repair.confirmed,"unrelated target is held when only its old landmark was relocated");
  System.out.println("LearnedReflowTest: "+checks+" checks passed; independent recorded-target and guard relocation");
 }
}
