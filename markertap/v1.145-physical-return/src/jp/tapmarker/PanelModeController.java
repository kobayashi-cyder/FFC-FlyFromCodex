package jp.tapmarker;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.MotionEvent;
import java.util.WeakHashMap;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * User-controlled panel presentation. It never calls start/stop for automation.
 * Inspired by the Digital Agency Design System: explicit primary actions,
 * readable labels, large touch targets and non-color-only status feedback.
 */
final class PanelModeController {
 static final String PREF="panelUi", KEY="displayMode", TAG="markertap.ui.panelmode", STATUS_TAG="markertap.ui.panelmode.status";
 private static final WeakHashMap<View,Boolean> listeners=new WeakHashMap<>();
 private static final WeakHashMap<View,Integer> styles=new WeakHashMap<>();
 static final int BLUE=0xff005BAC, INK=0xff1A2633, SUB=0xff415365, BORDER=0xffB9C8D7;
 static int mode(Context c){return PanelModePolicy.normalize(c.getSharedPreferences(PREF,0).getInt(KEY,0));}
 static void persist(Context c,int next){c.getSharedPreferences(PREF,0).edit().putInt(KEY,PanelModePolicy.normalize(next)).apply();}

 /** Service connection initializes the existing panel. Both modes keep it visible. */
 static void initializePanel(TapService s){
  s.showPanelBeforePanelMode();
  attach(s);
  sync(s);
 }

 /** Legacy "操作パネル" means the movable floating presentation. */
 static void showPanel(TapService s){
  s.showPanelBeforePanelMode();
  attach(s);
  switchMode(s,PanelModePolicy.FLOAT);
 }

 /** Dock at the bottom of the screen; panel is visible and no longer draggable. */
 static void dockPanel(TapService s){switchMode(s,PanelModePolicy.DOCKED);}

 private static void switchMode(TapService s,int next){
  if(s.panel==null){
   persist(s,next);
   s.showPanelBeforePanelMode();
  }
  if(mode(s)==PanelModePolicy.FLOAT&&next==PanelModePolicy.DOCKED&&s.panel!=null){
   WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();
   s.getSharedPreferences(PREF,0).edit().putInt("x",p.x).putInt("y",p.y).apply();
  }
  persist(s,next);
  attach(s);
  if(next==PanelModePolicy.DOCKED)s.compactPanel(true);
  sync(s);
 }

 /** Restore the fixed or floating location after rotation, refresh and unlock. */
 static void sync(TapService s){
  if(s.panel==null)return;
  int expected=PanelModePolicy.showOverlay(mode(s),s.isScreenGuarded())?View.VISIBLE:View.GONE;
  if(s.panel.getVisibility()!=expected)s.panel.setVisibility(expected);
  Integer previous=styles.get(s.panel);
  int current=mode(s);
  if(previous==null||previous!=current){
   GradientDrawable background=UiKit.shape(s,current==PanelModePolicy.DOCKED?0xffffffff:0xfff8fafb,current==PanelModePolicy.DOCKED?0:16);
   if(current==PanelModePolicy.DOCKED)background.setStroke(UiKit.dp(s,1),BORDER);
   s.panel.setBackground(background);
   s.panel.setElevation(current==PanelModePolicy.DOCKED?0:UiKit.dp(s,10));
   styles.put(s.panel,current);
  }
  TextView label=s.panel.findViewWithTag(STATUS_TAG);
  if(label!=null)label.setText(PanelModePolicy.modeLabel(current));
  applyPosition(s);
 }

 private static void applyPosition(TapService s){
  if(s.panel==null||s.wm==null)return;
  WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();
  DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);
  if(dm.widthPixels<=0||dm.heightPixels<=0)return;
  int current=mode(s);
  int width,x,y;
  if(current==PanelModePolicy.DOCKED){
   width=dm.widthPixels;
   x=0;
   y=PanelModePolicy.dockY(dm.heightPixels,s.panel.getHeight(),UiKit.dp(s,28));
  }else{
   width=Math.min(UiKit.dp(s,320),Math.max(UiKit.dp(s,180),dm.widthPixels-UiKit.dp(s,16)));
   x=PanelModePolicy.clamp(s.getSharedPreferences(PREF,0).getInt("x",UiKit.dp(s,8)),0,dm.widthPixels-width);
   y=PanelModePolicy.clamp(s.getSharedPreferences(PREF,0).getInt("y",UiKit.dp(s,56)),0,dm.heightPixels-Math.max(s.panel.getHeight(),UiKit.dp(s,48)));
  }
  if(p.width!=width||p.x!=x||p.y!=y){
   p.width=width;p.x=x;p.y=y;
   try{s.wm.updateViewLayout(s.panel,p);}catch(IllegalArgumentException ignored){
    // A detached panel is reconstructed by the AccessibilityService.
   }
  }
 }

 /** The original status drag listener remains active only in floating mode. */
 private static void installDrag(TapService s){
  if(s.panel==null||s.status==null||listeners.containsKey(s.panel))return;
  final float[] start=new float[4];
  s.status.setOnTouchListener((v,event)->{
   if(!PanelModePolicy.movable(mode(s)))return false;
   WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();
   int type=event.getActionMasked();
   if(type==MotionEvent.ACTION_DOWN){
    start[0]=event.getRawX();start[1]=event.getRawY();
    start[2]=p.x;start[3]=p.y;return true;
   }
   if(type==MotionEvent.ACTION_MOVE){
    DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);
    p.x=PanelModePolicy.clamp(Math.round(start[2]+event.getRawX()-start[0]),0,dm.widthPixels-s.panel.getWidth());
    p.y=PanelModePolicy.clamp(Math.round(start[3]+event.getRawY()-start[1]),0,dm.heightPixels-s.panel.getHeight());
    s.wm.updateViewLayout(s.panel,p);return true;
   }
   if(type==MotionEvent.ACTION_UP){
    s.getSharedPreferences(PREF,0).edit().putInt("x",p.x).putInt("y",p.y).apply();return true;
   }
   return type==MotionEvent.ACTION_CANCEL;
  });
  s.panel.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   if(mode(s)==PanelModePolicy.DOCKED&&s.panel!=null)applyPosition(s);
  });
  listeners.put(s.panel,Boolean.TRUE);
 }

 static void refresh(TapService s){
  s.refreshBeforePanelMode();
  sync(s);
 }

 static Button button(Context c,String label,boolean primary,Runnable action){
  Button b=UiKit.button(c,label,primary,action);
  b.setTextSize(14);
  b.setMinHeight(UiKit.dp(c,48));b.setMinimumHeight(UiKit.dp(c,48));
  int background=primary?BLUE:0xffffffff;
  GradientDrawable shape=UiKit.shape(c,background,10);
  if(!primary)shape.setStroke(UiKit.dp(c,2),BLUE);
  b.setTextColor(primary?0xffffffff:BLUE);
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22005BAC),shape,null));
  b.setContentDescription(label);
  return b;
 }

 private static void attach(TapService s){
  if(s.panel==null)return;
  installDrag(s);
  if(s.panel.findViewWithTag(TAG)!=null)return;
  LinearLayout row=UiKit.column(s);
  row.setTag(TAG);
  row.setPadding(0,UiKit.dp(s,6),0,UiKit.dp(s,2));
  TextView note=UiKit.text(s,PanelModePolicy.modeLabel(mode(s)),13,SUB,false);
  note.setTag(STATUS_TAG);
  row.addView(note);
  LinearLayout actions=UiKit.row(s,row);
  UiKit.cell(s,actions,button(s,"画面下部に固定",false,()->dockPanel(s)));
  UiKit.cell(s,actions,button(s,"フロートに戻す",false,()->showPanel(s)));
  s.panel.addView(row,Math.min(3,s.panel.getChildCount()));
 }

 static void select(MainActivity a,int next){
  TapService s=TapService.instance;
  if(s!=null){
   if(PanelModePolicy.normalize(next)==PanelModePolicy.FLOAT)showPanel(s);
   else dockPanel(s);
  }else persist(a,next);
  a.showPage(0);
 }

 static void augmentHome(MainActivity a){
  // The old Activity source has a hard-coded build label; use the installed APK.
  try{
   android.content.pm.PackageInfo installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0);
   if(a.root!=null&&a.root.getChildCount()>0&&a.root.getChildAt(0) instanceof LinearLayout){
    LinearLayout header=(LinearLayout)a.root.getChildAt(0);
    if(header.getChildCount()>1&&header.getChildAt(1) instanceof TextView)
     ((TextView)header.getChildAt(1)).setText("v"+installed.versionName);
   }
  }catch(RuntimeException|android.content.pm.PackageManager.NameNotFoundException ignored){
   // A missing package query must never block the app's primary controls.
  }
  LinearLayout box=UiKit.card(a,a.content,"表示方法","画面下部への固定と、ドラッグで移動できるフロート表示を切り替えます。切り替え中も自動操作は継続します。");
  a.content.removeView(box);
  a.content.addView(box,Math.min(1,a.content.getChildCount()));
  TextView state=UiKit.text(a,PanelModePolicy.modeLabel(mode(a)),16,INK,true);
  state.setContentDescription("現在の表示方法："+PanelModePolicy.modeLabel(mode(a)));
  box.addView(state);
  LinearLayout actions=UiKit.row(a,box);
  UiKit.cell(a,actions,button(a,"画面下部に固定",false,()->select(a,PanelModePolicy.DOCKED)));
  UiKit.cell(a,actions,button(a,"フロートに戻す",true,()->select(a,PanelModePolicy.FLOAT)));

  // Normal Activity diagnostics also work while the docked toolbar is visible.
  TapService s=TapService.instance;
  LinearLayout diagnosis=UiKit.card(a,a.content,"自律操作の診断","入力送信とゲーム側の進行確認は別です。画面が確認できないときは誤タップを防止します。");
  if(s==null||s.pilot==null){
   diagnosis.addView(UiKit.text(a,"ユーザー補助サービスは未接続です。",15,SUB,false));
   return;
  }
  AutoPilot p=s.pilot;
  String phase=p.phases==null||p.phases.engine==null?"不明":p.phases.engine.current;
  long now=SystemClock.uptimeMillis();
  long age=p.phaseSampleTakenAt>0&&p.phaseSampleTakenAt<=now?now-p.phaseSampleTakenAt:-1;
  String ageLabel=age<0?"未取得":age+"ms前";
  String frameLabel=age<0?"画像未取得":age>=1500?"認識画像が古い・再取得待ち":"認識画像あり（進捗未確認）";
  String target=p.target==null||p.target.isEmpty()?s.currentApp:p.target;
  TextView detail=UiKit.text(a,"対象："+s.appLabel(target)
    +"\n稼働："+(p.automationRunning()?"実行中":"停止中")
    +"\n状態："+phase
    +"\n認識画像："+ageLabel
    +"\n画像判定："+frameLabel
    +"\n入力："+s.inputHold
    +"\n送信要求："+s.inputRequests+" / 完了："+s.inputCompleted+" / 拒否："+s.inputRejected+" / 中断："+s.inputCancelled,14,INK,false);
  detail.setPadding(0,UiKit.dp(a,6),0,UiKit.dp(a,8));diagnosis.addView(detail);
  UiKit.full(a,diagnosis,button(a,"進行エラーを確認",false,()->ProgressErrors.showLatest(a)));
 }
 private PanelModeController(){}
}
