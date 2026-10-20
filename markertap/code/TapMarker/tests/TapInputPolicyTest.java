package jp.tapmarker;
public final class TapInputPolicyTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(String model:new String[]{"F-52A","F52A","f_52a"," F-52A "}){
   check(TapInputPolicy.pressMillis(model,1)==1,"fastest marker presses do not inherit a device-profile floor");
   check(TapInputPolicy.pressMillis(model,30)==30,"explicit short learned taps retain their duration");
   check(TapInputPolicy.pressMillis(model,120)==120,"longer user-recorded presses remain intact");
   for(PerformancePolicy.Profile profile:new PerformancePolicy.Profile[]{PerformancePolicy.ECO,PerformancePolicy.NORMAL,PerformancePolicy.QUICK,PerformancePolicy.F52A}){
    long held=TapInputPolicy.pressMillis(model,1),interval=PerformancePolicy.effectiveTap(3,profile);
    check(held+TapInputPolicy.nextDelay(model,interval,held)==3,"every recognition performance selection retains the requested start-to-start period");
   }
   check(60+TapInputPolicy.nextDelay(model,500,60)==500,"slower saved settings retain their start-to-start period");
  }
  check(TapInputPolicy.pressMillis("Pixel 10",1)==1&&TapInputPolicy.nextDelay("Pixel 10",3,1)==2,"other models retain their timing");
  TapInputPolicy.Retry retry=new TapInputPolicy.Retry();
  check(retry.failed()==250&&retry.failed()==500&&retry.failed()==1000&&retry.failed()==-1,"interrupted input retries three times with bounded backoff");
  retry.completed();check(retry.failures()==0&&retry.failed()==250,"a completed gesture resets the consecutive failure budget");
  PhaseEngine engine=ContinuousTapTest.game();
  check(ContinuousTapPolicy.holdReason(engine,false,false,false,false,true,10,10).contains("認識"),"unrecognized game is diagnosed before input");
  check(ContinuousTapPolicy.holdReason(engine,true,false,false,false,true,10,1510).contains("1.5秒"),"slow image processing remains observable and does not authorize stale taps");
  check(ContinuousTapPolicy.holdReason(engine,true,false,false,false,false,10,10).contains("対象アプリ"),"an external app cannot receive retried input");
  engine.observe(java.util.Collections.singletonMap("ad",new float[]{1}),20);
  check(ContinuousTapPolicy.holdReason(engine,true,false,false,false,true,20,20).contains("分岐"),"the first visible ad still holds continuous input");
  System.out.println("TapInputPolicyTest: "+checks+" checks passed; uncapped duration, cadence, retry budget and recognition holds; Android input not tested");
 }
}
