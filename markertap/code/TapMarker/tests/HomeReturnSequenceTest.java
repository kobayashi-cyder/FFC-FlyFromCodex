package jp.tapmarker;
import java.util.*;

public final class HomeReturnSequenceTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Fake implements HomeReturnSequence.Host {
  long time,visibleAt=0;int homes,launches,failures;boolean accepted=true,alive=true;
  final TreeMap<Long,List<Runnable>> queue=new TreeMap<>();final PhaseEngine e=new PhaseEngine();final RecoveryRequest request=new RecoveryRequest();final int id;
  Fake(){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="coin-ad";e.phases.put(p.id,p);e.enter(p.id,0);id=request.begin(e,5);}
  public boolean valid(){return alive&&request.valid(e,5,id);}
  public long now(){return time;}
  public boolean home(){homes++;return accepted;}
  public boolean homeVisible(){return time>=visibleAt;}
  public void launch(){launches++;}
  public void failed(String reason){check(!reason.isEmpty(),"failed HOME explains the pending recovery");failures++;}
  public void later(Runnable task,long delay){queue.computeIfAbsent(time+delay,k->new ArrayList<>()).add(task);}
  void until(long end){while(!queue.isEmpty()&&queue.firstKey()<=end){Map.Entry<Long,List<Runnable>> row=queue.pollFirstEntry();time=row.getKey();for(Runnable r:row.getValue())r.run();}time=end;}
 }
 public static void main(String[] args){
  Fake f=new Fake();HomeReturnSequence.start(f,false);check(f.homes==0&&f.launches==1,"external HOME/browser return directly launches only the registered game");
  f=new Fake();f.visibleAt=600;HomeReturnSequence.start(f,true);f.until(299);check(f.homes==1&&f.launches==0,"HOME completion is separate from request acceptance");f.until(599);check(f.launches==0,"unsettled HOME cannot be overrun by a 300ms handoff");f.until(600);check(f.launches==1&&f.failures==0,"confirmed launcher permits one game launch");f.until(5000);check(f.launches==1,"settled sequence cannot launch twice");
  f=new Fake();HomeReturnSequence.start(f,true);f.until(299);f.alive=false;f.until(5000);check(f.launches==0&&f.failures==0,"all-stop cancels the delayed launch");
  f=new Fake();HomeReturnSequence.start(f,true);f.e.enter("coin-ad",100);f.until(5000);check(f.launches==0,"same-state re-entry invalidates a pre-HOME request");
  f=new Fake();HomeReturnSequence.start(f,true);f.request.cancel();f.until(5000);check(f.launches==0,"a new observed branch cancels the old HOME continuation");
  f=new Fake();f.accepted=false;HomeReturnSequence.start(f,true);f.until(5000);check(f.homes==1&&f.launches==0&&f.failures==1,"rejected HOME returns to observation without an unverified launch");
  f=new Fake();f.visibleAt=5000;HomeReturnSequence.start(f,true);f.until(1499);check(f.failures==0&&f.launches==0,"HOME has a bounded verification window");f.until(1500);check(f.failures==1&&f.launches==0,"missing launcher times out instead of becoming stuck");
  Fake a=new Fake(),b=new Fake();HomeReturnSequence.start(a,true);HomeReturnSequence.start(b,true);a.alive=false;a.until(300);b.until(300);check(a.launches==0&&b.launches==1,"another run has independent pending work");
  System.out.println("HomeReturnSequenceTest: "+checks+" checks passed; production coordinator with virtual clock, no physical Android HOME/input");
 }
}
