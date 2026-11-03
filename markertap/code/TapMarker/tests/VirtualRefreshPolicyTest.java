package jp.tapmarker;
public final class VirtualRefreshPolicyTest {
 private static int n;
 private static void is(String name,VirtualRefreshPolicy.Decision d,VirtualRefreshPolicy.Action expected){
  if(d.action!=expected)throw new AssertionError(name+": expected "+expected+", got "+d.action+": "+d.reason);
  n++;System.out.println("PASS "+name);
 }
 private static VirtualRefreshPolicy.Observation obs(long now,long frame,boolean network,boolean dns,boolean gone){
  return new VirtualRefreshPolicy.Observation(now,frame,true,false,network,dns,gone,false);
 }
 public static void main(String[] args){
  VirtualRefreshPolicy p=new VirtualRefreshPolicy();
  is("initial stale requests frame",p.observe(obs(20_000,10_000,true,false,false)),VirtualRefreshPolicy.Action.REQUEST_FRAME);
  is("cooldown prevents busy loop",p.observe(obs(23_000,10_000,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  is("no destructive refresh before delay",p.observe(obs(29_000,10_000,true,false,false)),VirtualRefreshPolicy.Action.REQUEST_FRAME);
  is("stale eventually rebuilds",p.observe(obs(37_000,10_000,true,false,false)),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  is("rebuild waits for verification",p.observe(obs(38_000,10_000,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  is("two fresh frames regain normal",p.observe(obs(40_000,39_800,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  is("second fresh frame stays normal",p.observe(obs(41_000,40_800,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  if(p.rebuildCount(42_000)!=1)throw new AssertionError("budget lost after new frames");n++;
  is("DNS never relaunches",p.observe(obs(80_000,0,true,true,false)),VirtualRefreshPolicy.Action.WAIT_DNS);
  is("network never rebuilds",p.observe(obs(81_000,0,false,false,true)),VirtualRefreshPolicy.Action.WAIT_NETWORK);
  VirtualRefreshPolicy x=new VirtualRefreshPolicy(),y=new VirtualRefreshPolicy();
  is("first lane display removal",x.observe(obs(10_000,9_500,true,false,true)),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  is("other lane untouched",y.observe(obs(10_000,9_500,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  is("first lane repeat rebuild delayed",x.observe(obs(20_000,9_500,true,false,true)),VirtualRefreshPolicy.Action.NONE);
  is("first lane second recovery",x.observe(obs(40_000,9_500,true,false,true)),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  is("first lane loop quarantined",x.observe(obs(70_000,9_500,true,false,true)),VirtualRefreshPolicy.Action.QUARANTINE);
  is("second lane still operational",y.observe(obs(70_000,69_500,true,false,false)),VirtualRefreshPolicy.Action.NONE);
  VirtualRefreshPolicy manual=new VirtualRefreshPolicy();
  is("manual request screenshot",manual.manualRefresh(10_000,false,true,false),VirtualRefreshPolicy.Action.REQUEST_FRAME);
  is("manual hard refresh",manual.manualRefresh(20_000,true,true,false),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  is("manual refresh respects budget",manual.manualRefresh(30_000,true,true,false),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  is("manual cannot loop forever",manual.manualRefresh(40_000,true,true,false),VirtualRefreshPolicy.Action.QUARANTINE);
  manual.resetAfterExplicitUserStart();
  is("explicit restart clears quarantine",manual.manualRefresh(50_000,true,true,false),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  VirtualRefreshPolicy pm=new VirtualRefreshPolicy();
  is("manual input respected",pm.observe(new VirtualRefreshPolicy.Observation(10_000,0,true,true,true,false,true,false)),VirtualRefreshPolicy.Action.NONE);
  is("autonomy off respected",pm.observe(new VirtualRefreshPolicy.Observation(10_000,0,false,false,true,false,true,false)),VirtualRefreshPolicy.Action.NONE);
  is("rebuild in progress ignored",pm.observe(new VirtualRefreshPolicy.Observation(10_000,0,true,false,true,false,true,true)),VirtualRefreshPolicy.Action.NONE);
  is("failed rebuild still counted",manual.manualRefresh(51_000,true,true,false),VirtualRefreshPolicy.Action.RECREATE_DISPLAY);
  manual.failRebuild();
  is("failed rebuild does not refund budget",manual.manualRefresh(52_000,true,true,false),VirtualRefreshPolicy.Action.QUARANTINE);
  System.out.println("VirtualRefreshPolicy assertions="+n);
 }
}
