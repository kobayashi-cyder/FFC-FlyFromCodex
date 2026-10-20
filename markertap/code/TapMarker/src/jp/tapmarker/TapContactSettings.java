package jp.tapmarker;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

final class TapContactSettings {
 private final Context context;int mode;boolean spread;float refreshRate=60;
 TapContactSettings(Context context){this.context=context;refresh();}
 void refresh(){
  mode=context.getSharedPreferences("tapInput",0).getInt("mode",TapContactPolicy.AUTO);
  spread=context.getSharedPreferences("tapInput",0).getBoolean("spread",true);
  DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
  Display display=manager==null?null:manager.getDisplay(Display.DEFAULT_DISPLAY);
  refreshRate=display==null?60:display.getRefreshRate();
 }
 String target(){return context instanceof TapService?((TapService)context).currentApp:"";}
 long press(long requested){return press(target(),requested);}
 long press(String target,long requested){return TapContactPolicy.pressMillis(Build.MODEL,mode,refreshRate,target,requested);}
 long release(){return release(target());}
 long release(String target){return TapContactPolicy.releaseMillis(Build.MODEL,mode,refreshRate,target);}
 String describe(){return (mode==TapContactPolicy.AUTO?"自動 / ":"")+(release()>0?"離散入力：押下 "+press(1)+"ms以上・解放 "+release()+"ms以上":"最短入力：1ms押下要求・解放後すぐ次へ")+"\n連打位置："+(spread?"中央・上下左右の5点からランダム":"中央の1点");}
}
