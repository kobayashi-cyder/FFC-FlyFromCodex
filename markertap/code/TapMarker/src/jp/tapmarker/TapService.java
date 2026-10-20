package jp.tapmarker;
import android.accessibilityservice.*;import android.graphics.*;import android.os.*;import android.content.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;import java.util.*;
public class TapService extends AccessibilityService {
 android.content.res.Configuration uiConfiguration;
 PerformanceSettings performance;TapContactSettings contacts;ProgressErrors progressErrors;long inputSuccessAt,inputFailureAt;
 final GestureDispatchGuard gestures=new GestureDispatchGuard(new GestureDispatchGuard.Scheduler(){public void later(Runnable task,long delay){h.postDelayed(task,delay);}public void remove(Runnable task){h.removeCallbacks(task);}});
 final InputPriority inputPriority=new InputPriority();final MarkerTapLogic markerLogic=new MarkerTapLogic(this);Tapper markerTapper;
 Tapper tapper(){if(markerTapper==null)markerTapper=new Tapper(SystemClock::uptimeMillis,(task,delay)->h.postDelayed(task,delay),new AndroidTapDriver(this),new Tapper.Listener(){
  public void state(String state){holdInput(state);}
  public void result(Tapper.Command command,int result){if(result==Tapper.COMPLETED){count++;refresh();if(!command.repeat&&pilot!=null)pilot.note("タップ確認：送信完了 / ゲームの反応を確認してください");}}
 });return markerTapper;}
 long inputTargetVersion;String inputHold="未開始",lastInputError="";long inputRequests,inputCompleted,inputCancelled,inputRejected,inputTimedOut;
 TapContactSettings contacts(){if(contacts==null)contacts=new TapContactSettings(this);return contacts;}
 long tapPressMillis(long requested){return contacts().press(requested);}
 long tapPressMillis(String target,long requested){return contacts().press(target,requested);}
 long tapReleaseMillis(){return contacts().release();}
 long tapReleaseMillis(String target){return contacts().release(target);}
 long minimumTapPeriod(){return Math.max(effectiveInterval(),tapPressMillis(1)+tapReleaseMillis());}
 void afterTapRelease(Runnable done){afterTapRelease(currentApp,done);}
 void afterTapRelease(String target,Runnable done){long release=tapReleaseMillis(target);if(release>0)h.postDelayed(done,release);else done.run();}
 long tapNextDelay(long held){return TapInputPolicy.nextDelay(Build.MODEL,effectiveInterval(),held+tapReleaseMillis());}
 long tapNextDelay(String target,long held){return TapInputPolicy.nextDelay(Build.MODEL,effectiveInterval(),held+tapReleaseMillis(target));}
 float[] mainTapPoints(float[] centers,int[] bounds,int displayId){
  float density=getResources().getDisplayMetrics().density;
  if(displayId!=Display.DEFAULT_DISPLAY&&pilot!=null)for(VirtualWindows.Window window:pilot.virtualWindows.windows)if(window.id==displayId){density=window.density/160f;break;}
  return DiscreteTapTargets.points(centers,bounds,contacts().spread?Math.max(1,Math.round(6*density)):0);
 }
 void holdInput(String reason){if(!reason.equals(inputHold)){inputHold=reason;refresh();}}
 void rememberAutomationIssue(String target,PhaseEngine engine,String reason){
  if(!AutomationIncident.notable(reason))return;
  String state=engine==null?"":engine.current;PhaseEngine.Phase phase=engine==null?null:engine.phases.get(state);
  if(phase!=null)state=phase.name+" / "+state;
  android.content.SharedPreferences preferences=getSharedPreferences("automationDiagnostics",0);
  String previous=preferences.getString("lastIncident",""),next=AutomationIncident.remember(previous,target,state,reason,System.currentTimeMillis());
  if(!next.equals(previous))preferences.edit().putString("lastIncident",next).apply();
 }
 String inputReport(){long age=pilot==null||pilot.phaseSampleTakenAt<=0?-1:SystemClock.uptimeMillis()-pilot.phaseSampleTakenAt;return "入力："+inputHold+"\n"+contacts().describe()+"\n最小間隔："+minimumTapPeriod()+"ms / 指定間隔："+effectiveInterval()+"ms\n本体ジェスチャー要求："+inputRequests+" / 完了："+inputCompleted+" / 中断："+inputCancelled+" / 拒否："+inputRejected+" / 通知待ち超過："+inputTimedOut+"\n直近の入力エラー："+(lastInputError.isEmpty()?"なし":lastInputError)+"\n本体認識画像："+(age<0?"未取得":age+"ms前")+"\n"+(markerTapper==null?"タッパー未開始":markerTapper.report())+(pilot==null||pilot.upgrades==null?"":"\n"+pilot.upgrades.report())+"\n直近の自律診断：\n"+getSharedPreferences("automationDiagnostics",0).getString("lastIncident","なし")+"\n完了はAndroidの送信完了です。ゲームの反応は画面で確認します。";}
 boolean dispatchTrackedGesture(GestureDescription gesture,GestureResultCallback callback,Handler handler){
  return dispatchTrackedGesture(currentApp,gesture,callback,handler);
 }
 boolean dispatchTrackedGesture(String target,GestureDescription gesture,GestureResultCallback callback,Handler handler){
  inputRequests++;inputHold="送信中";if(progressErrors!=null)progressErrors.action(target,"ジェスチャー",0,Float.NaN,Float.NaN);
  long duration=1;for(int i=0;i<gesture.getStrokeCount();i++){GestureDescription.StrokeDescription stroke=gesture.getStroke(i);duration=Math.max(duration,stroke.getStartTime()+stroke.getDuration());}
  try{boolean accepted=gestures.dispatch(duration,tapReleaseMillis(target),completion->dispatchGesture(gesture,new GestureResultCallback(){
   public void onCompleted(GestureDescription g){completion.finished(true);}
   public void onCancelled(GestureDescription g){completion.finished(false);}
  },h),outcome->{
   if(outcome==GestureDispatchGuard.COMPLETED){inputSuccessAt=SystemClock.uptimeMillis();inputCompleted++;inputHold="送信完了";if(callback!=null)callback.onCompleted(gesture);}
   else{inputFailureAt=SystemClock.uptimeMillis();inputCancelled++;if(outcome==GestureDispatchGuard.TIMED_OUT)inputTimedOut++;inputHold=outcome==GestureDispatchGuard.TIMED_OUT?"入力完了の通知待ちを終了・再確認":"Androidによる入力中断";lastInputError=inputHold;if(callback!=null)callback.onCancelled(gesture);}
  });if(!accepted){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="Androidが入力を拒否・他の入力処理中";lastInputError=inputHold;}return accepted;}
  catch(RuntimeException failure){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="入力エラー："+VirtualWindows.brief(failure);lastInputError=inputHold;return false;}
 }
 int effectiveInterval(){return interval==3?1:Math.max(1,interval);}
 int busyPoll(){return performance==null?12:performance.profile().busyPollMs;}
 boolean markerEditing;
 boolean markerOverlaysVisible(){return markerEditing||!PresetRegistry.supports(currentApp)||pilot!=null&&pilot.phases.pkg.equals(currentApp)&&pilot.phases.customized;}
 String currentApp="";AutoPilot pilot; public static TapService instance;WindowManager wm;Handler h=new Handler(Looper.getMainLooper());LinearLayout panel;TextView status;Button start,add,del;View capture;ArrayList<Mark> marks=new ArrayList<>();boolean running=false,deleting=false;int interval=3,index=0,generation=0;long count=0;boolean busy=false;int size;android.content.BroadcastReceiver screenOff;
 class Mark {String id;TextView view;WindowManager.LayoutParams p;Mark(float x,float y){this(java.util.UUID.randomUUID().toString(),x,y);}Mark(String identity,float x,float y){id=identity;view=new UiStrings.DisplayText(TapService.this);view.setGravity(Gravity.CENTER);view.setTextColor(Color.WHITE);view.setTextSize(17);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xdddd7200);bg.setShape(1);bg.setStroke(3,Color.WHITE);view.setBackground(bg);p=params(size,size);p.x=(int)x-size/2;p.y=(int)y-size/2;final float[] xy=new float[4];view.setOnTouchListener((v,e)->{if(running)return false;switch(e.getAction()){case 0:xy[0]=e.getRawX();xy[1]=e.getRawY();xy[2]=p.x;xy[3]=p.y;return true;case 2:if(!deleting){p.x=(int)(xy[2]+e.getRawX()-xy[0]);p.y=(int)(xy[3]+e.getRawY()-xy[1]);wm.updateViewLayout(view,p);}return true;case 1:if(deleting&&Math.hypot(e.getRawX()-xy[0],e.getRawY()-xy[1])<20){wm.removeView(view);marks.remove(this);renumber();}save();return true;}return true;});if(!markerOverlaysVisible())view.setVisibility(View.INVISIBLE);wm.addView(view,p);}}
 WindowManager.LayoutParams params(int w,int height){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;}
 protected void onServiceConnected(){instance=this;VirtualResolutionLab.migrate(this);uiConfiguration=new android.content.res.Configuration(getResources().getConfiguration());if(performance==null)performance=new PerformanceSettings(this);contacts().refresh();if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);size=(int)(44*getResources().getDisplayMetrics().density);if(screenOff==null){screenOff=new android.content.BroadcastReceiver(){public void onReceive(Context c,Intent i){stopAll("画面が消灯した");}};registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));}if(pilot==null)pilot=new AutoPilot(this);if(progressErrors==null)progressErrors=new ProgressErrors(this);showPanel();progressErrors.start();pilot.wireless.startMonitor();refresh();}
 Button button(String label,Runnable action){Button b=UiKit.button(this,label,false,action);b.setTextSize(12);b.setMinHeight(UiKit.dp(this,42));b.setMinimumHeight(UiKit.dp(this,42));return b;}
 boolean compact=true;LinearLayout compactBar,panelDetails;ScrollView panelScroll;Button fold,primary;
 void compactPanel(boolean enabled){
  compact=enabled;if(panel==null)return;boolean learning=pilot!=null&&pilot.learning!=null&&pilot.learning.active;
  if(!enabled&&pilot!=null&&pilot.virtualWindows.headings!=null)pilot.virtualWindows.headings.collapse();
  if(panelScroll!=null)panelScroll.setVisibility(enabled&&!learning?View.GONE:View.VISIBLE);
  if(panelDetails!=null)for(int i=0;i<panelDetails.getChildCount();i++){View child=panelDetails.getChildAt(i);child.setVisibility(enabled&&learning&&child!=pilot.learning.controls?View.GONE:View.VISIBLE);}
  status.setMaxLines(enabled?5:7);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
  if(fold!=null)fold.setText(enabled?"詳細":"閉じる");
  if(panelScroll!=null){android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);panelScroll.getLayoutParams().height=Math.min(UiKit.dp(this,enabled&&learning?150:340),Math.max(UiKit.dp(this,72),dm.heightPixels-UiKit.dp(this,255)));panelScroll.requestLayout();}
  panel.post(()->{if(panel==null)return;android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();p.y=Math.max(0,Math.min(p.y,dm.heightPixels-panel.getHeight()));wm.updateViewLayout(panel,p);});
 }
 void panelSection(String name){TextView heading=UiKit.text(this,name,13,UiKit.MUTED,true);heading.setPadding(0,UiKit.dp(this,16),0,UiKit.dp(this,4));panelDetails.addView(heading);}
 LinearLayout panelRow(){return UiKit.row(this,panelDetails);}
 void panelButton(LinearLayout row,String label,Runnable action){UiKit.cell(this,row,button(label,action));}
 public void showPanel(){
  if(panel!=null)return;
  android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);
  panel=UiKit.column(this);panel.setPadding(UiKit.dp(this,12),UiKit.dp(this,8),UiKit.dp(this,12),UiKit.dp(this,12));panel.setBackground(UiKit.shape(this,0xfff8fafb,16));panel.setElevation(UiKit.dp(this,10));
  status=UiKit.text(this,"MarkerTap",13,UiKit.INK,false);status.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));status.setContentDescription("実行状況。ドラッグして操作パネルを移動");panel.addView(status);
  compactBar=UiKit.row(this,panel);primary=UiKit.button(this,"自動開始",true,()->{if(pilot!=null)AutomationRouter.start(pilot,true);});UiKit.cell(this,compactBar,primary);
  Button stop=button("全停止",()->stopAll());stop.setTextColor(0xffa52b34);UiKit.cell(this,compactBar,stop);
  LinearLayout quick=UiKit.row(this,panel);panelButton(quick,"対象",()->{if(pilot!=null)pilot.splitLauncher.choose();});panelButton(quick,"画面",()->{if(pilot!=null)pilot.virtualWindows.previewSizeDialog();});fold=button("詳細",()->compactPanel(!compact));UiKit.cell(this,quick,fold);
  if(progressErrors!=null)progressErrors.attach(panel);if(pilot!=null)pilot.virtualWindows.attachHeadings(panel);
  panelScroll=new ScrollView(this);panelScroll.setFillViewport(false);panelDetails=UiKit.column(this);panelScroll.addView(panelDetails);int maxHeight=Math.max(UiKit.dp(this,90),Math.min(UiKit.dp(this,340),dm.heightPixels-UiKit.dp(this,245)));panel.addView(panelScroll,new LinearLayout.LayoutParams(-1,maxHeight));
  panelSection("マーカーとタップ");LinearLayout manual=panelRow();start=button("連打開始",()->primaryAction());UiKit.cell(this,manual,start);panelButton(manual,"タップ確認",()->testTap());
  LinearLayout edits=panelRow();add=button("追加",()->{stop();deleting=false;if(ensureProfile())toggleAdd();});UiKit.cell(this,edits,add);del=button("削除",()->{stop();removeCapture();markerEditing=true;if(pilot!=null)pilot.hide(false);deleting=!deleting;del.setText(deleting?"削除中":"削除");});UiKit.cell(this,edits,del);
  LinearLayout manual2=panelRow();panelButton(manual2,"連打のみ",()->tapOnly());panelButton(manual2,"間隔を変更",()->{stop();interval=interval==3?50:interval==50?100:interval==100?250:interval==250?500:interval==500?1000:3;refresh();save();});
  LinearLayout markerTools=panelRow();panelButton(markerTools,"アプリ登録",()->{if(ensureProfile())UiStrings.toast(this,"このアプリ専用の配置と学習を使用します",0).show();});panelButton(markerTools,"マーカー全消去",()->{stop();if(clearArmed){for(Mark m:marks)wm.removeView(m.view);marks.clear();clearArmed=false;save();refresh();}else{clearArmed=true;status.setText("もう一度「マーカー全消去」で確定");h.postDelayed(()->{clearArmed=false;refresh();},2500);}});
  if(pilot!=null)pilot.controls(panelDetails);
  panelSection("状況と設定");LinearLayout errors=panelRow();panelButton(errors,"進行エラーを見る",()->ProgressErrors.showLatest(this));LinearLayout tools=panelRow();panelButton(tools,"詳しい状況",()->{if(pilot!=null)pilot.virtualWindows.showStatus();});panelButton(tools,"アプリ設定",()->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
  LinearLayout exit=panelRow();panelButton(exit,"パネルを終了",()->close());
  int width=Math.min(UiKit.dp(this,320),Math.max(UiKit.dp(this,180),dm.widthPixels-UiKit.dp(this,16)));
  WindowManager.LayoutParams p=params(width,-2);android.content.SharedPreferences saved=getSharedPreferences("panelUi",0);p.x=Math.max(0,Math.min(saved.getInt("x",UiKit.dp(this,8)),dm.widthPixels-width));p.y=Math.max(UiKit.dp(this,24),Math.min(saved.getInt("y",UiKit.dp(this,56)),Math.max(UiKit.dp(this,24),dm.heightPixels-UiKit.dp(this,235))));wm.addView(panel,p);
  final float[] pos=new float[4];status.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){pos[0]=e.getRawX();pos[1]=e.getRawY();pos[2]=p.x;pos[3]=p.y;}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){p.x=Math.max(0,Math.min((int)(pos[2]+e.getRawX()-pos[0]),dm.widthPixels-panel.getWidth()));p.y=Math.max(0,Math.min((int)(pos[3]+e.getRawY()-pos[1]),dm.heightPixels-panel.getHeight()));wm.updateViewLayout(panel,p);}else if(e.getActionMasked()==MotionEvent.ACTION_UP){getSharedPreferences("panelUi",0).edit().putInt("x",p.x).putInt("y",p.y).apply();}return true;});
  load();if(pilot!=null){pilot.logic.load(currentApp);pilot.phases.load(currentApp);pilot.hide(false);}compactPanel(true);refresh();
  if(!saved.contains("y"))panel.post(()->{if(panel==null)return;p.y=Math.max(UiKit.dp(this,24),dm.heightPixels-panel.getHeight()-UiKit.dp(this,20));wm.updateViewLayout(panel,p);});
 }
 boolean clearArmed;
 void toggleAdd(){markerEditing=true;if(pilot!=null)pilot.hide(false);if(capture!=null){removeCapture();return;}capture=new View(this);capture.setBackgroundColor(0x18000000);capture.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){marks.add(new Mark(e.getRawX(),e.getRawY()));renumber();save();}return true;});wm.addView(capture,params(-1,-1)); // place panel above capture
 WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();wm.removeView(panel);wm.addView(panel,p);add.setText("完了");refresh();}
 void removeCapture(){if(capture!=null){wm.removeView(capture);capture=null;}if(add!=null)add.setText("追加");}
 void renumber(){for(int i=0;i<marks.size();i++)marks.get(i).view.setText(""+(i+1));refresh();}
 void primaryAction(){if(pilot!=null&&pilot.dailyRunner.route.active()){stopAll();return;}if(pilot!=null&&pilot.virtualSession()){pilot.toggleAutomation();return;}if(pilot!=null&&(PresetRegistry.supports(currentApp)||GameTarget.added(currentApp))){if(pilot.active||pilot.splitPilot.active)stop();else pilot.startUsagiOrCurrent();}else if(running)stopMarkers();else if(pilot!=null&&pilot.active)beginMarkers();else begin();}
 long lastRefreshAt=0;
 void refresh(){long now=android.os.SystemClock.uptimeMillis();if((running||pilot!=null&&pilot.splitPilot.active)&&now-lastRefreshAt<(performance==null?250:performance.profile().previewMs))return;lastRefreshAt=now;if(pilot!=null)pilot.virtualWindows.refreshHeadings();boolean virtual=pilot!=null&&pilot.virtualSession();boolean usagi=PresetRegistry.supports(currentApp)||GameTarget.added(currentApp);String label=virtual?(pilot.automationRunning()?"自律実行中":"自動開始"):usagi?(pilot!=null&&pilot.automationRunning()?"自動停止":"自動開始"):(running?"連打停止":"連打開始");if(start!=null)start.setText(label);if(pilot!=null&&pilot.auto!=null)pilot.auto.setText(virtual&&pilot.automationRunning()?"自律実行中":pilot.automationRunning()?"オートマトン停止":"オートマトン開始");if(primary!=null)primary.setText(pilot!=null&&pilot.automationRunning()?"自動停止":"自動開始");if(status!=null&&virtual){status.setText(pilot.virtualWindows.panelSummary());return;}if(status!=null)status.setText((currentApp.isEmpty()?"未割当":appLabel(currentApp))+(pilot==null?"":"\n"+(pilot.automationRunning()?"自律ON：":"自律OFF：")+pilot.message)+"\n"+(running?markerTapper!=null&&markerTapper.active()?"連打中":"連打保留":pilot!=null&&pilot.active?"連打保留":"停止中")+"  "+(pilot!=null&&pilot.autonomousMain()?"画面から対象を認識":marks.size()+"箇所")+" / "+(interval==3?"最速":interval+"ms")+"（最小 "+minimumTapPeriod()+"ms） / 送信完了 "+count+"回"+"\n入力："+inputHold);}
 void touchable(boolean yes){if(yes&&(running||busy||pilot!=null&&pilot.learning!=null&&pilot.learning.active))yes=false;for(Mark m:marks){int oldFlags=m.p.flags;if(yes)m.p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else m.p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;if(oldFlags!=m.p.flags)wm.updateViewLayout(m.view,m.p);}}
 void begin(){markerEditing=true;if(pilot!=null)pilot.wireless.resumeMaintenance();stop();if(pilot!=null)pilot.hide(false);removeCapture();if(!ensureProfile())return;beginMarkers();}
 void beginMarkers(){if(pilot!=null&&pilot.learning!=null&&pilot.learning.active){pilot.note("学習中の連打はマーカー実演を押してください");return;}removeCapture();deleting=false;del.setText("削除");if(marks.isEmpty()){UiStrings.toast(this,"追加を押してマーカーを配置してください",0).show();return;}running=true;generation++;index=0;start.setText("連打停止");touchable(false);refresh();tick(generation);}
 void beginAutonomousTaps(){if(running||pilot==null||!pilot.autonomousMain())return;removeCapture();deleting=false;if(del!=null)del.setText("削除");running=true;generation++;index=0;touchable(false);refresh();tick(generation);}
 void stopMarkers(){running=false;generation++;if(markerTapper!=null)markerTapper.pause("停止中");if(start!=null)start.setText("連打開始");touchable(true);refresh();}
 void stopAll(){stopAll("全停止操作");}
 void stopAll(String reason){PcControl.cancelPlacement(this,reason);if(pilot!=null)pilot.wireless.pauseMaintenance();stop(reason);if(pilot!=null)pilot.note("停止理由："+reason+" / 開始で再開できます");}
 void stop(){stop(StopReason.describe(StopReason.origin(Thread.currentThread().getStackTrace())));}
 void stop(String reason){if(pilot!=null){pilot.virtualWindows.stopped(reason,StopReason.origin(Thread.currentThread().getStackTrace()));pilot.wireless.cancel();pilot.stop();}running=false;generation++;if(markerTapper!=null)markerTapper.pause("停止中");if(start!=null)start.setText("連打開始");if(wm!=null)touchable(true);refresh();}
 long safetyChecked=0,safetyBackAt=0;int safetyKind=0,safetyAttempts=0;
 void collectSafety(AccessibilityNodeInfo node,StringBuilder text,int[] remaining){if(node==null||remaining[0]--<=0)return;CharSequence pkg=node.getPackageName();if(pkg!=null&&pkg.toString().equals(getPackageName()))return;CharSequence value=node.getText(),description=node.getContentDescription();if(value!=null)text.append(value).append(' ');if(description!=null)text.append(description).append(' ');for(int i=0;i<node.getChildCount()&&remaining[0]>0;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){collectSafety(child,text,remaining);child.recycle();}}}
 boolean guardDialog(){long now=SystemClock.uptimeMillis();if(safetyChecked==0||now-safetyChecked>=100){safetyChecked=now;AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null){StringBuilder text=new StringBuilder();collectSafety(root,text,new int[]{500});root.recycle();safetyKind=DialogSafety.classify(text.toString());}else safetyKind=0;}if(safetyKind==0){if(pilot!=null&&pilot.usagiPresetActive()&&(pilot.phases.engine.current.equals(UsagiPreset.PREFIX+"error")||pilot.phases.engine.current.equals(UsagiPreset.PREFIX+"account")))pilot.enterUsagi("verify","保護対象の表示が閉じたため再判定");if(now-safetyBackAt>3000)safetyAttempts=0;return now-safetyBackAt<700;}if(pilot!=null&&pilot.usagiPresetActive()){String protectedState=UsagiPreset.PREFIX+(safetyKind==2?"account":"error");if(!pilot.phases.engine.current.equals(protectedState))pilot.enterUsagi(safetyKind==2?"account":"error","保護対象のテキスト検出");}if(pilot!=null)pilot.note(safetyKind==2?"アカウント操作を検出 / タップ禁止・戻るで復帰":"異常表示を検出 / タップ保留・戻るで復帰");if(!busy&&now-safetyBackAt>=2000){safetyBackAt=now;safetyAttempts++;if(safetyAttempts<=2){performGlobalAction(GLOBAL_ACTION_BACK);if(pilot!=null&&pilot.active&&pilot.phases.engine.phases.containsKey(pilot.phases.engine.current))pilot.phases.engine.enter(pilot.phases.engine.current,now);if(pilot!=null)pilot.phases.record("保護対象の表示を検出 / 確認ボタンを押さず戻る");}else if(safetyAttempts==3&&pilot!=null&&pilot.active){pilot.returnRegistered(pilot.token,()->{});}else{stop();if(pilot!=null)pilot.note("ダイアログから復帰できないため停止 / アカウント操作は実行しません");}}return true;}
 void tick(final int token){markerLogic.start(token);}
 void tapOnly(){
  if(pilot==null)return;
  if(pilot.virtualSession()){pilot.note("仮想画面は自動開始を使用します。入力だけの確認はプレビューを直接タップしてください");return;}
  if(running&&!pilot.active){stopMarkers();pilot.note("タッパー単独の連打を停止");return;}
  begin();if(running)pilot.note("タッパー単独：画面判定OFF / 全停止で停止 / 間隔の最速で完了直後に次を送信");
 }
 void testTap(){
  if(pilot==null)return;
  if(pilot.virtualSession()){stop();pilot.virtualWindows.minimize(false);pilot.note("タップ確認：仮想画面の押したい位置を1回タップしてください。状況で送信結果を確認できます");return;}
  stop();removeCapture();if(!ensureProfile())return;
  if(marks.isEmpty()){pilot.note("タップ確認：追加で押したい位置にマーカーを1個置き、完了を押してください");return;}
  if(busy){pilot.note("直前の入力完了後にタップ確認を押してください");return;}
  if(guardDialog())return;
  Mark mark=marks.get(0);float x=mark.p.x+size/2,y=mark.p.y+size/2;Rect bounds=pilot.splitLauncher.bounds(currentApp);
  if(bounds==null||!bounds.contains((int)x,(int)y)){holdInput("タップ確認のマーカーが対象画面の外です");pilot.note(inputHold);return;}
  tapper().update(new Tapper.Command(currentApp,Display.DEFAULT_DISPLAY,"test:"+generation,new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom},new float[]{x,y},60,1,SystemClock.uptimeMillis()+1500,false));
  pilot.note("タップ確認：60msで1回送信 / 自動判定は停止中");
 }

 void save(){try{org.json.JSONArray records=new org.json.JSONArray();for(Mark m:marks){org.json.JSONObject j=new org.json.JSONObject();j.put("id",m.id);j.put("x",m.p.x+size/2);j.put("y",m.p.y+size/2);records.put(j);}int[] screen=pilot==null?new int[]{getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels}:pilot.displaySize();getSharedPreferences("markers",0).edit().putInt("profileWidth:"+GameProfiles.key(this,currentApp),screen[0]).putInt("profileHeight:"+GameProfiles.key(this,currentApp),screen[1]).putString(currentApp.isEmpty()?"markerRecords":"profile:"+GameProfiles.key(this,currentApp),records.toString()).putInt(currentApp.isEmpty()?"interval":"interval:"+GameProfiles.key(this,currentApp),interval).putString("lastApp",currentApp).apply();}catch(Exception e){UiStrings.toast(this,"マーカー保存失敗",0).show();}}
 void load(){if(!marks.isEmpty())return;android.content.SharedPreferences prefs=getSharedPreferences("markers",0);if(currentApp.isEmpty())currentApp=prefs.getString("lastApp","");if(!prefs.getBoolean("default3Migrated",false)){prefs.edit().putInt("interval",3).putBoolean("default3Migrated",true).apply();}String recordKey=currentApp.isEmpty()?"markerRecords":"profile:"+GameProfiles.key(this,currentApp);interval=prefs.getInt(currentApp.isEmpty()?"interval":"interval:"+GameProfiles.key(this,currentApp),3);if(prefs.contains(recordKey)){try{org.json.JSONArray records=new org.json.JSONArray(prefs.getString(recordKey,"[]"));for(int i=0;i<records.length();i++){org.json.JSONObject j=records.getJSONObject(i);marks.add(new Mark(j.getString("id"),(float)j.getDouble("x"),(float)j.getDouble("y")));}}catch(Exception e){UiStrings.toast(this,"マーカー読込失敗",0).show();}}else if(currentApp.isEmpty()){String old=prefs.getString("positions","");for(String a:old.split(";")){try{String[] xy=a.split(",");marks.add(new Mark(Float.parseFloat(xy[0]),Float.parseFloat(xy[1])));}catch(Exception ignored){}}save();}renumber();}
 String appLabel(String pkg){String known=GameTarget.label(pkg.indexOf('#')>=0?pkg:GameProfiles.key(this,pkg));if(!known.isEmpty())return known;try{return getPackageManager().getApplicationInfo(GameTarget.packageName(pkg),0).loadLabel(getPackageManager()).toString();}catch(Exception e){return pkg;}}
 boolean validApp(String pkg){if(pkg==null||pkg.isEmpty()||pkg.equals(getPackageName())||pkg.equals("com.android.systemui"))return false;android.content.pm.ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);return home==null||home.activityInfo==null||!pkg.equals(home.activityInfo.packageName);}
 boolean ensureProfile(){String pkg=pilot==null?"":pilot.packageNow();if(!validApp(pkg)){UiStrings.toast(this,"対象アプリを開いてから操作してください",0).show();return false;}switchProfile(pkg);return true;}
 void switchProfile(String pkg){if(pkg.equals(currentApp)){if(pilot!=null)pilot.hide(false);else for(Mark m:marks)m.view.setVisibility(View.VISIBLE);return;}stop();removeCapture();if(currentApp.isEmpty()){currentApp=pkg;save();}else{save();for(Mark m:marks)wm.removeView(m.view);marks.clear();currentApp=pkg;markerEditing=false;load();save();}renumber();if(pilot!=null){pilot.logic.load(currentApp);pilot.phases.load(currentApp);pilot.hide(false);}if(pilot!=null)pilot.note("アプリ別 / 学習"+pilot.ruleCount(pkg)+"件");}
 Runnable detectApp=()->{if(panel==null||pilot==null||pilot.dailyRunner.route.active()||pilot.activeDialog!=null&&pilot.activeDialog.isShowing()||pilot.learning!=null&&pilot.learning.active||pilot.shotBusy||pilot.splitLauncher.preparing||pilot.wireless.working||pilot.splitPilot.active||!pilot.virtualWindows.windows.isEmpty())return;String pkg=pilot.packageNow();if(!validApp(pkg))return;android.content.SharedPreferences prefs=getSharedPreferences("markers",0);if(pkg.equals(currentApp)){if(pilot!=null)pilot.hide(false);else for(Mark m:marks)m.view.setVisibility(View.VISIBLE);return;}if(pilot.active){if(!pilot.recoverExternal()){pilot.note("対象アプリ外 / オートマトンの状態を保持して待機");pilot.schedulePhase(pilot.token,100);}return;}if(prefs.contains("profile:"+GameProfiles.key(this,pkg))){switchProfile(pkg);}else if(!currentApp.isEmpty()){stop();removeCapture();for(Mark m:marks)m.view.setVisibility(View.INVISIBLE);pilot.note("未登録アプリ / アプリ登録で作成");}};
 void prepareVirtualRun(){if(pilot!=null&&pilot.learning!=null)pilot.learning.cancel();if(pilot!=null)pilot.stopSingle(false);running=false;generation++;if(markerTapper!=null)markerTapper.pause("停止中");removeCapture();for(Mark m:marks)m.view.setVisibility(View.INVISIBLE);touchable(true);}
 void close(){stopAll("アプリの実行部終了");if(panel==null)return;if(pilot!=null)pilot.virtualWindows.closeViews();removeCapture();save();for(Mark m:marks)wm.removeView(m.view);marks.clear();if(panel!=null){wm.removeView(panel);panel=null;status=null;}}
 public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);boolean languageOnly=UiStrings.languageOnly(uiConfiguration,c);uiConfiguration=new android.content.res.Configuration(c);if(languageOnly){UiStrings.refresh(panel);if(pilot!=null){for(VirtualWindows.Window w:pilot.virtualWindows.windows){UiStrings.refresh(w.view);pilot.virtualWindows.state(w,w.runState);}pilot.virtualWindows.reflow();}lastRefreshAt=0;refresh();return;}contacts().refresh();if(pilot!=null&&pilot.virtualSession()){pilot.virtualWindows.reflow();refresh();return;}if(pilot!=null&&pilot.splitPilot.active){inputTargetVersion++;pilot.splitPilot.geometryChanged();refresh();return;}if(pilot!=null&&pilot.active&&(pilot.autonomousMain()||pilot.phases.engine.windowRelative)){inputTargetVersion++;pilot.geometryChanged();refresh();return;}stop();removeCapture();UiStrings.toast(this,"画面の向きが変わりました。マーカー位置を確認してください",0).show();} public void onAccessibilityEvent(AccessibilityEvent e){if(pilot!=null)pilot.wireless.onEvent(e);if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED){safetyChecked=0;inputTargetVersion++;if(pilot!=null)pilot.virtualWindows.invalidateScreenInfo();}if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&pilot!=null&&pilot.farmActive())pilot.farmConfirmed=false;if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&pilot!=null&&pilot.usagiPresetActive())pilot.usagiGameConfirmed=false;if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){if(pilot!=null){pilot.coinConfirmed=false;pilot.shoppingConfirmed=false;pilot.boxConfirmed=false;pilot.boxCheckedAt=0;pilot.poipoiConfirmed=false;pilot.poipoiCheckedAt=0;pilot.coinCheckedAt=0;pilot.shoppingCheckedAt=0;pilot.adHome.reset();}if(running)markerLogic.update();h.removeCallbacks(detectApp);h.postDelayed(detectApp,200);}}public void onInterrupt(){if(pilot!=null&&pilot.virtualSession()){stopMarkers();return;}stopAll("ユーザー補助の割込み");}public void onTrimMemory(int level){super.onTrimMemory(level);if(level==10||level==15||level>=60){PresetData.clear();if(performance!=null)performance.memoryPressure();if(pilot!=null)pilot.virtualWindows.trimFrameCircuit();}}public void onDestroy(){
  try{if(progressErrors!=null)progressErrors.close();}catch(Throwable ignored){}
  try{close();}catch(Throwable ignored){}
  try{if(screenOff!=null)unregisterReceiver(screenOff);}catch(Throwable ignored){}
  try{if(pilot!=null)pilot.destroy();}catch(Throwable ignored){}
  try{VirtualBridgeProvider.emergencyStop();}catch(Throwable ignored){}
  instance=null;super.onDestroy();
 }
}
