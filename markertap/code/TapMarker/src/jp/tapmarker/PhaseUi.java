package jp.tapmarker;
import android.app.*;import android.view.*;import android.widget.*;import java.util.*;
class PhaseUi {
 GraphUi graph;EasyEditorUi easy;boolean graphMode=false,easyMode=false;final AutoPilot pilot;final TapService s;PhaseUi(AutoPilot p){pilot=p;s=p.s;graph=new GraphUi(this);easy=new EasyEditorUi(this);}
 android.view.ContextThemeWrapper context(){return new android.view.ContextThemeWrapper(s,android.R.style.Theme_Material_Light_Dialog_Alert);}
 void returnToEditor(){s.h.postDelayed(()->{if(graphMode)graph.show();else if(easyMode)easy.show();},100);}
 void show(AlertDialog dialog){dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);pilot.activeDialog=dialog;dialog.show();}
 boolean ready(){s.stop();s.removeCapture();if(!pilot.editingVirtualPackage.isEmpty()&&pilot.virtualWindows.window(pilot.editingVirtualPackage)!=null){if(!pilot.phases.pkg.equals(pilot.editingVirtualPackage)||!pilot.phases.storage().equals(GameProfiles.key(s,pilot.editingVirtualPackage)))pilot.phases.load(pilot.editingVirtualPackage);return true;}if(!s.ensureProfile())return false;if(!pilot.phases.pkg.equals(s.currentApp)||!pilot.phases.storage().equals(GameProfiles.key(s,s.currentApp)))pilot.phases.load(s.currentApp);return true;}
 void requireOwner(AppPhases owner,PhaseEngine model){if(pilot.phases!=owner||owner.engine!=model||!owner.storage().equals(GameProfiles.key(s,owner.pkg)))throw new IllegalArgumentException("編集中のアプリ・設定が切り替わりました。対象アプリで開き直してください");}
 boolean edit(AppPhases owner,PhaseEngine model,Runnable change){try{requireOwner(owner,model);change.run();return true;}catch(RuntimeException failure){UiStrings.toast(s,failure.getMessage(),Toast.LENGTH_LONG).show();return false;}}

 interface Named {void apply(String name);}
 void nameDialog(String title,String old,Named apply){EditText input=new EditText(context());input.setText(old);AlertDialog dialog=new UiStrings.DialogBuilder(context()).setTitle(title).setView(input).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String name=input.getText().toString().trim();if(name.isEmpty()||name.contains("「")||name.contains("」")){input.setError("空欄と括弧を含む名前は使えません");return;}try{apply.apply(name);dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}}));show(dialog);}
 void addRegion(){if(!ready())return;final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;nameDialog("領域の名前","領域"+(pilot.phases.engine.regions.size()+1),name->{requireOwner(owner,model);if(owner.regionNamed(name)!=null)throw new IllegalArgumentException("同じ名前があります");pilot.phases.pendingRegionName=name;pilot.phases.pendingRegionId="";s.h.postDelayed(()->pilot.editor(false),150);});}
 void regionList(){if(!ready())return;ArrayList<PhaseEngine.Region> regions=new ArrayList<>(pilot.phases.engine.regions.values());String[] names=new String[regions.size()+1];for(int i=0;i<regions.size();i++)names[i]=regions.get(i).name;names[regions.size()]="＋ 領域追加";show(new UiStrings.DialogBuilder(context()).setTitle("このアプリの監視領域").setItems(names,(d,i)->{if(i==regions.size()){s.h.postDelayed(()->addRegion(),100);return;}regionEdit(regions.get(i));}).create());}
 void regionEdit(PhaseEngine.Region r){
  final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;AutomatonGraph.requireRegion(model,r);LinearLayout l=new LinearLayout(context());l.setOrientation(1);
  l.addView(s.button("この領域の中央をテストタップ",()->{if(edit(owner,model,()->AutomatonGraph.requireRegion(model,r))){pilot.activeDialog.dismiss();pilot.testRegion(r);}}));
  l.addView(s.button("範囲を描き直す",()->{if(edit(owner,model,()->{AutomatonGraph.requireRegion(model,r);owner.pendingRegionName=r.name;owner.pendingRegionId=r.id;})){pilot.activeDialog.dismiss();s.h.postDelayed(()->{if(edit(owner,model,()->{}))pilot.editor(false);},150);}}));
  l.addView(s.button("名前を変更",()->{pilot.activeDialog.dismiss();nameDialog("領域名",r.name,name->{requireOwner(owner,model);AutomatonGraph.requireRegion(model,r);PhaseEngine.Region other=owner.regionNamed(name);if(other!=null&&other!=r)throw new IllegalArgumentException("同じ名前があります");owner.mutate(()->r.name=name);});}));
  l.addView(s.button("この領域を削除",()->{if(edit(owner,model,()->{AutomatonGraph.requireRegion(model,r);for(PhaseEngine.Phase p:model.phases.values())for(PhaseEngine.Watch w:p.watches)if(w.regionId.equals(r.id)||Arrays.asList(w.guardIds).contains(r.id))throw new IllegalArgumentException("先にこの領域を使う矢印を削除してください");owner.mutate(()->model.regions.remove(r.id));}))pilot.activeDialog.dismiss();}));
  show(new UiStrings.DialogBuilder(context()).setTitle(r.name).setView(l).setNegativeButton("閉じる",null).create());
 }
 void phaseList(){if(!ready())return;final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;ArrayList<PhaseEngine.Phase> phases=new ArrayList<>(pilot.phases.engine.phases.values());String[] names=new String[phases.size()+1];for(int i=0;i<phases.size();i++)names[i]=phases.get(i).name;names[phases.size()]="＋ 状態追加";show(new UiStrings.DialogBuilder(context()).setTitle("状態とルール").setItems(names,(d,i)->{if(i==phases.size()){nameDialog("状態名","状態"+(phases.size()+1),name->{requireOwner(owner,model);if(owner.phaseNamed(name)!=null)throw new IllegalArgumentException("同じ名前があります");PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=UUID.randomUUID().toString();p.name=name;pilot.phases.engine.phases.put(p.id,p);if(pilot.phases.engine.current.isEmpty()){pilot.phases.engine.current=p.id;pilot.phases.engine.initial=p.id;}pilot.phases.saveEdited();s.h.postDelayed(()->phaseEdit(p),100);});}else phaseEdit(phases.get(i));}).create());}
 void switchPhase(){boolean live=pilot.active&&pilot.phases.enabled;if(live){if(pilot.actionPending){pilot.note("操作完了後に状態切替を押してください");return;}}else if(!ready())return;final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;ArrayList<PhaseEngine.Phase> phases=new ArrayList<>(pilot.phases.engine.phases.values());if(phases.isEmpty()){phaseList();return;}String[] names=new String[phases.size()];for(int i=0;i<phases.size();i++)names[i]=phases.get(i).name;show(new UiStrings.DialogBuilder(context()).setTitle("開始状態を選択").setItems(names,(d,i)->{if(!edit(owner,model,()->AutomatonGraph.requirePhase(model,phases.get(i))))return;if(!live)pilot.phases.engine.initial=phases.get(i).id;pilot.phases.engine.enter(phases.get(i).id,android.os.SystemClock.uptimeMillis());if(live)pilot.phases.save();else pilot.phases.saveEdited();if(live){pilot.applyPhaseMarkers();pilot.phases.record("手動切替 → "+phases.get(i).name);}pilot.note((live?"自律継続 / 状態：":"開始状態：")+phases.get(i).name);}).create());}
 void promptPicker(){
  if(!ready())return;final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;
  ArrayList<PhaseEngine.Phase> phases=new ArrayList<>(pilot.phases.engine.phases.values());
  if(phases.isEmpty()){nameDialog("最初の状態名","待機中",name->{requireOwner(owner,model);PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=UUID.randomUUID().toString();p.name=name;pilot.phases.engine.phases.put(p.id,p);pilot.phases.engine.current=p.id;pilot.phases.engine.initial=p.id;pilot.phases.saveEdited();s.h.postDelayed(()->promptPicker(),150);});return;}
  LinearLayout l=new LinearLayout(context());l.setOrientation(1);l.setPadding(24,16,24,16);
  TextView help=new UiStrings.DisplayText(context());help.setText("文章の入力は不要です。定型指示と有効な状態を選び、次の画面で領域と遷移先を指定してください。");l.addView(help);
  Spinner template=spinner(l,"定型指示",new String[]{"この表示があれば、その領域の中央をタップ","領域が変わったら、その中央をタップ","この表示があれば、マーカーを連打","この表示があれば、描いた曲線をなぞる","時間が経ったら、次の状態へ移る"},0);
  String[] names=new String[phases.size()];int selected=0;for(int i=0;i<phases.size();i++){names[i]=phases.get(i).name;if(phases.get(i).id.equals(pilot.phases.engine.current))selected=i;}
  Spinner phase=spinner(l,"この状態中だけ有効",names,selected);
  AlertDialog dialog=new UiStrings.DialogBuilder(context()).setTitle("指示を選択").setView(l).setPositiveButton("領域・操作を設定",(d,which)->{PhaseEngine.Watch w=new PhaseEngine.Watch();int t=template.getSelectedItemPosition();w.condition=t==1?PhaseEngine.CHANGE:t==4?PhaseEngine.TIMEOUT:PhaseEngine.MATCH;w.action=t==2?PhaseEngine.MARKERS:t==3?PhaseEngine.CURVE:t==4?PhaseEngine.WAIT:PhaseEngine.CENTER;s.h.postDelayed(()->{if(edit(owner,model,()->{}))watchEdit(phases.get(phase.getSelectedItemPosition()),null,w);},100);}).setNegativeButton("取消",null).create();show(dialog);
 }
 String conditionName(int n){String[] names={"変化","一致","時間","表示消失"};return n>=0&&n<names.length?names[n]:"無効な条件";}
 String actionName(int n){String[] names={"中央タップ","マーカー","曲線","戻る","停止","遷移","アプリ復帰"};return n>=0&&n<names.length?names[n]:"無効な操作";}

 String ruleName(PhaseEngine.Watch w){return w.name.isEmpty()?"遷移":w.name;}
 String watchLabel(PhaseEngine.Watch w){PhaseEngine.Region r=pilot.phases.engine.regions.get(w.regionId);PhaseEngine.Phase next=pilot.phases.engine.phases.get(w.nextPhase);return (w.name.isEmpty()?"":w.name+" / ")+(r==null?"領域なし":r.name)+" / "+conditionName(w.condition)+" → "+actionName(w.action)+(next==null?"":" → "+next.name);}
 void phaseEdit(PhaseEngine.Phase p){
  final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;
  if(!edit(owner,model,()->AutomatonGraph.requirePhase(model,p)))return;
  LinearLayout l=new LinearLayout(context());l.setOrientation(1);l.setPadding(20,12,20,12);
  EditText name=field(l,"状態名",p.name);
  CheckBox terminal=new UiStrings.DisplayCheck(context());terminal.setText("終了状態  入ったら自律と連打を停止");terminal.setChecked(p.terminal);l.addView(terminal);
  CheckBox markers=new UiStrings.DisplayCheck(context());markers.setText("この状態中は通常連打（プリセットは画面から認識）");markers.setChecked(p.markersDuring);l.addView(markers);
  TextView note=new UiStrings.DisplayText(context());note.setText("状態名とチェック項目は「保存」で確定します。遷移を開く前に状態の変更を保存してください。");l.addView(note);
  l.addView(s.button("＋ 遷移追加",()->{if(edit(owner,model,()->AutomatonGraph.requirePhase(model,p))){pilot.activeDialog.dismiss();watchEdit(p,null);}}));
  for(PhaseEngine.Watch w:p.watches)l.addView(s.button(watchLabel(w),()->{if(edit(owner,model,()->AutomatonGraph.requirePhase(model,p))){pilot.activeDialog.dismiss();watchEdit(p,w);}}));
  if(AutomatonEdits.canDuplicate(p))l.addView(s.button("保存済みの状態を複製",()->{pilot.activeDialog.dismiss();nameDialog("複製した状態名",p.name+" コピー",newName->{requireOwner(owner,model);final PhaseEngine.Phase[] copy=new PhaseEngine.Phase[1];owner.mutate(()->copy[0]=AutomatonEdits.duplicatePhase(model,p,newName));s.h.postDelayed(()->phaseEdit(copy[0]),100);});}));
  l.addView(s.button("この状態を削除",()->{pilot.activeDialog.dismiss();show(new UiStrings.DialogBuilder(context()).setTitle("状態を削除").setMessage(p.name+"と、この状態から出る遷移を削除します。この状態へ入る遷移がある場合は、先に遷移先を変えてください。").setPositiveButton("削除する",(d,n)->{if(edit(owner,model,()->owner.removePhase(p)))returnToEditor();}).setNegativeButton("取消",(d,n)->s.h.postDelayed(()->phaseEdit(p),100)).create());}));
  TextView error=new UiStrings.DisplayText(context());l.addView(error);ScrollView scroll=new ScrollView(context());scroll.addView(l);
  AlertDialog dialog=new UiStrings.DialogBuilder(context()).setTitle("状態を編集").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",(d,n)->returnToEditor()).create();
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{requireOwner(owner,model);AutomatonGraph.requirePhase(model,p);String title=name.getText().toString().trim();if(title.isEmpty())throw new IllegalArgumentException("状態名を入力してください");PhaseEngine.Phase other=owner.phaseNamed(title);if(other!=null&&other!=p)throw new IllegalArgumentException("同じ状態名があります");owner.mutate(()->{p.name=title;p.terminal=terminal.isChecked();p.markersDuring=markers.isChecked();});dialog.dismiss();pilot.note("状態を保存しました");returnToEditor();}catch(Exception failure){error.setText(failure.getMessage());}}));
  show(dialog);
 }
 Spinner spinner(LinearLayout l,String title,String[] items,int selected){TextView label=new UiStrings.DisplayText(context());label.setText(title);l.addView(label);Spinner spinner=new Spinner(context());spinner.setAdapter(new UiStrings.StringAdapter(context(),android.R.layout.simple_spinner_dropdown_item,items));spinner.setSelection(Math.max(0,selected));l.addView(spinner);return spinner;}
 EditText field(LinearLayout l,String label,String value){TextView text=new UiStrings.DisplayText(context());text.setText(label);l.addView(text);EditText edit=new EditText(context());edit.setText(value);l.addView(edit);return edit;}
 void bindMarkers(AppPhases owner,PhaseEngine.Watch w){
  try{org.json.JSONArray records=new org.json.JSONArray(s.getSharedPreferences("markers",0).getString("profile:"+owner.storage(),"[]"));if(records.length()==0)throw new IllegalArgumentException("対象アプリに先にマーカーを配置してください");
   w.markerIds=new String[records.length()];w.markerPoints=owner.engine.windowRelative?new float[records.length()*2]:new float[0];
   int[] size=pilot.displaySize();android.content.SharedPreferences prefs=s.getSharedPreferences("markers",0);float width=prefs.getInt("profileWidth:"+owner.storage(),size[0]),height=prefs.getInt("profileHeight:"+owner.storage(),size[1]);boolean virtual=owner.pkg.equals(pilot.editingVirtualPackage)&&pilot.virtualWindows.window(owner.pkg)!=null;android.graphics.Rect bounds=virtual?null:pilot.splitLauncher.bounds(owner.pkg);
   for(int i=0;i<records.length();i++){org.json.JSONObject marker=records.getJSONObject(i);w.markerIds[i]=marker.getString("id");if(owner.engine.windowRelative){float x=(float)marker.getDouble("x"),y=(float)marker.getDouble("y");if(!virtual&&(bounds==null||bounds.isEmpty()))throw new IllegalArgumentException("対象アプリを開いてください");w.markerPoints[i*2]=virtual?x/width:(x-bounds.left)/bounds.width();w.markerPoints[i*2+1]=virtual?y/height:(y-bounds.top)/bounds.height();}}
   if(owner.engine.windowRelative&&!ObservationLearner.validPoints(w.markerPoints))throw new IllegalArgumentException("対象ウインドウ内にマーカーを配置してください");
  }catch(org.json.JSONException invalid){throw new IllegalArgumentException("対象アプリのマーカーを読み込めませんでした",invalid);}
 }

 interface WatchForm {PhaseEngine.Watch build();}
 void watchEdit(PhaseEngine.Phase p,PhaseEngine.Watch old){watchEdit(p,old,old==null?new PhaseEngine.Watch():old);}
 void watchEdit(PhaseEngine.Phase p,PhaseEngine.Watch old,PhaseEngine.Watch original){
  final AppPhases owner=pilot.phases;final PhaseEngine model=owner.engine;
  if(!edit(owner,model,()->{AutomatonGraph.requirePhase(model,p);if(old!=null&&!p.watches.contains(old))throw new IllegalArgumentException("この遷移は更新されています");}))return;
  final PhaseEngine.Watch source=AutomatonEdits.draft(original);
  ArrayList<PhaseEngine.Region> regions=new ArrayList<>(model.regions.values());ArrayList<PhaseEngine.Phase> phases=new ArrayList<>(model.phases.values());
  String[] regionNames=new String[regions.size()+1+(source.regionId.isEmpty()||model.regions.containsKey(source.regionId)?0:1)];
  String[] phaseNames=new String[phases.size()+1+(source.nextPhase.isEmpty()||model.phases.containsKey(source.nextPhase)?0:1)];
  regionNames[0]="領域なし  時間条件のみ";phaseNames[0]="同じ状態に留まる";int ri=0,ni=0;
  for(int i=0;i<regions.size();i++){regionNames[i+1]=regions.get(i).name;if(regions.get(i).id.equals(source.regionId))ri=i+1;}
  for(int i=0;i<phases.size();i++){phaseNames[i+1]=phases.get(i).name;if(phases.get(i).id.equals(source.nextPhase))ni=i+1;}
  if(regionNames.length>regions.size()+1){ri=regionNames.length-1;regionNames[ri]="未登録の領域："+source.regionId;}
  if(phaseNames.length>phases.size()+1){ni=phaseNames.length-1;phaseNames[ni]="未登録の遷移先："+source.nextPhase;}
  LinearLayout l=new LinearLayout(context());l.setOrientation(1);l.setPadding(20,12,20,12);
  TextView current=new UiStrings.DisplayText(context());current.setText("この遷移が有効な状態："+p.name+"\n保存前の変更は「取消」で破棄できます。");l.addView(current);
  EditText ruleTitle=field(l,"遷移名",source.name);
  Spinner region=spinner(l,"1 判定する領域",regionNames,ri);
  Spinner condition=spinner(l,"2 実行する条件",new String[]{"領域が変化","記録した状態と一致","状態開始から時間経過","記録した表示が消えた"},source.condition);
  Spinner action=spinner(l,"3 実行する操作",new String[]{"領域中央を1回タップ","登録マーカー群を連打","登録した曲線をなぞる","戻る","自律と連打を停止","操作せず遷移","登録アプリをインテントで開く"},source.action);
  Spinner next=spinner(l,"4 次の状態",phaseNames,ni);
  EditText wait=field(l,"5 操作後の待機 ms",String.valueOf(source.waitMs));wait.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  LinearLayout tapFields=new LinearLayout(context());tapFields.setOrientation(1);l.addView(tapFields);
  final Spinner tapPosition;final EditText tapX,tapY;
  if(model.windowRelative||RecordedRepairSampling.local(model,source)){tapPosition=spinner(tapFields,"タップ位置",new String[]{"相対位置を指定","条件領域の中央"},source.touchPoint.length==2?0:1);float[] point=source.touchPoint.length==2?source.touchPoint:new float[]{.5f,.5f};tapX=field(tapFields,"ウインドウ内 X %",String.valueOf(point[0]*100));tapY=field(tapFields,"ウインドウ内 Y %",String.valueOf(point[1]*100));tapX.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);tapY.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);}else{tapPosition=null;tapX=null;tapY=null;TextView position=new UiStrings.DisplayText(context());position.setText("相対位置のない設定は通常、判定領域の中央を使います。標準の専用操作では別の位置を使う場合があります。相対タップを使う場合は、対象ウインドウで学習した設定を編集してください。");tapFields.addView(position);}
  LinearLayout stabilityFields=new LinearLayout(context());stabilityFields.setOrientation(1);l.addView(stabilityFields);
  EditText stability=field(stabilityFields,"変化後に安定を待つ ms",String.valueOf(source.stableMs));
  LinearLayout timeFields=new LinearLayout(context());timeFields.setOrientation(1);l.addView(timeFields);
  EditText timeout=field(timeFields,"状態開始から何 ms 後に実行するか",String.valueOf(source.timeoutMs));
  TextView summary=new UiStrings.DisplayText(context());l.addView(summary);
  Button detail=new UiStrings.DisplayButton(context());detail.setText("詳細設定を開く");l.addView(detail);
  LinearLayout advanced=new LinearLayout(context());advanced.setOrientation(1);l.addView(advanced);advanced.setVisibility(View.GONE);
  detail.setOnClickListener(v->{boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);detail.setText(show?"詳細設定を閉じる":"詳細設定を開く");});
  EditText threshold=field(advanced,"変化と一致のしきい値",String.valueOf(source.threshold));
  EditText priority=field(advanced,"優先度  元の値を保持",String.valueOf(source.priority));
  TextView caution=new UiStrings.DisplayText(context());caution.setText("異なる操作が同時に成立すると、優先度に関係なく安全のため保留します。しきい値を上げるほど画像の違いを許容します。");advanced.addView(caution);
  CheckBox fallback=new UiStrings.DisplayCheck(context());fallback.setText("他の条件が成立しない時だけ実行");fallback.setChecked(source.fallback);advanced.addView(fallback);
  CheckBox keepCurve=new UiStrings.DisplayCheck(context());keepCurve.setText("登録済みの曲線を保持");keepCurve.setChecked(source.curve.length>=4);if(source.curve.length>=4)advanced.addView(keepCurve);
  CheckBox keepGuards=new UiStrings.DisplayCheck(context());keepGuards.setText("学習した補助領域もすべて一致させる");keepGuards.setChecked(source.guardIds.length>0);if(source.guardIds.length>0)advanced.addView(keepGuards);
  TextView error=new UiStrings.DisplayText(context());l.addView(error);
  WatchForm form=()->{
   requireOwner(owner,model);AutomatonGraph.requirePhase(model,p);
   if(region.getSelectedItemPosition()>regions.size()||next.getSelectedItemPosition()>phases.size())throw new IllegalArgumentException("未登録の領域と遷移先を選び直してください");
   PhaseEngine.Watch w=AutomatonEdits.draft(source);
   w.regionId=region.getSelectedItemPosition()==0?"":regions.get(region.getSelectedItemPosition()-1).id;
   w.condition=condition.getSelectedItemPosition();w.action=action.getSelectedItemPosition();w.nextPhase=next.getSelectedItemPosition()==0?"":phases.get(next.getSelectedItemPosition()-1).id;
   w.name=ruleTitle.getText().toString().trim();w.fallback=fallback.isChecked();w.priority=Integer.parseInt(priority.getText().toString().trim());w.stableMs=Long.parseLong(stability.getText().toString().trim());
   w.timeoutMs=Long.parseLong(timeout.getText().toString().trim());w.threshold=Float.parseFloat(threshold.getText().toString().trim());w.waitMs=Integer.parseInt(wait.getText().toString().trim());
   if(w.stableMs<0||w.stableMs>60000)throw new IllegalArgumentException("安定待ちは0から60000ms");
   if(w.timeoutMs<0||w.waitMs<0||w.waitMs>10000||!Float.isFinite(w.threshold)||w.threshold<0||w.threshold>.1f)throw new IllegalArgumentException("時間は0ms以上  待機は0から10000ms  しきい値は0から0.1");
   if((w.condition!=PhaseEngine.TIMEOUT||w.action==PhaseEngine.CENTER)&&w.regionId.isEmpty())throw new IllegalArgumentException("判定する領域を選んでください");
   if(!source.regionId.equals(w.regionId)){w.reference=null;w.pairedReference=false;}
   if(!keepGuards.isChecked()){w.guardIds=new String[0];w.guardReferences=new float[0][];}
   if(source.action!=w.action){w.touchPoint=new float[0];w.markerPoints=new float[0];w.markerIds=new String[0];w.relativeCurve=false;}
   if(w.action==PhaseEngine.CENTER&&tapPosition!=null){
    if(tapPosition.getSelectedItemPosition()==0){w.touchPoint=new float[]{Float.parseFloat(tapX.getText().toString().trim())/100,Float.parseFloat(tapY.getText().toString().trim())/100};if(!ObservationLearner.validPoints(w.touchPoint))throw new IllegalArgumentException("相対タップ位置は0以上100未満の%で指定してください");}else w.touchPoint=new float[0];
   }
   if(w.action==PhaseEngine.MARKERS&&w.markerIds.length==0)bindMarkers(owner,w);
   if(w.action==PhaseEngine.CURVE){
    if(source.action==PhaseEngine.CURVE&&source.curve.length>=4&&keepCurve.isChecked())w.relativeCurve=source.relativeCurve;
    else{w.curve=pilot.logic.curve.clone();w.duration=pilot.logic.duration;w.relativeCurve=false;if(w.curve.length<4)throw new IllegalArgumentException("先に曲線を描いてください");if(model.windowRelative||RecordedRepairSampling.local(model,w)){android.graphics.Rect viewport=pilot.editingBounds();if(viewport==null)throw new IllegalArgumentException("対象ウインドウを開いてください");for(int z=0;z<w.curve.length;z+=2){w.curve[z]=(w.curve[z]-viewport.left)/viewport.width();w.curve[z+1]=(w.curve[z+1]-viewport.top)/viewport.height();}if(!ObservationLearner.validPoints(w.curve))throw new IllegalArgumentException("対象ウインドウの内側で曲線を描いてください");w.relativeCurve=true;}}
   }
   return w;
  };
  if(AutomatonEdits.canDuplicate(source))l.addView(s.button("この遷移を複製して編集",()->{try{PhaseEngine.Watch copy=AutomatonEdits.duplicateWatch(form.build());pilot.activeDialog.dismiss();s.h.postDelayed(()->watchEdit(p,null,copy),100);}catch(Exception failure){error.setText(failure.getMessage());}}));
  if(old!=null)l.addView(s.button("この遷移を削除",()->{pilot.activeDialog.dismiss();show(new UiStrings.DialogBuilder(context()).setTitle("遷移を削除").setMessage(p.name+"の「"+ruleName(old)+"」を削除します。保存前の変更は破棄します。").setPositiveButton("削除する",(d,n)->{if(edit(owner,model,()->owner.removeWatch(p,old)))returnToEditor();}).setNegativeButton("取消",(d,n)->s.h.postDelayed(()->watchEdit(p,old),100)).create());}));
  ScrollView scroll=new ScrollView(context());scroll.addView(l);
  AlertDialog dialog=new UiStrings.DialogBuilder(context()).setTitle(p.name+"の遷移を編集").setView(scroll).setPositiveButton("保存",null).setNeutralButton("一致状態を記録",null).setNegativeButton("取消",(d,n)->returnToEditor()).create();
  dialog.setOnShowListener(d->{
   Runnable refresh=()->{
    int c=condition.getSelectedItemPosition();stabilityFields.setVisibility(c==PhaseEngine.CHANGE?View.VISIBLE:View.GONE);timeFields.setVisibility(c==PhaseEngine.TIMEOUT?View.VISIBLE:View.GONE);
    tapFields.setVisibility(action.getSelectedItemPosition()==PhaseEngine.CENTER?View.VISIBLE:View.GONE);
    if(tapX!=null){boolean percent=tapPosition.getSelectedItemPosition()==0;tapX.setEnabled(percent);tapY.setEnabled(percent);}
    dialog.getButton(-3).setVisibility(c==PhaseEngine.MATCH||c==PhaseEngine.DISAPPEAR?View.VISIBLE:View.GONE);
    summary.setText(p.name+"で "+region.getSelectedItem()+"が「"+condition.getSelectedItem()+"」なら "+action.getSelectedItem()+" → "+next.getSelectedItem());
   };
   AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int pos,long id){refresh.run();}public void onNothingSelected(AdapterView<?> a){}};
   region.setOnItemSelectedListener(listener);condition.setOnItemSelectedListener(listener);action.setOnItemSelectedListener(listener);next.setOnItemSelectedListener(listener);if(tapPosition!=null)tapPosition.setOnItemSelectedListener(listener);refresh.run();
   android.view.View.OnClickListener save=v->{
    try{PhaseEngine.Watch w=form.build();if(v==dialog.getButton(-3)&&w.regionId.isEmpty())throw new IllegalArgumentException("一致状態を記録する領域を選んでください");owner.replaceWatch(p,old,w);dialog.dismiss();pilot.note("遷移を保存しました");if(v==dialog.getButton(-3))s.h.postDelayed(()->{if(edit(owner,model,()->{}))pilot.captureMatch(w);},200);else returnToEditor();}
    catch(NumberFormatException failure){error.setText("数値欄を半角数字で入力してください");}catch(Exception failure){error.setText(failure.getMessage());}
   };
   dialog.getButton(-1).setOnClickListener(save);dialog.getButton(-3).setOnClickListener(save);
  });show(dialog);
 }
}
