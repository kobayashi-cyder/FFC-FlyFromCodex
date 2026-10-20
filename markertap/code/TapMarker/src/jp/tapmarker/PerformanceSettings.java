package jp.tapmarker;
import android.app.ActivityManager;import android.content.Context;import android.os.SystemClock;import android.os.Build;

final class PerformanceSettings {
 final Context context;final int cores;final boolean lowRam;final int memoryClass;final long totalMemory,heapBytes;
 volatile PerformancePolicy.Profile selected;volatile long pressureUntil;
 PerformanceSettings(Context context){
  this.context=context;cores=Math.max(1,Runtime.getRuntime().availableProcessors());heapBytes=Runtime.getRuntime().maxMemory();
  ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
  ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(info);
  lowRam=am!=null&&am.isLowRamDevice();memoryClass=am==null?0:am.getMemoryClass();totalMemory=info.totalMem;refresh();
 }
 void refresh(){selected=PerformancePolicy.choose(context.getSharedPreferences("performance",0).getInt("mode",0),lowRam,memoryClass,totalMemory,cores,Build.MODEL);}
 PerformancePolicy.Profile profile(){return SystemClock.uptimeMillis()<pressureUntil?(selected==PerformancePolicy.F52A?selected:PerformancePolicy.ECO):selected;}
 void memoryPressure(){pressureUntil=SystemClock.uptimeMillis()+60000;}
 int[] geometry(int w,int h,int density,int windows){return VirtualResolutionPolicy.geometry(profile(),w,h,density,windows,heapBytes,VirtualResolutionLab.selected(context));}
 String describe(){int mode=context.getSharedPreferences("performance",0).getInt("mode",0);return (mode==0?"自動 / ":"")+profile().name;}
}
