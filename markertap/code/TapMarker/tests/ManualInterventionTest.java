package jp.tapmarker;

public final class ManualInterventionTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  ManualIntervention a=new ManualIntervention(),b=new ManualIntervention();check(!a.holding(1000)&&!b.holding(1000),"both displays initially run");int first=a.begin(1000);check(a.holding(3000)&&!b.holding(3000),"finger hold pauses only its own display");check(a.release(3100,false)==first&&a.current(first),"release retains only this input request");a.completed(first,3200);check(a.holding(3900)&&!a.holding(4000),"fresh-image settling interval precedes automatic resume");
  int next=a.begin(4100);a.completed(first,4200);check(a.holding(5000)&&a.current(next)&&!a.current(first),"late completion cannot resume a newer manual touch");a.release(5100,true);check(a.holding(5800)&&!a.holding(5900),"cancel resumes after a bounded recheck");a.begin(6000);a.reset();check(!a.holding(6001)&&!a.current(next),"stopping the run invalidates pending manual callbacks");check(!b.holding(6001),"other display never rearmed or paused");
  System.out.println("ManualInterventionTest: "+checks+" checks; per-display hold, release, settling, stale completions and cancellation; preview forwarding requires device validation");
 }
}
