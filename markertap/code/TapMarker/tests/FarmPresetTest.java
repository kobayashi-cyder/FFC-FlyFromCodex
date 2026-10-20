package jp.tapmarker;
import java.util.*;import java.io.*;
public class FarmPresetTest {
 static int count;static void check(boolean b,String why){if(!b)throw new AssertionError(why);count++;}
 public static void main(String[] args)throws Exception{
 DataInputStream in=new DataInputStream(new FileInputStream(args[0]));Map<String,float[]> refs=UsagiPresetTest.read(in);Map<String,Map<String,float[]>> frames=new HashMap<>();int n=in.readInt();for(int i=0;i<n;i++){String id=in.readUTF();Map<String,float[]> raw=UsagiPresetTest.read(in),samples=new HashMap<>();for(String key:raw.keySet())samples.put(FarmPreset.P+key,raw.get(key));frames.put(id,samples);}in.close();PhaseEngine e=new PhaseEngine();e.strictRuntime=true;check(FarmPreset.install(e,refs),"install");check(!FarmPreset.install(e,refs),"idempotent");check(e.validate().isEmpty(),"valid graph");
 for(PhaseEngine.Phase p:e.phases.values())check(p.markersDuring==p.id.equals(FarmPreset.P+"main"),"markers only harvest");
 for(String[] row:new String[][]{{"main","72","none"},{"main","22","none"},{"main","40","upgrade-wait"},{"main","6614-110","upgrade-wait"},{"main","6614-54","none"},{"main","6614-20","check"},{"main","42","chest"},{"main","66","reward"},{"main","70","bonus"},{"main","68","check"},{"reward","66","check"},{"bonus","70","verify"},{"verify","72","main"},{"check","66","reward"},{"check","70","bonus"},{"check","68","none"}}){
 e.enter(FarmPreset.P+row[0],0);e.observe(frames.get(row[1]),100);check(e.poll()==null,"first frame protection "+Arrays.toString(row));e.observe(frames.get(row[1]),600);PhaseEngine.Watch w=e.poll();if(row[2].equals("none")){check(w==null,"no unexpected branch "+Arrays.toString(row)+e.diagnostic);continue;}check(w!=null,"missing branch "+Arrays.toString(row)+e.diagnostic);check(w.nextPhase.equals(FarmPreset.P+row[2]),"wrong destination "+Arrays.toString(row));if(w.action==PhaseEngine.CENTER){check(e.confirmsAction(w,e.current,e.epoch,frames.get(row[1])),"preflight matches");check(!e.confirmsAction(w,e.current,e.epoch,frames.get("68")),"ad cancels tap");}e.finish(w,700);check(e.current.equals(FarmPreset.P+row[2]),"finish");
 }
 check(FarmPreset.home(e,frames.get("72")),"home recognized");check(!FarmPreset.home(e,frames.get("66")),"reward forbids harvest");check(!FarmPreset.home(e,frames.get("68")),"ad forbids harvest");
 e.enter(FarmPreset.P+"check",0);e.observe(frames.get("68"),5100);check(e.poll().nextPhase.equals(FarmPreset.P+"ad"),"unknown content recovers registered app");
 PhaseEngine.Watch upgrade=e.rule(FarmPreset.P+"upgrade-ready");upgrade.threshold=.004f;check(FarmPreset.upgrade(e)&&upgrade.threshold==.02f,"old profile receives corrected threshold");check(!FarmPreset.upgrade(e),"migration idempotent");upgrade.threshold=.012f;check(!FarmPreset.upgrade(e)&&upgrade.threshold==.012f,"custom threshold preserved");
 System.out.println("PASS "+count+" farm recorded-frame checks. Device not tested.");
 }
}
