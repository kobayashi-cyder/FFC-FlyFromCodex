package jp.tapmarker;
public class WirelessModePolicyTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  WirelessModePolicy p=new WirelessModePolicy();
  check(!p.shouldConnect(true,false,false,0),"default upgrade mode is manual");
  p.mode(true);check(!p.shouldConnect(false,false,false,0),"Wi-Fi absent: never bootstrap");
  check(p.shouldConnect(true,false,false,0),"Wi-Fi returns: bootstrap eligible");
  check(!p.shouldConnect(true,true,false,0),"live detached backend is reused");
  check(!p.shouldConnect(true,false,true,0),"ongoing pairing is not duplicated");
  p.attempted(1000);check(!p.shouldConnect(true,false,false,20999),"rapid network callbacks cannot launch duplicate retries");
  p.failed(2000);check(!p.shouldConnect(true,false,false,21999)&&p.shouldConnect(true,false,false,22000),"first retry has bounded backoff");
  for(int i=0;i<10;i++)p.failed(100000);
  check(p.retryAt==160000,"repeated failures capped at sixty seconds");
  p.pause();check(!p.shouldConnect(true,false,false,200000),"full stop suppresses background restart");
  p.connected();check(p.paused&&!p.shouldConnect(true,false,false,200000),"backend arrival after stop cannot resume maintenance");
  p.resume();check(p.shouldConnect(true,false,false,200000),"explicit start resumes maintenance");
  p.mode(false);check(!p.shouldConnect(true,false,false,Long.MAX_VALUE),"manual mode never auto-connects");
  p.mode(true);check(p.shouldConnect(true,false,false,0),"explicit automatic mode choice takes effect");
  System.out.println("WirelessModePolicyTest: "+checks+" checks passed; Wi-Fi/ADB on-device behavior not tested");
 }
}
