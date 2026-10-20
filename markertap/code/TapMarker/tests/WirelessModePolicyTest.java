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
  p.mode(false);p.requestSessionRecovery();
  check(p.shouldConnect(true,false,false,0),"an explicitly started manual session can recover after backend death");
  check(!p.shouldConnect(false,false,false,0),"a lost offline backend waits for Wi-Fi without starting ADB");
  p.cancelSessionRecovery();check(!p.shouldConnect(true,false,false,0),"cancelled session cannot restart manual mode");
  p.requestSessionRecovery();p.pause();p.resume();check(!p.shouldConnect(true,false,false,0),"full stop discards the recovery request");
  p.requestSessionRecovery();p.connected();check(!p.shouldConnect(true,false,false,0),"successful manual recovery does not turn on idle auto-connect");
  check(!p.cancelForWifiLoss(false,true,true,true),"Wi-Fi loss must not shut down an already connected detached backend");
  check(p.cancelForWifiLoss(false,true,true,false),"offline discovery without a backend may be cancelled");
  check(!p.cancelForWifiLoss(false,true,false,false),"explicit pairing is not cancelled by quiet maintenance");
  check(!p.cancelForWifiLoss(true,true,true,false),"connected Wi-Fi keeps bootstrap active");
  System.out.println("WirelessModePolicyTest: "+checks+" checks passed; Wi-Fi/ADB on-device behavior not tested");
 }
}
