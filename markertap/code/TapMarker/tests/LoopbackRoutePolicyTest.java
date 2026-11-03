package jp.tapmarker;
public final class LoopbackRoutePolicyTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] unused){
  check(!LoopbackRoutePolicy.canAttempt(false,false,0),"initial offline cannot authenticate");
  check(!LoopbackRoutePolicy.canAttempt(false,true,0),"paired without proven port cannot invent endpoint");
  check(!LoopbackRoutePolicy.canAttempt(false,false,5555),"unpaired cached port not authorized");
  check(LoopbackRoutePolicy.canAttempt(false,true,43911),"offline trusted loopback endpoint eligible");
  check(LoopbackRoutePolicy.canAttempt(true,false,0),"wifi can start user-guided setup");
  check(LoopbackRoutePolicy.eligiblePort(true,43911)==43911,"remembered port intact");
  check(LoopbackRoutePolicy.eligiblePort(false,43911)==0,"no unauthorized reconnect");
  check(LoopbackRoutePolicy.eligiblePort(true,-1)==0,"reject negative port");
  check(LoopbackRoutePolicy.eligiblePort(true,0)==0,"reject zero");
  check(LoopbackRoutePolicy.eligiblePort(true,65536)==0,"reject out-of-range");
  check(LoopbackRoutePolicy.eligiblePort(true,65535)==65535,"accept bound");
  check(LoopbackRoutePolicy.eligiblePort(true,1)==1,"accept low service port (not automatically scanned)");
  check(!LoopbackRoutePolicy.shouldDialCached(false,5555),"do not dial before pairing");
  check(LoopbackRoutePolicy.shouldDialCached(true,5555),"user-authorized 5555 only");
  check(!LoopbackRoutePolicy.requireWifiDiscovery(false),"no mDNS without wifi");
  check(LoopbackRoutePolicy.requireWifiDiscovery(true),"wifi mDNS is fallback");
  System.out.println("LoopbackRoutePolicyTest: "+checks+" checks passed");
 }
}
