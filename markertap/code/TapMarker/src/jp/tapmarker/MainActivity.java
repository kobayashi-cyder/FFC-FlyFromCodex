package jp.tapmarker;

import android.app.*;
import android.os.*;
import android.content.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 final Handler handler=new Handler(Looper.getMainLooper());
 LinearLayout root,content;TextView status,summary,targets,lanes;Button run;
 Spinner performanceMode,inputMode,positionMode,connectionMode;Button[] tabs=new Button[3];int page;
 final Runnable ticker=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,1000);}};
 int dp(float n){return UiKit.dp(this,n);}
 public void onCreate(Bundle state){
  super.onCreate(state);VirtualResolutionLab.migrate(this);getWindow().setStatusBarColor(UiKit.BG);getWindow().setNavigationBarColor(UiKit.BG);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  root=UiKit.column(this);root.setBackgroundColor(UiKit.BG);root.setPadding(dp(20),dp(32),dp(20),dp(16));
  root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=bars.top;bottom=bars.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}root.setPadding(dp(20),top+dp(12),dp(20),bottom+dp(12));return insets;});
  setContentView(root);root.requestApplyInsets();
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
  TextView title=UiKit.text(this,"MarkerTap",28,UiKit.INK,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  header.addView(UiKit.text(this,"v1.121",13,UiKit.MUTED,false));
  summary=UiKit.text(this,"",14,UiKit.MUTED,false);summary.setPadding(0,dp(4),0,dp(12));root.addView(summary);
  LinearLayout nav=UiKit.row(this,root);String[] names={"ホーム","設定","作成"};
  for(int i=0;i<names.length;i++){final int p=i;tabs[i]=UiKit.button(this,names[i],false,()->showPage(p));UiKit.cell(this,nav,tabs[i]);}
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(16);root.addView(scroll,sp);content=UiKit.column(this);scroll.addView(content);
  showPage(state==null?0:state.getInt("page",0));ProgressErrors.openFromIntent(this,getIntent());
 }
 protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);ProgressErrors.openFromIntent(this,intent);}
 protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);}
 protected void onResume(){super.onResume();refreshStatus();handler.removeCallbacks(ticker);handler.post(ticker);}
 protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}
 void service(java.util.function.Consumer<TapService> action){TapService s=TapService.instance;if(s==null){UiStrings.toast(this,"ユーザー補助を有効にしてください",Toast.LENGTH_SHORT).show();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}action.accept(s);}
 void showPage(int next){page=Math.max(0,Math.min(2,next));content.removeAllViews();status=targets=lanes=null;run=null;connectionMode=null;
  for(int i=0;i<tabs.length;i++){tabs[i].setTextColor(i==page?0xffffffff:UiKit.INK);tabs[i].setBackground(UiKit.shape(this,i==page?UiKit.GREEN:0xffe3e9eb,10));}
  if(page==0)home();else if(page==1)settings();else creation();refreshStatus();
 }
 void home(){
  LinearLayout current=UiKit.card(this,content,"いまの状態","");status=UiKit.text(this,"",21,UiKit.GREEN,true);current.addView(status);
  lanes=UiKit.text(this,"",14,UiKit.MUTED,false);lanes.setPadding(0,dp(10),0,dp(4));current.addView(lanes);
  run=UiKit.button(this,"自動開始",true,()->service(s->{AutomationRouter.start(s.pilot,true);refreshStatus();}));UiKit.full(this,current,run);
  LinearLayout actions=UiKit.row(this,current);Button stop=UiKit.button(this,"全停止",false,()->service(s->{s.stopAll("ホーム画面の全停止");refreshStatus();}));stop.setTextColor(0xffa52b34);UiKit.cell(this,actions,stop);UiKit.cell(this,actions,UiKit.button(this,"操作パネル",false,()->service(s->{s.showPanel();s.compactPanel(true);moveTaskToBack(true);})));
  LinearLayout diagnostics=UiKit.card(this,content,"進行エラー","進行不良を検出すると、診断文と操作前後の画像を端末に保存します。画像付き診断を共有できます。");UiKit.full(this,diagnostics,UiKit.button(this,"進行エラーを見る",false,()->ProgressErrors.showLatest(this)));UiKit.full(this,diagnostics,UiKit.button(this,"画像付き診断を共有",false,()->ProgressErrors.shareLatest(this)));UiKit.full(this,diagnostics,UiKit.button(this,"エラー通知の設定",false,()->ProgressErrors.notificationSettings(this)));
  LinearLayout selected=UiKit.card(this,content,"操作するアプリ","同時稼働数は2〜4から選択。CMの待機中も各画面を維持します。");targets=UiKit.text(this,"",15,UiKit.INK,false);selected.addView(targets);UiKit.full(this,selected,UiKit.button(this,"アプリを選んで画面を開く",false,()->service(s->s.pilot.splitLauncher.choose())));
  LinearLayout setup=UiKit.card(this,content,"接続と確認","初回はユーザー補助を有効にし、端末内接続を設定します。");
  UiKit.full(this,setup,UiKit.button(this,"ユーザー補助を設定",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
  LinearLayout checks=UiKit.row(this,setup);UiKit.cell(this,checks,UiKit.button(this,"端末内接続",false,()->service(s->s.pilot.wireless.guide(false))));UiKit.cell(this,checks,UiKit.button(this,"詳しい状況",false,()->service(s->s.pilot.virtualWindows.showStatus())));
  UiKit.full(this,setup,UiKit.button(this,"端末内実行部を再起動",false,()->service(s->s.pilot.wireless.confirmRestart())));
 }
 void refreshStatus(){
  TapService s=TapService.instance;summary.setText(s==null?"ユーザー補助の接続が必要です":"画面ごとの状態を確認して操作できます");if(status==null)return;
  boolean active=s!=null&&s.pilot!=null&&s.pilot.automationRunning();status.setText(s==null?"未接続":active?"自動操作を実行中":"停止中");status.setTextColor(s==null?0xff99600f:active?UiKit.GREEN:UiKit.INK);run.setEnabled(s!=null);run.setText(active?"自動停止":"自動開始");
  if(s==null){lanes.setText("下の「ユーザー補助を設定」から接続してください。");targets.setText("接続後に対象アプリを表示します。");return;}
  StringBuilder lines=new StringBuilder();for(VirtualWindows.Window w:s.pilot.virtualWindows.windows){if(lines.length()>0)lines.append("\n\n");lines.append(s.appLabel(w.pkg)).append("\n").append(w.inputRecovery.pending?w.inputRecovery.status():UiKit.concise(w.runState));if(!w.stopReason.isEmpty())lines.append("\n").append(getString(R.string.stop_reason_value,UiStrings.status(this,w.stopReason)));else if(!w.inputError.isEmpty())lines.append("\n").append(getString(R.string.target_input_rechecking));lines.append("  ·  ").append(getString(R.string.sent_count,Long.toString(w.inputs)));}
  if(lines.length()==0)lines.append(s.pilot.message);lanes.setText(lines.toString());StringBuilder chosen=new StringBuilder(s.getString(R.string.virtual_concurrency_value,s.pilot.splitLauncher.virtualLimit()));for(String pkg:s.pilot.splitLauncher.targets()){if(chosen.length()>0)chosen.append("\n");chosen.append("・").append(s.appLabel(pkg));}targets.setText(chosen.length()==0?"未選択":chosen.toString());
 }
 Spinner spinner(LinearLayout box,String[] values,int selected,java.util.function.IntConsumer change){Spinner v=new Spinner(this);ArrayAdapter<String> a=new UiStrings.StringAdapter(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);v.setAdapter(a);v.setSelection(selected);v.setMinimumHeight(dp(48));box.addView(v,new LinearLayout.LayoutParams(-1,-2));v.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View view,int pos,long id){change.accept(pos);}});return v;}
 void settings(){
  LinearLayout language=UiKit.card(this,content,"言語","端末の言語設定に従います。日本語・英語に対応しています。");language.addView(UiKit.text(this,"アプリ名・記録した文字・独自の状態名は元の表記を維持します。",13,UiKit.MUTED,false));UiKit.full(this,language,UiKit.button(this,"言語設定を開く",false,()->{try{Intent intent=Build.VERSION.SDK_INT>=33?new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,android.net.Uri.parse("package:"+getPackageName())):new Intent(Settings.ACTION_LOCALE_SETTINGS);startActivity(intent);}catch(android.content.ActivityNotFoundException unavailable){UiStrings.toast(this,getString(R.string.locale_settings_unavailable),Toast.LENGTH_LONG).show();}}));
  LinearLayout load=UiKit.card(this,content,"処理の負荷","通常は「自動」。小さい端末では画面取得の負荷を抑えます。");
  performanceMode=spinner(load,new String[]{"自動（推奨）","軽量","標準","高速"},getSharedPreferences("performance",0).getInt("mode",0),pos->{if(getSharedPreferences("performance",0).getInt("mode",0)!=pos){getSharedPreferences("performance",0).edit().putInt("mode",pos).apply();if(TapService.instance!=null){TapService.instance.performance.refresh();TapService.instance.refresh();}}});
  LinearLayout taps=UiKit.card(this,content,"タップの押し方","押す・離すの時間と、連打する位置を設定します。");
  inputMode=spinner(taps,new String[]{"自動（推奨）","離散入力（押す・離す）","最短入力（1ms要求）"},getSharedPreferences("tapInput",0).getInt("mode",TapContactPolicy.AUTO),pos->{if(getSharedPreferences("tapInput",0).getInt("mode",TapContactPolicy.AUTO)!=pos){getSharedPreferences("tapInput",0).edit().putInt("mode",pos).apply();inputChanged();}});
  positionMode=spinner(taps,new String[]{"場所：5点からランダム","場所：中央に固定"},getSharedPreferences("tapInput",0).getBoolean("spread",true)?0:1,pos->{boolean spread=pos==0;if(getSharedPreferences("tapInput",0).getBoolean("spread",true)!=spread){getSharedPreferences("tapInput",0).edit().putBoolean("spread",spread).apply();inputChanged();}});
  UiKit.full(this,taps,UiKit.button(this,"タップが届くか10回確認",false,()->startActivity(new Intent(this,InputCheckActivity.class))));
  LinearLayout connection=UiKit.card(this,content,"接続の維持","自動接続でも初回のペア設定が必要です。全停止すると自動復帰も休止します。");
  connectionMode=spinner(connection,new String[]{"Wi-Fi接続中は自動で維持","手動で接続する"},getSharedPreferences("wirelessAdb",0).getBoolean("automaticWifi",false)?0:1,pos->{boolean enabled=pos==0;if(getSharedPreferences("wirelessAdb",0).getBoolean("automaticWifi",false)!=enabled)getSharedPreferences("wirelessAdb",0).edit().putBoolean("automaticWifi",enabled).apply();});
  UiKit.full(this,connection,UiKit.button(this,"接続情報を入力",false,()->service(s->s.pilot.wireless.input())));
  LinearLayout resolution=UiKit.card(this,content,"仮想画面の起動解像度","端末の通常解像度・DPIで起動します。");UiKit.full(this,resolution,UiKit.button(this,"解像度を確認",false,()->VirtualResolutionLab.show(this)));
  LinearLayout display=UiKit.card(this,content,"仮想画面の見え方","縮小・最小表示でも内部の画像判定と入力座標を維持します。");UiKit.full(this,display,UiKit.button(this,"表示サイズを変更",false,()->service(s->s.pilot.virtualWindows.previewSizeDialog())));
 }
 void inputChanged(){TapService s=TapService.instance;if(s!=null){s.stop("タップ設定を変更");s.contacts().refresh();s.refresh();}UiStrings.toast(this,"設定を保存しました。自動開始で再開します。",Toast.LENGTH_SHORT).show();}
 void creation(){
  LinearLayout video=UiKit.card(this,content,"録画から作る","録画を解析して状態と遷移を作ります。タップ位置を確認してから実行します。");UiKit.full(this,video,UiKit.button(this,"録画からオートマトンを生成",true,()->RecordedImportActivity.open(this)));UiKit.full(this,video,UiKit.button(this,"録画で停止箇所を探して改修",false,()->RecordedImportActivity.openRepair(this)));
  LinearLayout edit=UiKit.card(this,content,"設定と学習","操作パネルの「詳細」から学習・かんたん編集・領域設定を開けます。");UiKit.full(this,edit,UiKit.button(this,"詳細パネルを開く",false,()->service(s->{s.showPanel();s.compactPanel(false);moveTaskToBack(true);})));
  LinearLayout help=UiKit.card(this,content,"使い方","");UiKit.full(this,help,UiKit.button(this,"基本の手順を見る",false,()->new UiStrings.DialogBuilder(this).setTitle("基本の手順").setMessage("1. ユーザー補助を接続\n2. 操作するアプリを選択\n3. 仮想画面を開く\n4. 自動開始\n\n標準対応外の操作は、学習または録画から作成します。未知の画面では状態を確認してください。\n\n操作パネルは上部をドラッグして移動できます。小窓の見出しをタップすると、その画面の詳しい状況を表示します。").setPositiveButton("閉じる",null).show()));
 }
}
