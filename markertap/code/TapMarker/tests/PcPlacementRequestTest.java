package jp.tapmarker;
public final class PcPlacementRequestTest {
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  String a="0123456789abcdef0123456789abcdef",b="1123456789abcdef0123456789abcdef",owner="2123456789abcdef0123456789abcdef";
  String[] apps={"com.demo.one","com.demo.two"};
  PcPlacementRequest request=new PcPlacementRequest();request.request(a,apps,1000);
  apps[0]="com.changed.app";check(request.packages[0].equals("com.demo.one"),"phone targets are copied");
  check(request.take(b,owner,1001)==null&&request.state.equals("waiting"),"stale request cannot consume current selection");
  String[] taken=request.take(a,owner,1002);check(taken.length==2&&request.state.equals("claimed"),"PC atomically takes phone request");
  taken[0]="com.changed.app";check(request.packages[0].equals("com.demo.one"),"PC target list is independent");
  check(request.take(a,b,1003)==null,"second poll or second PC cannot start the request twice");
  check(request.matches(a,owner,new String[]{"com.demo.one","com.demo.two"},1004),"claimed owner and exact targets accepted");
  check(!request.matches(a,b,request.packages,1004)&&!request.matches(a,owner,new String[]{"com.demo.two"},1004),"wrong owner and changed target rejected");
  check(request.cancel("user stopped")&&!request.matches(a,owner,request.packages,1005),"stop invalidates an in-flight start");
  check(!request.finish(a,owner,"late result"),"late failure cannot overwrite cancellation");
  request.request(b,new String[]{"com.demo.two"},2000);
  check(!request.finish(a,owner,"old failure")&&request.state.equals("waiting"),"old response cannot overwrite replacement request");
  check(!request.expire(121999)&&request.expire(122000)&&request.state.equals("expired"),"old waiting request cannot start much later");
  check(request.take(b,owner,122001)==null,"expired request is not claimable");
  request.request(a,new String[]{"com.demo.one"},300000);request.take(a,owner,300001);
  check(request.finish(a,owner,"")&&request.state.equals("accepted"),"accepted means delivered, not game input proven");
  check(!request.expire(999999)&&!request.cancel("stop"),"delivery request expiry cannot change an accepted run");
  request.request(b,new String[]{"com.demo.two"},400000);request.take(b,owner,400001);
  check(request.finish(b,owner,"not configured")&&request.state.equals("failed")&&request.error.equals("not configured"),"failed preflight is recorded once without retrying");
  boolean denied=false;try{request.request(a,new String[]{"com.demo.one","com.demo.one"},500000);}catch(IllegalArgumentException expected){denied=true;}
  check(denied,"duplicate targets rejected before changing request");
  check(request.state.equals("failed")&&request.id.equals(b),"invalid replacement preserves previous request");
  System.out.println("PcPlacementRequestTest: "+checks+" checks passed");
 }
}
