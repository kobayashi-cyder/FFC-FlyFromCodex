package jp.tapmarker;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Imports a user-selected local video without storage-wide permissions or uploading it. */
public final class RecordedImportActivity extends Activity {
 static final int PICK_VIDEO=72;
 final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 AtomicBoolean cancelled=new AtomicBoolean();int generation;boolean busy;
 Uri video;Bitmap preview;float[] crop={0,0,1,1},exclude;
 LinearLayout root,review;TextView file,status,cropInfo;Spinner targets,spacing;EditText start,end;Button pick,analyze,cancel;
 final ArrayList<String> targetKeys=new ArrayList<>();
 long durationMs;String importTarget="",baseline="";
 RecordedAutomatonGenerator.Model model;Spinner purpose;CheckBox repairTestStart;PhaseEngine repairBase;boolean repairMode;
 static void open(Context context){open(context,false);}
 static void openRepair(Context context){open(context,true);}
 static void open(Context context,boolean repair){
  TapService s=TapService.instance;String target="";
  if(s!=null){target=GameProfiles.key(s,s.currentApp);s.stopAll("録画から生成");s.pilot.recordingImportActive=true;s.pilot.hide(true);if(s.pilot.activeDialog!=null)s.pilot.activeDialog.dismiss();}
  Intent intent=new Intent(context,RecordedImportActivity.class).putExtra("target",target).putExtra("repair",repair);if(!(context instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);root=new LinearLayout(this);root.setOrientation(1);int pad=dp(18);root.setPadding(pad,dp(20),pad,dp(24));ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  text(root,"録画から生成・繰り返し改修",23);purpose=spinner(root,"録画の使い方",new String[]{"新規オートマトンを生成","既存設定の修正箇所を探して改修"});purpose.setSelection(saved==null?(getIntent().getBooleanExtra("repair",false)?1:0):(saved.getBoolean("repair",false)?1:0));repairTestStart=new UiStrings.DisplayCheck(this);repairTestStart.setText("改修後はこの録画の最初の状態からテストする");repairTestStart.setChecked(saved==null||saved.getBoolean("repairTestStart",true));root.addView(repairTestStart);
  text(root,"1 録画と対象を選ぶ → 2 アプリの範囲を指定 → 3 解析 → 4 操作を確認して保存\n録画の画面変化から状態・遷移・判定領域を抽出します。操作位置は画像上で登録します。改修モードは既存設定との比較 → 修正候補 → 同じ録画で再検証 → 適用 → 次の録画の解析を繰り返せます。",15);
  String selected=saved==null?getIntent().getStringExtra("target"):saved.getString("target");
  LinkedHashSet<String> apps=new LinkedHashSet<>(Arrays.asList(PresetRegistry.APPS));apps.addAll(Arrays.asList(GameTarget.ADDED_APPS));if(selected!=null&&!selected.isEmpty())apps.add(GameTarget.packageName(selected));
  ArrayList<String> labels=new ArrayList<>();for(String app:apps){String[] keys=GameTarget.USAPO.equals(app)?new String[]{GameTarget.FARM,GameTarget.BALLOON,GameTarget.USAPO}:new String[]{app};for(String key:keys){targetKeys.add(key);labels.add(label(key));}}
  targets=spinner(root,"保存するアプリ・ゲーム",labels.toArray(new String[0]));if(selected!=null&&targetKeys.contains(selected))targets.setSelection(targetKeys.indexOf(selected));
  file=text(root,"録画が選択されていません",15);pick=button(root,"録画ファイルを選択",()->{cancelWork();Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(intent,PICK_VIDEO);}catch(ActivityNotFoundException error){message("ファイル選択アプリがありません");}});
  start=field(root,"解析開始（秒）","0");end=field(root,"解析終了（秒）","");spacing=spinner(root,"取得間隔",new String[]{"0.5秒：短い遷移を拾う","1秒：標準","2秒：低負荷"});spacing.setSelection(1);
  button(root,"開始時刻の画像を表示",()->{if(!busy&&video!=null)loadPreview(false);});
  cropInfo=text(root,"範囲：録画全体 / 除外なし",14);
  button(root,"アプリの範囲を指定",()->area(false));button(root,"操作パネルなどの除外範囲を指定",()->area(true));button(root,"範囲と除外をリセット",()->{if(busy)return;crop=new float[]{0,0,1,1};exclude=null;model=null;review.removeAllViews();updateArea();});
  analyze=button(root,"録画を解析する",()->analyze());cancel=button(root,"解析を中止",()->{cancelWork();message("解析を中止しました。保存済みの設定は変更していません");});cancel.setEnabled(false);
  status=text(root,"",15);review=new LinearLayout(this);review.setOrientation(1);root.addView(review);
  button(root,"閉じる",()->finish());
  if(saved!=null){start.setText(saved.getString("start","0"));end.setText(saved.getString("end",""));spacing.setSelection(saved.getInt("spacing",1));float[] bounds=saved.getFloatArray("crop");if(bounds!=null)crop=bounds;exclude=saved.getFloatArray("exclude");String uri=saved.getString("uri");if(uri!=null){video=Uri.parse(uri);loadPreview(false);}updateArea();}
  TextWatcher changed=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){invalidateResult();}public void afterTextChanged(Editable e){}};start.addTextChangedListener(changed);end.addTextChangedListener(changed);
  AdapterView.OnItemSelectedListener choice=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> view){}public void onItemSelected(AdapterView<?> view,View row,int index,long id){invalidateResult();}};targets.setOnItemSelectedListener(choice);spacing.setOnItemSelectedListener(choice);purpose.setOnItemSelectedListener(choice);button(root,"前回の録画を読み込んで再解析",()->restoreLastVideo());button(root,"改修前に戻す",()->restoreRepair());
 }
 void invalidateResult(){if(model==null)return;model=null;review.removeAllViews();message("対象・解析範囲・間隔が変わりました。録画を再解析してください");}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(LinearLayout into,String value,int size){TextView t=new UiStrings.DisplayText(this);t.setText(value);t.setTextSize(size);t.setPadding(0,dp(7),0,dp(7));into.addView(t);return t;}
 Button button(LinearLayout into,String title,Runnable action){Button b=new UiStrings.DisplayButton(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());into.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
 EditText field(LinearLayout into,String name,String value){text(into,name,14);EditText edit=new EditText(this);edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);edit.setSingleLine(true);edit.setText(value);into.addView(edit);return edit;}
 Spinner spinner(LinearLayout into,String name,String[] values){text(into,name,14);Spinner view=new Spinner(this);view.setAdapter(new UiStrings.StringAdapter(this,android.R.layout.simple_spinner_dropdown_item,values));into.addView(view);return view;}
 String label(String key){String known=GameTarget.label(key);if(!known.isEmpty())return known;if(GameTarget.USAPO.equals(key))return "うさポ共通画面";try{return getPackageManager().getApplicationInfo(GameTarget.packageName(key),0).loadLabel(getPackageManager()).toString();}catch(Exception e){if(UsagiPreset.APP.equals(key))return "増えうさぎ";if(FarmPreset.APP.equals(key))return "農園うさぎ";if(CoinPreset.APP.equals(key))return "うさぎのコイン落とし";if(ShoppingPreset.APP.equals(key))return "買い物うさぎ";return key;}}
 void message(String text){status.setText(text);}
 void busy(boolean value){busy=value;pick.setEnabled(!value);targets.setEnabled(!value);start.setEnabled(!value);end.setEnabled(!value);spacing.setEnabled(!value);analyze.setEnabled(!value);purpose.setEnabled(!value);repairTestStart.setEnabled(!value);cancel.setEnabled(value);}
 void cancelWork(){cancelled.set(true);generation++;busy(false);}
 int work(){cancelWork();cancelled=new AtomicBoolean();busy(true);return generation;}
 boolean live(int token){return token==generation&&!cancelled.get()&&!isFinishing()&&!isDestroyed();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_VIDEO||result!=RESULT_OK||data==null||data.getData()==null)return;video=data.getData();try{getContentResolver().takePersistableUriPermission(video,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception unavailable){}if(preview!=null){preview.recycle();preview=null;}durationMs=0;start.setText("0");crop=new float[]{0,0,1,1};exclude=null;model=null;review.removeAllViews();updateArea();loadPreview(true);}
 static long metadata(MediaMetadataRetriever r,int key){try{return Long.parseLong(r.extractMetadata(key));}catch(Exception invalid){return 0;}}
 static Bitmap decode(MediaMetadataRetriever r,long ms){
  Bitmap b;if(Build.VERSION.SDK_INT>=27){int width=(int)metadata(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),height=(int)metadata(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);int rotation=(int)metadata(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);if(rotation==90||rotation==270){int temp=width;width=height;height=temp;}float scale=Math.min(1,Math.min(384f/Math.max(1,width),960f/Math.max(1,height)));b=r.getScaledFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));}
  else{long width=metadata(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),height=metadata(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);if(width*height>12000000)throw new IllegalArgumentException("この端末では録画の解像度が大きすぎます。縮小した録画を選んでください");b=r.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST);if(b!=null){float scale=Math.min(1,Math.min(384f/b.getWidth(),960f/b.getHeight()));if(scale<1){Bitmap small=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);if(small!=b)b.recycle();b=small;}}}
  if(b==null)throw new IllegalArgumentException("録画のフレームを読み出せません。端末が対応するMP4などを選んでください");return b;
 }
 void loadPreview(boolean resetEnd){
  final long previewAt;try{previewAt=resetEnd?0:seconds(start);}catch(Exception error){message(error.getMessage());return;}
  final Uri uri=video;final int token=work();final AtomicBoolean stop=cancelled;message("録画を読み込んでいます…");worker.execute(()->{MediaMetadataRetriever r=new MediaMetadataRetriever();Bitmap bitmap=null;try{if(stop.get())return;r.setDataSource(this,uri);long length=metadata(r,MediaMetadataRetriever.METADATA_KEY_DURATION);if(length<1000||length>7L*24*3600*1000)throw new IllegalArgumentException("録画の長さを読み取れません。1秒以上の動画を選んでください");bitmap=decode(r,Math.min(length-1,previewAt));final Bitmap ready=bitmap;bitmap=null;main.post(()->{if(!live(token)){ready.recycle();return;}if(preview!=null)preview.recycle();preview=ready;durationMs=length;file.setText("選択済み："+String.format(Locale.JAPAN,"%.1f秒 / %d×%d（解析用）",length/1000.0,ready.getWidth(),ready.getHeight()));if(resetEnd||end.getText().toString().isEmpty())end.setText(String.format(Locale.US,"%.3f",length/1000.0));busy(false);message("対象アプリのウインドウだけを囲んでください。ステータスバー・別アプリ・操作パネルは外すと安定します。");});}catch(Throwable e){failed(token,e);}finally{if(bitmap!=null)bitmap.recycle();try{r.release();}catch(Exception ignored){}}});
 }
 void failed(int token,Throwable error){final String detail=error instanceof OutOfMemoryError?"メモリ不足です。短い範囲・低い解像度の録画で再試行してください":error.getMessage()==null?"録画を読み込めませんでした":error.getMessage();main.post(()->{if(!live(token))return;busy(false);message(detail);});}
 void updateArea(){cropInfo.setText("範囲："+percent(crop)+(exclude==null?" / 除外なし":" / 除外："+percent(exclude)));}
 String percent(float[] a){return String.format(Locale.JAPAN,"%.0f,%.0f → %.0f,%.0f%%",a[0]*100,a[1]*100,a[2]*100,a[3]*100);}
 void area(boolean exclusion){
  if(busy)return;if(preview==null){message("先に録画ファイルを選択してください");return;}
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);text(box,exclusion?"判定に使わないパネルをドラッグで囲んでください。範囲は録画全体に対する位置です。":"対象アプリの内容ウインドウをドラッグで囲んでください。実行時に取得するウインドウと同じ範囲にします。",14);
  ImageBox view=new ImageBox(preview,true);view.rect=(exclusion?exclude:crop)==null?null:(exclusion?exclude:crop).clone();box.addView(view,new LinearLayout.LayoutParams(-1,dp(360)));
  if(exclusion)button(box,"除外しない",()->{view.rect=null;view.invalidate();});
  AlertDialog dialog=new UiStrings.DialogBuilder(this).setTitle(exclusion?"除外範囲":"アプリの範囲").setView(box).setPositiveButton("範囲を保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{float[] a=view.rect;if(a!=null&&(a[2]-a[0]<.02f||a[3]-a[1]<.02f)){message("範囲が小さすぎます");return;}if(!exclusion&&a==null)return;if(exclusion)exclude=a==null?null:a.clone();else crop=a.clone();model=null;review.removeAllViews();updateArea();dialog.dismiss();}));dialog.show();
 }
 static long seconds(EditText field){try{double v=Double.parseDouble(field.getText().toString());if(!Double.isFinite(v)||v<0||v>7*24*3600)throw new Exception();return Math.round(v*1000);}catch(Exception e){throw new IllegalArgumentException("開始・終了を秒で指定してください");}}
 static ObservationLearner.Frame frame(Bitmap image,float[] crop,float[] exclude){
  int w=image.getWidth(),h=image.getHeight(),left=Math.max(0,Math.min(w-1,(int)(crop[0]*w))),top=Math.max(0,Math.min(h-1,(int)(crop[1]*h))),right=Math.max(left+1,Math.min(w,Math.round(crop[2]*w))),bottom=Math.max(top+1,Math.min(h,Math.round(crop[3]*h)));RegionSampler.Pixels pixels=BitmapPixels.of(image);
  ObservationLearner.Frame f=ObservationLearner.Frame.capture(right-left,bottom-top,(x,y)->pixels.get(left+x,top+y));if(exclude!=null){float[] a={Math.max(0,(exclude[0]-crop[0])/(crop[2]-crop[0])),Math.max(0,(exclude[1]-crop[1])/(crop[3]-crop[1])),Math.min(1,(exclude[2]-crop[0])/(crop[2]-crop[0])),Math.min(1,(exclude[3]-crop[1])/(crop[3]-crop[1]))};if(a[2]>a[0]&&a[3]>a[1])f.excluded=a;}return f;
 }
 void analyze(){
  try{if(video==null||preview==null)throw new IllegalArgumentException("先に録画を選択してください");final long from=seconds(start),to=Math.min(durationMs,seconds(end));long requested=spacing.getSelectedItemPosition()==0?500:spacing.getSelectedItemPosition()==1?1000:2000;final long interval=RecordedAutomatonGenerator.interval(from,to,requested);if((to-from)/interval<3)throw new IllegalArgumentException("解析範囲を長くするか、取得間隔を短くしてください");
   final Uri uri=video;final float[] bounds=crop.clone(),excluded=exclude==null?null:exclude.clone();importTarget=targetKeys.get(targets.getSelectedItemPosition());repairMode=purpose.getSelectedItemPosition()==1;AppPhases existing=new AppPhases(this);existing.loadTarget(importTarget);if(!existing.loadIssue.isEmpty())throw new IllegalArgumentException(existing.loadIssue);if(repairMode&&existing.engine.phases.isEmpty())throw new IllegalArgumentException("改修する設定がありません。新規生成を選んでください");repairBase=existing.engine.runtimeCopy();baseline=getSharedPreferences("appPhases",0).getString(importTarget,"{}");model=null;review.removeAllViews();final int token=work();final AtomicBoolean stop=cancelled;message("解析中…（実際の取得間隔 "+interval+"ms / 最大600フレーム）");
   worker.execute(()->{MediaMetadataRetriever r=new MediaMetadataRetriever();try{if(stop.get())return;r.setDataSource(this,uri);RecordedAutomatonGenerator generator=new RecordedAutomatonGenerator();int count=0;for(long at=from;at<to;at+=interval){if(stop.get())return;Bitmap image=decode(r,at);try{if(stop.get())return;generator.add(at,frame(image,bounds,excluded));}finally{image.recycle();}final int n=++count;if(n%10==0)main.post(()->{if(live(token))message("解析中："+n+"フレーム / 最大600");});}if(stop.get())return;final RecordedAutomatonGenerator.Model parsed=repairMode?generator.finishForRepair():generator.finish();final int inherited=repairMode?RecordedAutomatonRepair.inheritReviewed(repairBase,parsed):0;final RecordedAutomatonGenerator.Result checked=parsed.states.size()<2||parsed.edges.isEmpty()?null:RecordedAutomatonGenerator.compile(parsed);main.post(()->{if(!live(token))return;model=parsed;busy(false);message((repairMode?RecordedAutomatonRepair.analyze(repairBase,parsed).report()+"\n前回の登録操作から"+inherited+"状態を再利用\n":"")+(checked==null?"操作前後の遷移がない録画です。停止している表示を診断し、既存設定は保持します。":checked.explanation));showReview();});}catch(Throwable error){failed(token,error);}finally{try{r.release();}catch(Exception ignored){}}});
  }catch(Exception error){message(error.getMessage());}
 }
 void previewRepair(){
  if(busy||model==null||repairBase==null)return;final RecordedAutomatonGenerator.Model parsed=model;final PhaseEngine before=repairBase;final boolean fromRecording=repairTestStart.isChecked();final int token=work();message("修正候補を作り、同じ録画の状態・分岐で再検証しています…");worker.execute(()->{try{RecordedAutomatonRepair.Result result=RecordedAutomatonRepair.compile(before,parsed,fromRecording);main.post(()->{if(!live(token))return;busy(false);message(result.explanation);});}catch(Throwable error){failed(token,error);}});
 }
 void rememberVideo(RecordedAutomatonRepair.Result repaired){
  try{SharedPreferences prefs=getSharedPreferences("recordedRepair",0);JSONObject last=new JSONObject();last.put("uri",video.toString());last.put("start",start.getText().toString());last.put("end",end.getText().toString());last.put("spacing",spacing.getSelectedItemPosition());last.put("crop",AppLogic.array(crop));if(exclude!=null)last.put("exclude",AppLogic.array(exclude));prefs.edit().putString("last:"+importTarget,last.toString()).apply();if(repaired!=null){JSONArray history=new JSONArray(prefs.getString("history:"+importTarget,"[]"));JSONObject row=new JSONObject();row.put("at",System.currentTimeMillis());row.put("unknownBefore",repaired.before.unknown);row.put("unknownAfter",repaired.after.unknown);row.put("ambiguousBefore",repaired.before.ambiguous);row.put("ambiguousAfter",repaired.after.ambiguous);row.put("addedStates",repaired.addedStates);row.put("changedRules",repaired.changedRules);history.put(row);JSONArray kept=new JSONArray();for(int i=Math.max(0,history.length()-20);i<history.length();i++)kept.put(history.getJSONObject(i));prefs.edit().putString("history:"+importTarget,kept.toString()).apply();}}
  catch(Exception unavailable){message("設定は保存済みです。録画の再読み込みにはファイルを選び直してください。");}
 }
 void restoreLastVideo(){
  if(busy)return;String target=targetKeys.get(targets.getSelectedItemPosition());try{JSONObject last=new JSONObject(getSharedPreferences("recordedRepair",0).getString("last:"+target,"{}"));video=Uri.parse(last.getString("uri"));start.setText(last.getString("start"));end.setText(last.getString("end"));spacing.setSelection(last.optInt("spacing",1));crop=AppLogic.read(last.getJSONArray("crop"));exclude=last.has("exclude")?AppLogic.read(last.getJSONArray("exclude")):null;purpose.setSelection(1);model=null;review.removeAllViews();updateArea();loadPreview(false);}
  catch(Exception missing){message("このゲームの前回録画が見つかりません。録画ファイルを選択してください。");}
 }
 void restoreRepair(){
  if(busy)return;final String target=targetKeys.get(targets.getSelectedItemPosition());new UiStrings.DialogBuilder(this).setTitle("改修前の設定へ戻す").setMessage(label(target)+"の直前の退避設定を復元します。").setPositiveButton("戻す",(d,n)->{try{TapService service=TapService.instance;if(service!=null)service.stopAll("録画改修を戻す");AppPhases owner=new AppPhases(this);owner.loadTarget(target);owner.restoreCheckpointEdited();if(service!=null&&service.pilot.phases.storage().equals(target))service.pilot.phases.loadTarget(target);invalidateResult();message("改修前の設定を復元しました。同じ録画か別の録画で再解析できます。");}catch(Exception error){message(error.getMessage());}}).setNegativeButton("取消",null).show();
 }
 void showReview(){
  review.removeAllViews();if(model==null)return;text(review,"操作の確認："+label(importTarget),19);text(review,"各状態でタップ位置・スワイプ・操作なしを登録できます。同じ画面から異なる操作が必要な場合は、生成後の編集で判定条件を追加してください。",14);
  for(RecordedAutomatonGenerator.State s:model.states){int[] destinations=model.destinations(s.index);String kind=destinations.length==0?"録画終端":s.action==RecordedAutomatonGenerator.UNREVIEWED?"操作未登録":s.action==PhaseEngine.WAIT?"操作なし":s.action==PhaseEngine.CENTER?"タップ登録済み":"スワイプ登録済み";Button b=button(review,"状態"+(s.index+1)+"："+kind+" / 出現"+s.visits+"回 / "+String.format(Locale.JAPAN,"%.1f秒",s.firstMs/1000.0),()->annotate(s));b.setEnabled(destinations.length>0);}
  if(repairMode){text(review,RecordedAutomatonRepair.analyze(repairBase,model).report(),14);button(review,"修正候補を作成して録画で再検証",()->previewRepair());}button(review,repairMode?"修正候補を適用":"生成した設定を保存",()->confirmSave());
 }
 void annotate(RecordedAutomatonGenerator.State state){
  if(busy)return;int[] destinations=model.destinations(state.index);if(destinations.length==0)return;
  Bitmap bitmap=Bitmap.createBitmap(state.frame.pixels,state.frame.width,state.frame.height,Bitmap.Config.ARGB_8888);LinearLayout box=new LinearLayout(this);box.setOrientation(1);text(box,"タップは画像を1回押す。スワイプは画像上で指を動かす。位置はアプリ範囲内の割合として保存します。",14);Spinner kind=spinner(box,"操作",new String[]{"タップ","スワイプ","操作なしで表示を待つ"});kind.setSelection(state.action==PhaseEngine.WAIT?2:state.action==PhaseEngine.CURVE?1:0);
  ImageBox image=new ImageBox(bitmap,false);image.path=state.path.clone();image.swipe=kind.getSelectedItemPosition()==1;box.addView(image,new LinearLayout.LayoutParams(-1,dp(300)));kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int i,long id){image.swipe=i==1;image.setEnabled(i!=2);}});
  String[] labels=new String[destinations.length];int selected=0;for(int i=0;i<labels.length;i++){labels[i]="録画状態"+(destinations[i]+1);if(state.destination==destinations[i])selected=i;}Spinner next=spinner(box,"この操作後に確認する表示",labels);next.setSelection(selected);EditText timing=field(box,"操作時間（ms）",Long.toString(state.duration));
  ScrollView scroll=new ScrollView(this);scroll.addView(box);AlertDialog dialog=new UiStrings.DialogBuilder(this).setTitle("状態"+(state.index+1)+"の操作を登録").setView(scroll).setPositiveButton("操作を登録",null).setNegativeButton("取消",null).create();dialog.setOnDismissListener(d->bitmap.recycle());dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int action=kind.getSelectedItemPosition()==2?PhaseEngine.WAIT:kind.getSelectedItemPosition()==1?PhaseEngine.CURVE:PhaseEngine.CENTER;float[] path=image.path;if(action==PhaseEngine.CENTER&&path.length>2)path=Arrays.copyOf(path,2);long duration=action==PhaseEngine.WAIT?300:Long.parseLong(timing.getText().toString());model.annotate(state.index,action,path,duration,destinations[next.getSelectedItemPosition()]);dialog.dismiss();showReview();message("状態"+(state.index+1)+"の操作を登録しました");}catch(Exception error){UiStrings.toast(this,error.getMessage()==null?"入力を確認してください":error.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();
 }
 void confirmSave(){
  if(busy||model==null)return;if(repairMode&&model.edges.isEmpty()){message("状態の診断はできました。遷移先のない操作は生成しません。操作後まで含む次の録画を選んで改修を続けてください。");return;}int pending=0;for(RecordedAutomatonGenerator.State s:model.states)if(model.destinations(s.index).length>0&&s.action==RecordedAutomatonGenerator.UNREVIEWED)pending++;
  if(repairMode){if(pending>0){message(pending+"状態の操作位置が未確認です。操作を登録してから再検証・適用してください");return;}new UiStrings.DialogBuilder(this).setTitle("録画の修正候補を適用").setMessage("既存の状態・分岐を保持し、録画で確認した部分を追加・修正します。現在の設定は退避します。\n保存後は同じ録画で再検証したり、次の録画で改修を続けられます。保存だけでは入力を開始しません。").setPositiveButton("適用する",(d,n)->save()).setNegativeButton("確認を続ける",null).show();return;}new UiStrings.DialogBuilder(this).setTitle(pending>0?"録画の下書きを保存":"生成した設定を保存").setMessage(label(importTarget)+"のオートマトンを置き換えます。現在の設定は「かんたん編集 → 退避から復元」で戻せるように保存します。\n"+(pending>0?pending+"状態の操作が未登録です。編集して確認を完了するまで自動開始できません。":"保存後、対象アプリの開始画面を開いて手動で開始してください。")+"\n保存だけでは操作は始まりません。").setPositiveButton("保存する",(d,n)->save()).setNegativeButton("編集を続ける",null).show();
 }
 void save(){
  final RecordedAutomatonGenerator.Model parsed=model;final String target=importTarget,before=baseline;final boolean fromRecording=repairTestStart.isChecked();final int token=work();message("判定領域と遷移を検査しています…");worker.execute(()->{try{final RecordedAutomatonGenerator.Result result=RecordedAutomatonGenerator.compile(parsed);final RecordedAutomatonRepair.Result repaired=repairMode?RecordedAutomatonRepair.compile(repairBase,parsed,fromRecording):null;if(repaired!=null&&repaired.after.ambiguous>repaired.before.ambiguous)throw new IllegalStateException("修正後に状態の曖昧さが増えました。操作条件を確認して再検証してください");main.post(()->{if(!live(token))return;try{TapService service=TapService.instance;if(service!=null)service.stopAll("録画の設定を保存");SharedPreferences prefs=getSharedPreferences("appPhases",0);if(!before.equals(prefs.getString(target,"{}")))throw new IllegalStateException("解析中に対象の設定が変わりました。現在の設定を確認してから再解析してください");AppPhases owner=new AppPhases(this);owner.loadTarget(target);if(!owner.loadIssue.isEmpty())throw new IllegalStateException(owner.loadIssue);owner.checkpointEdited();owner.mutate(()->{owner.engine=repaired==null?result.engine:repaired.engine;if(repaired==null)owner.customized=true;owner.enabled=!owner.engine.recordingReviewRequired;owner.prompt=repaired==null?"端末内の録画から生成 / 操作は画像上で確認 / 未知・曖昧な表示は保留":"端末内の録画で比較・改修・再検証 / 既存の状態・分岐を保持 / 未確認操作は保留";});baseline=prefs.getString(target,"{}");rememberVideo(repaired);if(service!=null&&service.pilot.phases.storage().equals(target))service.pilot.phases.loadTarget(target);busy(false);message("保存しました："+label(target)+"\n"+(repaired==null?result.explanation:repaired.explanation)+"\n対象アプリを開き、かんたん編集で確認できます。");review.removeAllViews();button(review,"同じ録画で改修後を再検証",()->{purpose.setSelection(1);main.postDelayed(()->analyze(),100);});button(review,"次の録画を選んで改修を続ける",()->{purpose.setSelection(1);pick.performClick();});button(review,"対象を選択して操作パネルへ戻る",()->{TapService s=TapService.instance;if(s==null){message("ユーザー補助を有効にしてから対象アプリを開いてください");return;}if(!GameProfiles.select(s,target)){message("対象ゲームの選択を保存できませんでした");return;}s.pilot.phases.loadTarget(target);finish();s.showPanel();UiStrings.toast(s,"対象アプリと開始画面を開いてから、かんたん編集を押してください",Toast.LENGTH_LONG).show();});}catch(Exception error){busy(false);message(error.getMessage());}});}catch(Throwable error){failed(token,error);}});
 }
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(video!=null)b.putString("uri",video.toString());b.putString("target",targetKeys.get(targets.getSelectedItemPosition()));b.putString("start",start.getText().toString());b.putString("end",end.getText().toString());b.putInt("spacing",spacing.getSelectedItemPosition());b.putBoolean("repair",purpose.getSelectedItemPosition()==1);b.putBoolean("repairTestStart",repairTestStart.isChecked());b.putFloatArray("crop",crop);b.putFloatArray("exclude",exclude);}
 @Override protected void onResume(){super.onResume();TapService s=TapService.instance;if(s!=null){s.stopAll("録画から生成");s.pilot.recordingImportActive=true;s.pilot.hide(true);}}
 @Override protected void onDestroy(){cancelWork();worker.shutdownNow();if(preview!=null){preview.recycle();preview=null;}TapService s=TapService.instance;if(s!=null){s.pilot.recordingImportActive=false;s.pilot.hide(false);}super.onDestroy();}

 final class ImageBox extends View {
  final Bitmap bitmap;final boolean rectangle;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF display=new RectF();
  float[] rect,path=new float[0];boolean swipe;float downX,downY;final ArrayList<Float> points=new ArrayList<>();
  ImageBox(Bitmap bitmap,boolean rectangle){super(RecordedImportActivity.this);this.bitmap=bitmap;this.rectangle=rectangle;setMinimumHeight(dp(200));setBackgroundColor(0xffeeeeee);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;display.set((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2);paint.setStyle(Paint.Style.FILL);c.drawBitmap(bitmap,null,display,paint);paint.setColor(0xffe94f19);paint.setStrokeWidth(dp(3));paint.setStyle(Paint.Style.STROKE);if(rect!=null)c.drawRect(display.left+rect[0]*width,display.top+rect[1]*height,display.left+rect[2]*width,display.top+rect[3]*height,paint);if(path.length>=2){for(int i=2;i<path.length;i+=2)c.drawLine(display.left+path[i-2]*width,display.top+path[i-1]*height,display.left+path[i]*width,display.top+path[i+1]*height,paint);c.drawCircle(display.left+path[0]*width,display.top+path[1]*height,dp(8),paint);}}
  @Override public boolean onTouchEvent(android.view.MotionEvent event){if(!isEnabled()||display.isEmpty())return false;float x=Math.max(0,Math.min(.9999f,(event.getX()-display.left)/display.width())),y=Math.max(0,Math.min(.9999f,(event.getY()-display.top)/display.height()));int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(!display.contains(event.getX(),event.getY()))return false;getParent().requestDisallowInterceptTouchEvent(true);downX=x;downY=y;points.clear();points.add(x);points.add(y);path=new float[]{x,y};}
   if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){if(rectangle)rect=new float[]{Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y)};else if(swipe){if(points.size()<256){points.add(x);points.add(y);}else{points.set(points.size()-2,x);points.set(points.size()-1,y);}path=new float[points.size()];for(int i=0;i<path.length;i++)path[i]=points.get(i);}else path=new float[]{downX,downY};}
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
  }
 }
}
