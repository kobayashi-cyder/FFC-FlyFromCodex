package jp.tapmarker;
public final class PcRunLeaseTest {
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  String a="0123456789abcdef0123456789abcdef",b="1123456789abcdef0123456789abcdef";
  PcRunLease lease=new PcRunLease();
  check(PcRunLease.validToken(a)&&!PcRunLease.validToken("bad"),"strict owner token");
  check(PcRunLease.validPackage("com.demo.one")&&!PcRunLease.validPackage("com.demo;id")&&!PcRunLease.validPackage("jp.tapmarker.lite"),"validate targets");
  lease.prepare(a,1000);check(!lease.owns(a,1),"preparation owns no windows");
  lease.started(3);check(lease.owns(a,3)&&!lease.owns(b,3),"only owner can stop");
  check(!lease.owns(a,4),"phone replacement generation cannot be stopped by PC");
  check(!lease.timedOut(21000)&&lease.timedOut(21001),"heartbeat expiry boundary");
  lease.beat(a,22000);check(!lease.timedOut(22001),"status renews heartbeat");
  boolean denied=false;try{lease.prepare(b,23000);}catch(IllegalStateException expected){denied=true;}check(denied,"second PC cannot take ownership");
  denied=false;try{lease.beat(b,24000);}catch(SecurityException expected){denied=true;}check(denied,"foreign heartbeat refused");
  lease.expired=true;check(!lease.timedOut(999999),"expiry runs once");
  lease.clear();check(!lease.matches(a)&&!lease.timedOut(999999),"closed owner is released");
  lease.prepare(b,100);lease.started(9);check(lease.owns(b,9)&&!lease.expired,"new run after cleanup");
  System.out.println("PcRunLeaseTest: "+checks+" checks passed");
 }
}
