package jp.tapmarker;

import android.accessibilityservice.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit demonstration mode. Transparent interception is used only while learning. */
final class LearningController {
 final AutoPilot pilot;final TapService s;
 boolean active,inputBusy,capturing,finishing,compiling,repeat=true;int generation;
 String pkg="",profileKey="";VirtualWindows.Window window;Rect bounds=new Rect();View overlay;
 LinearLayout controls;Button toggle,markers;
 ObservationLearner recorder=new ObservationLearner();ObservationLearner.Frame latest,peer;
 ObservationLearner.Step pending;final ArrayList<Float> trace=new ArrayList<>();long touchAt,inputAt,lastCapture=-1;
 boolean released,beforeReady;Rect inputBounds;
 LearningController(AutoPilot p){pilot=p;s=p.s;}
 void controls(LinearLayout panel){controls=UiKit.column(s);panel.addView(controls);LinearLayout row=UiKit.row(s,controls);toggle=s.button("学習開始",()->toggle());UiKit.cell(s,row,toggle);markers=s.button("マーカー実演",()->demonstrateMarkers());UiKit.cell(s,row,markers);UiKit.full(s,controls,s.button("学習前に戻す",()->restoreBackup()));}
 boolean valid(int t){return active&&t==generation;}
 void toggle(){if(active){finish();return;}choose();}
 void choose(){
  s.stop("学習の対象選択");ArrayList<String> packages=new ArrayList<>();ArrayList<VirtualWindows.Window> windows=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
  String ordinary=pilot.packageNow();if(s.validApp(ordinary)){packages.add(ordinary);windows.add(null);labels.add(s.appLabel(ordinary)+"（通常画面）");}
  for(VirtualWindows.Window w:pilot.virtualWindows.windows)if(w.launched&&!w.closed&&w.frameAt>0){packages.add(w.pkg);windows.add(w);labels.add(s.appLabel(w.pkg)+"（仮想 #"+w.id+"）");}
  if(packages.isEmpty()){pilot.note("学習するアプリを通常画面または仮想画面で開いてください");return;}
  LinearLayout body=new LinearLayout(pilot.phaseUi.context());body.setOrientation(1);body.setPadding(24,12,24,12);TextView help=new UiStrings.DisplayText(pilot.phaseUi.context());help.setText("動画の送信は不要です。学習開始後、対象画面を普段どおり操作し、一周したら「学習終了・作成」を押してください。タップと曲線、マーカー実演を記録します。\n同じ表示は同じ状態になります。同じ表示で異なる操作を記録した場合は、条件を区別できるまで保留します。");body.addView(help);
  Spinner target=new Spinner(pilot.phaseUi.context());target.setAdapter(new UiStrings.StringAdapter(pilot.phaseUi.context(),android.R.layout.simple_spinner_dropdown_item,labels));body.addView(target);
  CheckBox loop=new UiStrings.DisplayCheck(pilot.phaseUi.context());loop.setText("記録した状態への復帰が確認できたら繰り返す");loop.setChecked(true);body.addView(loop);
  pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("アプリの操作を学習").setView(body).setPositiveButton("学習開始",(d,n)->{int index=target.getSelectedItemPosition();repeat=loop.isChecked();s.h.postDelayed(()->start(packages.get(index),windows.get(index)),100);}).setNegativeButton("取消",null).create());
 }
 void start(String app,VirtualWindows.Window virtual){
  s.stop("操作学習の開始");s.removeCapture();if(Build.VERSION.SDK_INT<30){pilot.note("画面学習はAndroid11以降が必要です");return;}
  if(virtual==null){if(!app.equals(pilot.packageNow())||!s.ensureProfile())return;pilot.editingVirtualPackage="";}else{if(!pilot.virtualWindows.check(virtual)||!pilot.virtualWindows.arm())return;pilot.editingVirtualPackage=app;if(virtual.minimized)pilot.virtualWindows.minimize(false);pilot.phases.load(app);}
  pkg=app;profileKey=GameProfiles.key(s,app);window=virtual;recorder=new ObservationLearner();latest=null;peer=null;pending=null;inputBusy=false;capturing=false;finishing=false;active=true;int t=++generation;
  for(TapService.Mark m:s.marks)m.view.setVisibility(View.INVISIBLE);s.touchable(false);
  if(window==null){overlay=new View(s);overlay.setBackgroundColor(Color.TRANSPARENT);overlay.setOnTouchListener((v,event)->touch(event.getActionMasked(),event.getPointerCount(),event.getRawX(),event.getRawY()));s.wm.addView(overlay,s.params(-1,-1));WindowManager.LayoutParams params=(WindowManager.LayoutParams)s.panel.getLayoutParams();s.wm.removeView(s.panel);s.wm.addView(s.panel,params);}
  toggle.setText("学習終了・作成");s.compactPanel(true);pilot.note("学習準備："+s.appLabel(pkg)+" / 最初の表示を取得中");poll(t);
 }
 void cancel(){if(!active&&overlay==null)return;active=false;generation++;inputBusy=false;capturing=false;finishing=false;compiling=false;pending=null;recorder.steps.clear();latest=null;peer=null;removeOverlay();if(toggle!=null)toggle.setText("学習開始");for(TapService.Mark m:s.marks)m.view.setVisibility(window==null?View.VISIBLE:View.INVISIBLE);s.touchable(true);}
 void removeOverlay(){if(overlay!=null){try{s.wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}pilot.panelTouchable(true);}
 void intercept(boolean yes){if(overlay==null)return;WindowManager.LayoutParams p=(WindowManager.LayoutParams)overlay.getLayoutParams();if(yes)p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;s.wm.updateViewLayout(overlay,p);}
 interface Captured {void ready(ObservationLearner.Frame frame);}
 void snapshot(int t,Captured done,Runnable failed){
  if(!valid(t))return;if(capturing){s.h.postDelayed(()->snapshot(t,done,failed),50);return;}
  long wait=TransitionTiming.remaining(lastCapture,SystemClock.uptimeMillis());if(window==null&&wait>0){s.h.postDelayed(()->snapshot(t,done,failed),wait);return;}
  capturing=true;lastCapture=SystemClock.uptimeMillis();
  if(window!=null){VirtualWindows.Window w=window;pilot.virtualWindows.capture(w,()->valid(t)&&window==w,image->decode(t,image,new Rect(0,0,w.width,w.height),done,failed),()->{capturing=false;if(valid(t))failed.run();});return;}
  if(!pkg.equals(pilot.packageNow())){capturing=false;failed.run();return;}
  Rect rect=pilot.splitLauncher.bounds(pkg);if(rect==null||rect.isEmpty()){capturing=false;failed.run();return;}final Rect capturedBounds=new Rect(rect);
  AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null){capturing=false;failed.run();return;}final int id=root.getWindowId();root.recycle();
  AccessibilityService.TakeScreenshotCallback callback=new AccessibilityService.TakeScreenshotCallback(){public void onFailure(int code){capturing=false;if(valid(t))failed.run();}public void onSuccess(AccessibilityService.ScreenshotResult result){Bitmap hardware=null,image=null;try{hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hardware!=null)image=hardware.copy(Bitmap.Config.ARGB_8888,false);}finally{if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();}if(image==null){capturing=false;if(valid(t))failed.run();return;}decode(t,image,capturedBounds,done,failed);}};
  try{if(Build.VERSION.SDK_INT>=34)s.takeScreenshotOfWindow(id,s.getMainExecutor(),callback);else s.takeScreenshot(Display.DEFAULT_DISPLAY,s.getMainExecutor(),callback);}catch(Exception error){callback.onFailure(-1);}
 }
 void decode(int t,Bitmap image,Rect rect,Captured done,Runnable failed){
  if(!valid(t)){image.recycle();return;}
  pilot.worker.execute(()->{ObservationLearner.Frame frame=null;try{int left=0,top=0,w=image.getWidth(),h=image.getHeight();if(window==null&&!(w==rect.width()&&h==rect.height())){if(w<rect.right||h<rect.bottom)throw new IllegalStateException("ウインドウ範囲が変わりました");left=rect.left;top=rect.top;w=rect.width();h=rect.height();}final int ox=left,oy=top;RegionSampler.Pixels pixels=BitmapPixels.of(image);frame=ObservationLearner.Frame.capture(w,h,(x,y)->pixels.get(x+ox,y+oy));}catch(Exception ignored){}finally{image.recycle();}final ObservationLearner.Frame result=frame;s.h.post(()->{if(!valid(t))return;capturing=false;if(result==null){failed.run();return;}if(inputBusy&&inputBounds!=null&&!inputBounds.equals(rect)){abortInput("ウインドウの大きさが変わったため、操作を記録し直してください");return;}bounds=new Rect(rect);if(window==null&&Build.VERSION.SDK_INT<34&&s.panel!=null){WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();result.excluded=new float[]{(p.x-rect.left)/(float)rect.width(),(p.y-rect.top)/(float)rect.height(),(p.x+s.panel.getWidth()-rect.left)/(float)rect.width(),(p.y+s.panel.getHeight()-rect.top)/(float)rect.height()};}done.ready(result);});});
 }
 void poll(int t){if(!valid(t)||finishing)return;if(inputBusy){s.h.postDelayed(()->poll(t),200);return;}snapshot(t,frame->{peer=latest;latest=frame;pilot.note("学習中："+s.appLabel(pkg)+" / "+recorder.steps.size()+"操作 / 画面を操作してください");s.h.postDelayed(()->poll(t),250);},()->{pilot.note("学習中：対象画面の表示を待機 / 対象以外は記録しません");s.h.postDelayed(()->poll(t),600);});}
 int previewTouchRevision;
 boolean previewTouch(VirtualWindows.Window w,MotionEvent event){if(!active||window!=w)return false;if(event.getActionMasked()==MotionEvent.ACTION_DOWN)previewTouchRevision=w.previewRevision;else if(previewTouchRevision!=w.previewRevision){touch(MotionEvent.ACTION_CANCEL,1,0,0);return true;}float[] local=previewPoint(w,event.getX(),event.getY());if(local==null){touch(MotionEvent.ACTION_CANCEL,1,0,0);return true;}touch(event.getActionMasked(),event.getPointerCount(),local[0],local[1]);return true;}
 static float[] previewPoint(VirtualWindows.Window w,float x,float y){return VirtualWindows.previewPoint(w,x,y);}

 boolean touch(int action,int fingers,float x,float y){
  if(!active)return false;if(fingers!=1||action==MotionEvent.ACTION_CANCEL){trace.clear();if(pending!=null&&!released)abortInput("1本指のタップ・曲線を実演してください");return true;}
  if(action==MotionEvent.ACTION_DOWN){if(inputBusy||finishing||latest==null){pilot.note("直前の記録を確認中 / 完了後に操作してください");return true;}if(!bounds.contains((int)x,(int)y))return true;
   inputBusy=true;released=false;beforeReady=false;inputBounds=new Rect(bounds);pending=new ObservationLearner.Step();pending.peer=latest;trace.clear();touchAt=SystemClock.uptimeMillis();append(x,y);int t=generation;snapshot(t,frame->{if(pending==null)return;pending.before=frame;beforeReady=true;sendWhenReady(t);},()->abortInput("操作前の表示が取得できないため、実演をやり直してください"));return true;
  }
  if(pending==null||released)return true;
  if(action==MotionEvent.ACTION_MOVE){append(x,y);return true;}
  if(action==MotionEvent.ACTION_UP){append(x,y);released=true;pending.path=new float[trace.size()];for(int i=0;i<trace.size();i++)pending.path[i]=trace.get(i);pending.duration=Math.max(1,Math.min(10000,SystemClock.uptimeMillis()-touchAt));float distance=0;for(int i=2;i<pending.path.length;i+=2)distance+=Math.hypot((pending.path[i]-pending.path[i-2])*bounds.width(),(pending.path[i+1]-pending.path[i-1])*bounds.height());pending.action=distance<24?PhaseEngine.CENTER:PhaseEngine.CURVE;if(pending.action==PhaseEngine.CENTER)pending.duration=Math.max(40,Math.min(700,pending.duration));sendWhenReady(generation);}
  return true;
 }
 void append(float x,float y){float nx=(x-inputBounds.left)/inputBounds.width(),ny=(y-inputBounds.top)/inputBounds.height();CurveMath.append(trace,Math.max(0,Math.min(.9999f,nx)),Math.max(0,Math.min(.9999f,ny)));}
 void sendWhenReady(int t){if(!valid(t)||pending==null||!released||!beforeReady)return;beforeReady=false;ObservationLearner.Step step=pending;float[] normalized=step.action==PhaseEngine.CENTER?new float[]{step.path[0],step.path[1]}:step.path;float[] path=physical(normalized,inputBounds);relay(t,path,step.duration,()->{inputAt=SystemClock.uptimeMillis();settle(t,null,0);},()->abortInput("入力が中断されたため記録しません / 再度実演してください"));}
 static float[] physical(float[] normalized,Rect rect){float[] p=normalized.clone();for(int i=0;i<p.length;i+=2){p[i]=rect.left+p[i]*rect.width();p[i+1]=rect.top+p[i+1]*rect.height();}return p;}
 void relay(int t,float[] path,long duration,Runnable done,Runnable failed){
  if(!valid(t))return;
  if(window!=null){pilot.virtualWindows.gesture(window,path,duration,()->valid(t),done,failed);return;}
  Rect current=pilot.splitLauncher.bounds(pkg);if(!pkg.equals(pilot.packageNow())||current==null||!current.equals(inputBounds)||s.guardDialog()){failed.run();return;}
  Path stroke=new Path();stroke.moveTo(path[0],path[1]);for(int i=2;i<path.length;i+=2)stroke.lineTo(path[i],path[i+1]);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(stroke,0,duration)).build();boolean covered=pilot.panelCovers(path[0],path[1]);if(covered&&duration>200){failed.run();pilot.note("曲線の開始位置からパネルを離して実演してください");return;}s.busy=true;intercept(false);if(covered)pilot.panelTouchable(false);
  s.h.postDelayed(()->{if(!valid(t)){s.busy=false;pilot.panelTouchable(true);return;}if(!pkg.equals(pilot.packageNow())||s.guardDialog()){s.busy=false;intercept(true);pilot.panelTouchable(true);failed.run();return;}boolean ok=s.dispatchTrackedGesture(gesture,new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription g){s.busy=false;intercept(true);pilot.panelTouchable(true);if(valid(t)){s.count++;done.run();}}public void onCancelled(GestureDescription g){s.busy=false;intercept(true);pilot.panelTouchable(true);if(valid(t))failed.run();}},s.h);if(!ok){s.busy=false;intercept(true);pilot.panelTouchable(true);failed.run();}},32);
 }
 static boolean settled(ObservationLearner.Frame a,ObservationLearner.Frame b){int stable=0;for(int y=0;y<10;y++)for(int x=0;x<5;x++){float[] r=ObservationLearner.around(.1f+x*.2f,.07f+y*.09f);if(FlyCircuit.distance(a.sample(r),b.sample(r))<.006f)stable++;}return stable>=35;}
 void settle(int t,ObservationLearner.Frame previous,int attempts){if(!valid(t)||pending==null)return;s.h.postDelayed(()->snapshot(t,frame->{if(pending==null)return;if(previous!=null&&(settled(previous,frame)||attempts>=10)){pending.after=frame;pending.afterPeer=previous;try{recorder.add(pending);}catch(Exception e){abortInput(e.getMessage());return;}peer=previous;latest=frame;pending=null;inputBusy=false;pilot.note("学習中："+recorder.steps.size()+"操作を記録 / 次の操作または学習終了");if(finishing)finish();}else settle(t,frame,attempts+1);},()->{if(attempts<15)settle(t,previous,attempts+1);else abortInput("操作後の対象画面を取得できません / この操作は記録しません");}),window==null?260:150);}
 void abortInput(String reason){pending=null;inputBusy=false;trace.clear();beforeReady=false;released=false;intercept(true);pilot.panelTouchable(true);pilot.note(reason);if(finishing){finishing=false;finish();}}
 void demonstrateMarkers(){
  if(!active){pilot.note("先に学習開始を押し、対象アプリを選んでください");return;}if(inputBusy||latest==null){pilot.note("直前の記録が完了してからマーカー実演を押してください");return;}
  ArrayList<String> ids=new ArrayList<>();ArrayList<Float> points=new ArrayList<>();try{
   int[] size=pilot.displaySize();SharedPreferences prefs=s.getSharedPreferences("markers",0);
   JSONArray records=new JSONArray(prefs.getString("profile:"+profileKey,"[]"));float width=prefs.getInt("profileWidth:"+profileKey,size[0]),height=prefs.getInt("profileHeight:"+profileKey,size[1]);
   for(int i=0;i<records.length();i++){JSONObject record=records.getJSONObject(i);float x=(float)record.getDouble("x"),y=(float)record.getDouble("y");float nx=window==null?(x-bounds.left)/bounds.width():x/width,ny=window==null?(y-bounds.top)/bounds.height():y/height;if(nx<0||nx>=1||ny<0||ny>=1)continue;ids.add(record.getString("id"));points.add(nx);points.add(ny);}
  }catch(Exception e){pilot.note("マーカーを読み込めませんでした");return;}
  if(ids.isEmpty()){pilot.note("対象アプリにマーカーを配置してから学習してください");return;}
  pending=new ObservationLearner.Step();pending.action=PhaseEngine.MARKERS;pending.duration=300;pending.markerIds=ids.toArray(new String[0]);pending.markerPoints=new float[points.size()];for(int i=0;i<points.size();i++)pending.markerPoints[i]=points.get(i);pending.peer=latest;inputBounds=new Rect(bounds);inputBusy=true;int t=generation;
  snapshot(t,frame->{if(pending==null)return;pending.before=frame;markerStep(t,0,SystemClock.uptimeMillis()+300);},()->abortInput("マーカー実演前の表示を取得できませんでした"));
 }
 void markerStep(int t,int index,long until){if(!valid(t)||pending==null)return;if(SystemClock.uptimeMillis()>=until){inputAt=SystemClock.uptimeMillis();settle(t,null,0);return;}float[] points=pending.markerPoints;int i=index%pending.markerIds.length*2;float[] path=physical(new float[]{points[i],points[i+1]},inputBounds);relay(t,path,1,()->s.h.postDelayed(()->markerStep(t,index+1,until),Math.max(0,s.interval-1)),()->abortInput("マーカー実演が中断されたため記録しません"));}
 void finish(){
  if(!active||compiling)return;if(inputBusy||capturing){finishing=true;pilot.note("最後の操作前後を確認してからオートマトンを作成します");if(!inputBusy)s.h.postDelayed(()->finish(),100);return;}
  if(recorder.steps.isEmpty()){finishing=false;pilot.note("まだ操作がありません / 画面を操作してから学習終了してください");poll(generation);return;}
  finishing=true;compiling=true;int t=generation;pilot.note("表示の同一性を比較し、状態・領域・矢印を作成中");pilot.worker.execute(()->{ObservationLearner.Result compiled=null;String error="";try{compiled=recorder.compile(repeat);}catch(Exception e){error=e.getMessage();}final ObservationLearner.Result result=compiled;final String issue=error;s.h.post(()->{if(!valid(t))return;compiling=false;if(result==null){finishing=false;pilot.note("作成できません："+issue);poll(t);return;}
   SharedPreferences prefs=s.getSharedPreferences("appPhases",0);String old=prefs.getString(profileKey,"{}");if(!prefs.edit().putString("learningBackup:"+profileKey,old).commit()){finishing=false;pilot.note("以前の設定を保存できないため学習結果を適用していません");poll(t);return;}
   AppPhases learned=new AppPhases(s);learned.pkg=pkg;learned.storageKey=profileKey;learned.engine=result.engine;learned.enabled=true;learned.customized=true;learned.prompt="端末内の実演から自動作成 / 同じ表示は同じ状態 / 不一致・曖昧な条件は保留";try{learned.save();}catch(Exception e){finishing=false;pilot.note("学習結果の保存失敗："+e.getMessage());poll(t);return;}
   cancel();pilot.phases=learned;s.compactPanel(false);pilot.note(result.states+"表示状態・"+result.steps+"操作のオートマトンを作成");
   pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("オートマトンを作成しました").setMessage(s.appLabel(pkg)+"\n"+result.states+"表示状態 / 操作後確認"+result.confirmationStates+"状態 / "+result.steps+"実演 / "+result.regions+"監視領域\n"+result.explanation+"\n実行中は現在の状態に必要な領域だけを判定します。").setPositiveButton("自動開始",(d,n)->{if(window!=null)pilot.splitPilot.startVirtual(new String[]{pkg});else pilot.startSingle();}).setNeutralButton("かんたん編集",(d,n)->s.h.postDelayed(()->pilot.phaseUi.easy.show(),100)).setNegativeButton("閉じる",null).create());
  });});
 }
 void restoreBackup(){if(active){pilot.note("学習終了後に戻してください。全停止で今回の学習を取り消せます");return;}if(!pilot.phaseUi.ready())return;String app=pilot.phases.pkg,storage=pilot.phases.storage();SharedPreferences prefs=s.getSharedPreferences("appPhases",0);String backup=prefs.getString("learningBackup:"+storage,null);if(backup==null){pilot.note("このアプリの学習前の設定はありません");return;}String current=prefs.getString(storage,"{}");if(!prefs.edit().putString(storage,backup).putString("learningBackup:"+storage,current).commit()){pilot.note("設定を復元できませんでした");return;}pilot.phases.load(app);pilot.note("学習前の設定へ戻しました / 再度押すと学習結果に戻せます");}
}
