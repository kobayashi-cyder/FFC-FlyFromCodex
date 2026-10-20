package jp.tapmarker;

import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Headings live inside the control panel; opening one only reveals its existing preview. */
final class VirtualWindowPanel {
 final TapService s;final VirtualWindows owner;final LinearLayout root,list;final ScrollView scroll;final Button toggle;
 final ArrayList<VirtualWindows.Window> represented=new ArrayList<>();final ArrayList<Row> rows=new ArrayList<>();
 boolean expanded;
 final class Row {
  final VirtualWindows.Window window;final TextView title,state;final Button expand;
  Row(VirtualWindows.Window w){
   window=w;LinearLayout row=new LinearLayout(s);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(UiKit.dp(s,56));row.setPadding(UiKit.dp(s,8),UiKit.dp(s,6),0,UiKit.dp(s,6));
   LinearLayout text=UiKit.column(s);text.setPadding(0,0,UiKit.dp(s,6),0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
   title=UiKit.text(s,s.appLabel(w.pkg),12,UiKit.INK,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);text.addView(title,new LinearLayout.LayoutParams(-1,-2));
   state=UiKit.text(s,"準備中",10,UiKit.MUTED,false);state.setSingleLine(true);state.setEllipsize(TextUtils.TruncateAt.END);text.addView(state,new LinearLayout.LayoutParams(-1,-2));
   text.setOnClickListener(v->owner.showPreview(w));text.setOnLongClickListener(v->{if(owner.windows.contains(w))owner.showWindowStatus(w);return true;});
   expand=s.button("拡大",()->{if(w.minimized)owner.showPreview(w);else owner.hidePreview(w);});row.addView(expand,new LinearLayout.LayoutParams(UiKit.dp(s,60),-2));
   list.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  void refresh(){
   String app=s.appLabel(window.pkg);if(!app.contentEquals(title.getText()))title.setText(app);
   String value=window.inputRecovery.pending?window.inputRecovery.status():!window.stopReason.isEmpty()?"停止："+window.stopReason:UiKit.concise(window.runState);
   if(value.startsWith(app+"："))value=value.substring(app.length()+1);
   if(!UiStrings.status(s,value).contentEquals(state.getText()))state.setText(value);
   String action=window.minimized?"拡大":"収納";if(!UiStrings.text(s,action).contentEquals(expand.getText()))expand.setText(action);
   expand.setContentDescription(UiStrings.text(s,window.minimized?"この仮想画面を表示":"この映像を見出しへ収納"));
  }
 }
 VirtualWindowPanel(TapService s,VirtualWindows owner,LinearLayout parent){
  this.s=s;this.owner=owner;expanded=s.getSharedPreferences("virtualPreview",0).getBoolean("headingsExpanded",false);
  root=UiKit.column(s);LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=UiKit.dp(s,8);parent.addView(root,margin);
  toggle=s.button("仮想画面 ▾",()->setExpanded(!expanded));toggle.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);root.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
  scroll=new ScrollView(s);scroll.setFillViewport(false);list=UiKit.column(s);list.setBackground(UiKit.shape(s,0xffedf2f3,10));scroll.addView(list,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,UiKit.dp(s,56)));refresh();
 }
 void setExpanded(boolean enabled){
  expanded=enabled;s.getSharedPreferences("virtualPreview",0).edit().putBoolean("headingsExpanded",enabled).apply();refresh();
  if(enabled)s.compactPanel(true);else clampPanel();
 }
 void collapse(){if(expanded){expanded=false;refresh();}}
 void refresh(){
  boolean same=represented.size()==owner.windows.size();if(same)for(int i=0;i<represented.size();i++)if(represented.get(i)!=owner.windows.get(i)){same=false;break;}
  if(!same){represented.clear();represented.addAll(owner.windows);rows.clear();list.removeAllViews();for(VirtualWindows.Window w:represented)rows.add(new Row(w));}
  root.setVisibility(represented.isEmpty()?View.GONE:View.VISIBLE);String heading=expanded?"仮想画面 ▴":"仮想画面 ▾";if(!UiStrings.text(s,heading).contentEquals(toggle.getText()))toggle.setText(heading);scroll.setVisibility(expanded?View.VISIBLE:View.GONE);
  DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);
  int reserve=270+(s.getSharedPreferences("automationDiagnostics",0).getBoolean("progressUnread",false)?56:0)+(s.pilot!=null&&s.pilot.learning!=null&&s.pilot.learning.active?150:0);int available=Math.max(UiKit.dp(s,48),dm.heightPixels-UiKit.dp(s,reserve));int height=Math.min(Math.max(1,rows.size())*UiKit.dp(s,70),Math.min(UiKit.dp(s,280),available));
  if(scroll.getLayoutParams().height!=height){scroll.getLayoutParams().height=height;scroll.requestLayout();}
  for(Row row:rows)row.refresh();
 }
 void clampPanel(){if(s.panel!=null)s.panel.post(()->{if(s.panel==null)return;DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();p.x=Math.max(0,Math.min(p.x,dm.widthPixels-s.panel.getWidth()));p.y=Math.max(0,Math.min(p.y,dm.heightPixels-s.panel.getHeight()));s.wm.updateViewLayout(s.panel,p);});}
}
