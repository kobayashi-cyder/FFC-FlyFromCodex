package jp.tapmarker;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Searchable native forms for phone, resized Android windows, and mouse input. */
final class EasyEditorUi {
 final PhaseUi ui;
 final TapService s;
 String query="";final Map<String,String> queries=new HashMap<>();
 EasyEditorUi(PhaseUi ui){this.ui=ui;s=ui.s;}
 LinearLayout body(){LinearLayout l=new LinearLayout(ui.context());l.setOrientation(1);int pad=(int)(12*s.getResources().getDisplayMetrics().density);l.setPadding(pad,pad,pad,pad);return l;}
 Button button(LinearLayout l,String title,Runnable run){Button b=new UiStrings.DisplayButton(ui.context());b.setText(title);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->run.run());l.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
 void close(){if(ui.pilot.activeDialog!=null)ui.pilot.activeDialog.dismiss();}
 void open(Runnable action){close();s.h.postDelayed(action,100);}
 void show(){
  if(!ui.ready())return;
  final AppPhases owner=ui.pilot.phases;
  final PhaseEngine model=owner.engine;
  ui.graphMode=false;ui.easyMode=true;
  LinearLayout l=body();
  TextView target=new UiStrings.DisplayText(ui.context());target.setText("対象："+s.appLabel(owner.storage())+"\n"+model.phases.size()+"状態 / "+model.regions.size()+"領域\n行を選ぶと編集できます。変更は「保存」で確定します。");l.addView(target);
  if(model.recordingReviewRequired)button(l,"録画の操作確認を完了",()->open(()->completeRecording(owner,model)));
  LinearLayout nav=new LinearLayout(ui.context());l.addView(nav);
  rowButton(nav,"図で編集",()->open(()->{ui.easyMode=false;ui.graph.show();}));
  rowButton(nav,"領域一覧",()->open(()->ui.regionList()));
  LinearLayout add=new LinearLayout(ui.context());l.addView(add);
  rowButton(add,"＋ 状態",()->open(()->addState(owner,model)));
  rowButton(add,"＋ 遷移",()->open(()->ui.promptPicker()));
  LinearLayout history=new LinearLayout(ui.context());l.addView(history);
  Button undo=rowButton(history,"元に戻す",()->restore(owner,model,false));
  Button redo=rowButton(history,"やり直す",()->restore(owner,model,true));
  undo.setEnabled(owner.editHistory.canUndo(owner.storage()));redo.setEnabled(owner.editHistory.canRedo(owner.storage()));
  LinearLayout backup=new LinearLayout(ui.context());l.addView(backup);
  rowButton(backup,"設定を退避",()->checkpoint(owner,model));
  rowButton(backup,"退避から復元",()->restoreCheckpoint(owner,model)).setEnabled(owner.hasEditorCheckpoint());
  EditText search=new EditText(ui.context());search.setSingleLine(true);UiStrings.hint(search,"状態名・遷移名・領域名を検索");search.setText(queries.getOrDefault(owner.storage(),""));l.addView(search);
  TextView count=new UiStrings.DisplayText(ui.context());l.addView(count);
  LinearLayout rows=new LinearLayout(ui.context());rows.setOrientation(1);
  ScrollView scroll=new ScrollView(ui.context());scroll.addView(rows);l.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  Runnable refresh=()->{
   rows.removeAllViews();String needle=search.getText().toString().trim().toLowerCase(Locale.ROOT);query=search.getText().toString();queries.put(owner.storage(),query);int found=0,shown=0;
   for(PhaseEngine.Phase p:model.phases.values()){
    boolean state=needle.isEmpty()||p.name.toLowerCase(Locale.ROOT).contains(needle);
    if(state){found++;if(shown++<120)button(rows,"状態："+p.name+(p.id.equals(model.initial)?"  開始":"")+(p.terminal?"  終了":"")+" / "+p.watches.size()+"遷移",()->{if(ui.edit(owner,model,()->AutomatonGraph.requirePhase(model,p)))open(()->ui.phaseEdit(p));});}
    for(PhaseEngine.Watch w:p.watches)if(needle.isEmpty()||AutomatonEdits.searchText(model,p,w).contains(needle)){
     found++;if(shown++<120)button(rows,p.name+" → "+ui.watchLabel(w)+((w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)&&w.reference==null?"\n一致画像が未記録":""),()->{if(ui.edit(owner,model,()->AutomatonGraph.requirePhase(model,p)))open(()->ui.watchEdit(p,w));});
    }
   }
   count.setText(found==0?"一致する設定がありません":found+"件"+(found>120?" / 先頭120件を表示  検索で絞り込んでください":""));
  };
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int c,int a){}public void onTextChanged(CharSequence v,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});
  AlertDialog dialog=new UiStrings.DialogBuilder(ui.context()).setTitle("かんたん編集").setView(l).setPositiveButton("設定チェック",(d,n)->open(()->check(owner,model))).setNegativeButton("閉じる",(d,n)->ui.easyMode=false).create();
  dialog.setOnCancelListener(d->ui.easyMode=false);ui.show(dialog);GraphUi.DisplayMetricsHelper.size(dialog,s);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);refresh.run();
 }
 Button rowButton(LinearLayout row,String title,Runnable run){Button b=new UiStrings.DisplayButton(ui.context());b.setText(title);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->run.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
 void choose(){
  s.stop("オートマトンの編集");
  ArrayList<String> targets=new ArrayList<>(),labels=new ArrayList<>();
  ArrayList<Boolean> virtual=new ArrayList<>();
  String ordinary=ui.pilot.packageNow();
  if(s.validApp(ordinary))addTargets(targets,labels,virtual,ordinary,false," 通常画面");
  for(VirtualWindows.Window w:ui.pilot.virtualWindows.windows)if(w.launched&&!w.closed&&w.frameAt>0)addTargets(targets,labels,virtual,w.pkg,true," 仮想画面 #"+w.id);
  if(targets.isEmpty()){ui.pilot.note("編集するアプリを通常画面か仮想画面で開いてください");return;}
  java.util.function.IntConsumer select=index->{
   String target=targets.get(index),app=GameTarget.packageName(target);
   ui.pilot.editingVirtualPackage=virtual.get(index)?app:"";
   if(!GameProfiles.select(s,target)){ui.pilot.note("編集対象を保存できませんでした");return;}
   if(!virtual.get(index))s.switchProfile(app);
   ui.pilot.phases.loadTarget(target);
   s.h.postDelayed(()->show(),100);
  };
  if(targets.size()==1){select.accept(0);return;}
  ui.show(new UiStrings.DialogBuilder(ui.context()).setTitle("編集するアプリとゲームを選択").setItems(labels.toArray(new String[0]),(d,index)->select.accept(index)).setNegativeButton("取消",null).create());
 }
 void addTargets(ArrayList<String> targets,ArrayList<String> labels,ArrayList<Boolean> virtual,String app,boolean isVirtual,String suffix){
  String[] keys=GameTarget.USAPO.equals(app)?new String[]{GameTarget.FARM,GameTarget.BALLOON,GameTarget.USAPO}:new String[]{app};
  for(String key:keys){targets.add(key);virtual.add(isVirtual);labels.add((key.equals(GameTarget.USAPO)?"うさポ共通画面":s.appLabel(key))+suffix);}
 }
 void addState(AppPhases owner,PhaseEngine model){ui.nameDialog("状態名","状態"+(model.phases.size()+1),name->{ui.requireOwner(owner,model);if(owner.phaseNamed(name)!=null)throw new IllegalArgumentException("同じ名前があります");final PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=UUID.randomUUID().toString();p.name=name;owner.mutate(()->{model.phases.put(p.id,p);if(model.initial.isEmpty())model.initial=model.current=p.id;});s.h.postDelayed(()->ui.phaseEdit(p),100);});}
 void restore(AppPhases owner,PhaseEngine model,boolean redo){if(!ui.edit(owner,model,()->{if(!owner.undoEdited(redo))throw new IllegalArgumentException("戻せる編集はありません");}))return;ui.pilot.note(redo?"編集をやり直しました":"編集を元に戻しました");open(()->show());}
 void checkpoint(AppPhases owner,PhaseEngine model){
  open(()->ui.show(new UiStrings.DialogBuilder(ui.context()).setTitle("現在の設定を退避").setMessage(s.appLabel(owner.storage())+"の状態・遷移・領域を保存します。前回の退避設定は置き換わります。マーカー配置とデイリー完了登録は別の設定です。").setPositiveButton("退避する",(d,n)->{if(ui.edit(owner,model,()->owner.checkpointEdited()))ui.pilot.note("編集前の設定を退避しました");s.h.postDelayed(()->show(),100);}).setNegativeButton("取消",(d,n)->s.h.postDelayed(()->show(),100)).create()));
 }
 void restoreCheckpoint(AppPhases owner,PhaseEngine model){
  open(()->ui.show(new UiStrings.DialogBuilder(ui.context()).setTitle("退避した設定へ戻す").setMessage(s.appLabel(owner.storage())+"の現在の状態・遷移・領域を退避時点へ戻します。復元操作も「元に戻す」で取り消せます。").setPositiveButton("復元する",(d,n)->{if(ui.edit(owner,model,()->owner.restoreCheckpointEdited()))ui.pilot.note("退避した設定を復元しました");s.h.postDelayed(()->show(),100);}).setNegativeButton("取消",(d,n)->s.h.postDelayed(()->show(),100)).create()));
 }
 void completeRecording(AppPhases owner,PhaseEngine model){
  if(!ui.edit(owner,model,()->{}))return;
  PhaseEngine check=model.runtimeCopy();check.current=check.initial;String issue=check.validate();
  if(!issue.isEmpty()){ui.pilot.note(issue);open(()->show());return;}
  ui.show(new UiStrings.DialogBuilder(ui.context()).setTitle("録画の操作確認を完了").setMessage("操作未登録の遷移を確認し、必要なタップ・スワイプを編集しましたか。操作を登録しなかった遷移は、次の表示を待つだけです。設定チェックは画面上での成功を保証しません。完了後も、自動開始は手動で行います。").setPositiveButton("確認済みとして保存",(d,n)->{if(ui.edit(owner,model,()->owner.mutate(()->{model.recordingReviewRequired=false;owner.enabled=true;})))ui.pilot.note("録画の操作確認を完了しました");s.h.postDelayed(()->show(),100);}).setNegativeButton("編集を続ける",(d,n)->s.h.postDelayed(()->show(),100)).create());
 }
 void check(AppPhases owner,PhaseEngine model){
  if(!ui.edit(owner,model,()->{}))return;
  StringBuilder report=new StringBuilder(AutomatonGraph.report(model));
  int missing=0;for(PhaseEngine.Phase p:model.phases.values())for(PhaseEngine.Watch w:p.watches){if((w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)&&w.reference==null){report.append("\n一致画像未記録：").append(p.name).append(" / ").append(ui.ruleName(w));missing++;}for(int i=0;i<w.guardIds.length;i++)if(i>=w.guardReferences.length||w.guardReferences[i]==null||w.guardReferences[i].length==0){report.append("\n補助画像未記録：").append(p.name).append(" / ").append(ui.ruleName(w));missing++;}}
  PhaseEngine check=model.runtimeCopy();check.current=check.initial;String issue=check.validate();if(!issue.isEmpty())report.append("\n").append(issue);if(missing==0&&issue.isEmpty())report.append("\n開始前の設定チェックに通りました。対象アプリで動作を確認してください。");
  TextView text=new UiStrings.DisplayText(ui.context());text.setText(report.toString());text.setPadding(24,16,24,16);ScrollView scroll=new ScrollView(ui.context());scroll.addView(text);ui.show(new UiStrings.DialogBuilder(ui.context()).setTitle("設定チェック").setView(scroll).setPositiveButton("編集一覧に戻る",(d,n)->s.h.postDelayed(()->show(),100)).create());
 }
}
