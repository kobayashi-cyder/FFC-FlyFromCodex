package jp.tapmarker;
import java.util.*;

/** Deterministic preview-only graph tests; recognition and input must be independent. */
public final class PreviewConnectomeTest {
 static int checks;
 static void check(boolean good,String message){checks++;if(!good)throw new AssertionError(message);}
 public static void main(String[] args){
  RegionPlan plan=RegionPlan.full(96,64,"three-by-two");
  PreviewConnectome graph=new PreviewConnectome();
  final int[] reads={0};
  RegionSampler.Pixels pixels=(x,y)->{reads[0]++;return 0xff112233;};
  RegionPreviewData sample=RegionPreviewData.capture(plan,96,1000,pixels);
  check(sample.patches.size()==6,"six base patches");
  check(reads[0]==96*64,"full initial preview read");
  PreviewConnectome.Schedule initial=graph.schedule(plan.previewKey,96,64,1000);
  check(initial.count()==6,"first frame samples every node");
  BitSet first=new BitSet();first.set(0,6);
  graph.observed(plan.previewKey,96,64,1000,sample.patches,first);
  // Stabilise all nodes; repeated captures become unnecessary on the preview path.
  for(int i=1;i<=12;i++)graph.observed(plan.previewKey,96,64,1000+i*100,sample.patches,new BitSet());
  PreviewConnectome.Schedule quiet=graph.schedule(plan.previewKey,96,64,2250);
  check(quiet.count()==0,"stable nodes skip unnecessary preview pixel reads");
  reads[0]=0;
  RegionPreviewData notDue=RegionPreviewData.capture(plan,96,2250,pixels,quiet);
  check(notDue.patches.isEmpty()&&reads[0]==0,"no read when every node retained");
  PreviewConnectome.Schedule refreshed=graph.schedule(plan.previewKey,96,64,3500);
  check(refreshed.count()==6,"quiet regions are refreshed within timeout");
  BitSet middle=new BitSet();middle.set(1);
  graph.observed(plan.previewKey,96,64,3500,sample.patches,middle);
  PreviewConnectome.Schedule adjacent=graph.schedule(plan.previewKey,96,64,3550);
  check(adjacent.includes(32,0),"changed patch sampled immediately");
  check(adjacent.includes(0,0),"left neighbour excited");
  check(adjacent.includes(64,0),"right neighbour excited");
  check(adjacent.includes(32,32),"below neighbour excited");
  check(!adjacent.includes(0,32)&&!adjacent.includes(64,32),"distant static regions stay asleep");
  check(graph.changes>=7&&graph.neighbourSignals>=3,"graph records actual activity");
  PreviewConnectome.Schedule changedScope=graph.schedule("new-geometry",96,64,3600);
  check(changedScope.count()==6,"new scene invalidates all stored classifications");
  PreviewConnectome.Schedule changedGeometry=graph.schedule("new-geometry",64,64,3600);
  check(changedGeometry.count()==4,"new geometry rebuilds node topology");
  boolean rejected=false;
  try{RegionPreviewData.capture(plan,96,3600,pixels,changedGeometry);}
  catch(IllegalArgumentException ok){rejected=true;}
  check(rejected,"stale masks must never be applied to a new image");
  System.out.println("PreviewConnectomeTest: "+checks+" checks passed");
 }
}
