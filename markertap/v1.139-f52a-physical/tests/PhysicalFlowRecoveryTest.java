package jp.tapmarker;

public final class PhysicalFlowRecoveryTest {
 static int n;
 static void ok(boolean v){n++;if(!v)throw new AssertionError("case "+n);}
 static PhysicalFlowRecovery.Decision observe(PhysicalFlowRecovery r,String state,
    boolean foreground,boolean confirmed,boolean pending,boolean returning,long sample,long at) {
  return r.observe(state,foreground,confirmed,pending,returning,sample,at);
 }
 public static void main(String[] args){
  PhysicalFlowRecovery r=new PhysicalFlowRecovery();
  final String MAIN=PhysicalFlowRecovery.MAIN,VERIFY=PhysicalFlowRecovery.VERIFY;
  final PhysicalFlowRecovery.Decision WAIT=PhysicalFlowRecovery.Decision.KEEP_OBSERVING;
  final PhysicalFlowRecovery.Decision CLASSIFY=PhysicalFlowRecovery.Decision.RECLASSIFY;
  ok(observe(r,MAIN,true,false,false,false,-1,100)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,1000,1200)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,1000,1800)==WAIT); // repeat same screenshot
  ok(observe(r,MAIN,true,false,false,false,2900,3000)==CLASSIFY); // new fresh observation
  ok(r.attempts()==1);
  ok(observe(r,VERIFY,true,false,false,false,3500,3600)==WAIT);
  ok(observe(r,VERIFY,true,false,false,false,5100,5200)==CLASSIFY);
  ok(r.attempts()==2);
  ok(observe(r,MAIN,true,false,true,false,5300,5400)==WAIT); // action pending
  ok(observe(r,MAIN,true,false,false,true,5500,5600)==WAIT); // returning
  ok(observe(r,MAIN,false,false,false,false,5800,5900)==WAIT); // not foreground
  ok(observe(r,MAIN,true,false,false,false,5900,6000)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,7400,7500)==WAIT); // < persistence
  ok(observe(r,MAIN,true,false,false,false,7700,7800)==CLASSIFY);
  ok(r.attempts()==3);
  ok(observe(r,MAIN,true,false,false,false,9000,9150)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,10700,10800)==PhysicalFlowRecovery.Decision.STOP_WITH_DIAGNOSTIC);
  ok(observe(r,MAIN,true,true,false,false,11000,11100)==WAIT); // confirmed clears budget
  ok(r.attempts()==0);
  ok(observe(r,MAIN,true,false,false,false,11000,11100)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,10500,12100)==WAIT); // stale image never progresses
  ok(observe(r,"farm-default-main",true,false,false,false,12100,12200)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,12200,12300)==WAIT);
  ok(observe(r,MAIN,true,false,false,false,14200,14300)==CLASSIFY);
  // Incident archive reports package com.kurashiru.nouen.usagi and
  // state farm-default-verify; both must enter the same guarded recovery.
  ok(PhysicalFlowRecovery.supportsGame("com.kurashiru.fue.usagi"));
  ok(PhysicalFlowRecovery.supportsGame("com.kurashiru.nouen.usagi"));
  ok(!PhysicalFlowRecovery.supportsGame("com.example.unrelated"));
  r.reset();
  ok(observe(r,"farm-default-verify",true,false,false,false,20000,20100)==WAIT);
  ok(observe(r,"farm-default-verify",true,false,false,false,22000,22100)==CLASSIFY);
  System.out.println("PhysicalFlowRecoveryTest: "+n+" assertions passed");
 }
}
