package jp.tapmarker;

import android.content.Context;
import android.content.Intent;
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
 static final String PREF="panelUi", KEY="displayMode", TAG="markertap.ui.panelmode";
 static final int BLUE=0xff005BAC, INK=0xff1A2633, SUB=0xff415365, BORDER=0xffB9C8D7;
 static int mode(Context c){return PanelModePolicy.normalize(c.getSharedPreferences(PREF,0).getInt(KEY,0));}
 static void persist(Context c,int next){c.getSharedPreferences(PREF,0).edit().putInt(KEY,PanelModePolicy.normalize(next)).apply();}

 /** Service connection always builds its dependent controls, but can start hidden. */
 static void initializePanel(TapService s){
  s.showPanelBeforePanelMode();
  attach(s);
  sync(s);
 }

 /** Explicit restore from a normal Activity or a legacy "操作パネル" control. */
 static void showPanel(TapService s){
  persist(s,PanelModePolicy.FLOAT);
  s.showPanelBeforePanelMode();
  attach(s);
  sync(s);
 }

 /** Hiding the overlay must never call stopAll(), stopMarkers(), or pilot.stop(). */
 static void hidePanel(TapService s){
  persist(s,PanelModePolicy.APP);
  sync(s);
 }

 /** Preserve mode across screen lock/unlock, refreshes and service recreation. */
 static void sync(TapService s){
  if(s.panel==null)return;
  int expected=PanelModePolicy.showOverlay(mode(s),s.isScreenGuarded())?View.VISIBLE:View.GONE;
  if(s.panel.getVisibility()!=expected)s.panel.setVisibility(expected);
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
  if(s.panel==null||s.panel.findViewWithTag(TAG)!=null)return;
  // Directly under the action bar: visible without expanding the long settings list.
  LinearLayout row=UiKit.column(s);
  row.setTag(TAG);
  row.setPadding(0,UiKit.dp(s,6),0,UiKit.dp(s,2));
  TextView note=UiKit.text(s,"表示方法：操作を停止せず切り替え",13,SUB,false);
  row.addView(note);
  LinearLayout actions=UiKit.row(s,row);
  UiKit.cell(s,actions,button(s,"通常画面へ",false,()->{
   hidePanel(s);
   try{s.startActivity(new Intent(s,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
   catch(RuntimeException ignored){
    // Display mode remains saved; launcher opens the ordinary app screen.
    UiStrings.toast(s,"ホーム画面のMarkerTapから操作を続けられます",0).show();
   }
  }));
  UiKit.cell(s,actions,button(s,"隠す",false,()->hidePanel(s)));
  s.panel.addView(row,Math.min(3,s.panel.getChildCount()));
 }

 static void select(MainActivity a,int next){
  persist(a,next);
  TapService s=TapService.instance;
  if(s!=null){
   if(PanelModePolicy.normalize(next)==PanelModePolicy.FLOAT)showPanel(s);
   else hidePanel(s);
  }
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
  LinearLayout box=UiKit.card(a,a.content,"表示方法","通常画面とフロート表示を切り替えます。表示方法の変更では自動操作は停止しません。");
  a.content.removeView(box);
  a.content.addView(box,Math.min(1,a.content.getChildCount()));
  TextView state=UiKit.text(a,PanelModePolicy.modeLabel(mode(a)),16,INK,true);
  state.setContentDescription("現在の表示方法："+PanelModePolicy.modeLabel(mode(a)));
  box.addView(state);
  LinearLayout actions=UiKit.row(a,box);
  UiKit.cell(a,actions,button(a,"フロートを隠す",false,()->select(a,PanelModePolicy.APP)));
  UiKit.cell(a,actions,button(a,"フロートを戻す",true,()->select(a,PanelModePolicy.FLOAT)));

  // The primary failure diagnostic is part of the normal app, not a hidden overlay.
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
