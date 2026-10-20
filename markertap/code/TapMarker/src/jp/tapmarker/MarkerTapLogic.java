package jp.tapmarker;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Display;

/** Decision-side adapter: publishes or revokes bounded commands, never schedules individual taps. */
final class MarkerTapLogic {
 private final TapService service;
 MarkerTapLogic(TapService service){this.service=service;}
 void start(int generation){cycle(generation);}
 void hold(String reason){service.tapper().pause(reason);service.holdInput(reason);}
 void cycle(int generation){if(!service.running||generation!=service.generation)return;update();service.h.postDelayed(()->cycle(generation),100);}
 void update(){
  if(!service.running)return;
  AutoPilot logic=service.pilot;
  if(logic==null){hold("自律の準備待ち");return;}
  boolean content=logic.autonomousMain();
  if(!content&&service.marks.isEmpty()){hold("マーカーがありません");return;}
  if(service.guardDialog()){hold("保護対象のダイアログを確認中");return;}
  if(!service.currentApp.equals(logic.packageNow())){hold("対象アプリの表示待ち");return;}
  if(!logic.canTapUsagi()){hold(service.inputHold);return;}
  if(logic.actionPending||logic.active&&logic.phases.enabled&&logic.phases.engine.awaitingBranch()){hold("ボタン操作・画面分岐の確認待ち");return;}
  Rect bounds=logic.splitLauncher.bounds(service.currentApp);if(bounds==null){hold("対象ウインドウの表示待ち");return;}
  float[] points;
  if(content){points=logic.phaseMainPoint();if(points==null||!bounds.contains((int)points[0],(int)points[1])){hold("画面から連打対象を再探索中");return;}}
  else{points=new float[service.marks.size()*2];int i=0;for(TapService.Mark mark:service.marks){float[] point=logic.phaseMarkerPoint(mark.p.x+service.size/2,mark.p.y+service.size/2);if(point==null||!bounds.contains((int)point[0],(int)point[1])){hold("対象画面とマーカー位置の再確認待ち");return;}points[i++]=point[0];points[i++]=point[1];}}
  long until=logic.active?logic.phaseSampleTakenAt+ContinuousTapPolicy.MAX_FRAME_AGE_MS:Long.MAX_VALUE;
  String context=service.generation+":"+(logic.active?logic.phases.engine.current+":"+logic.phases.engine.epoch:"manual");
  try{int[] target={bounds.left,bounds.top,bounds.right,bounds.bottom};service.tapper().update(new Tapper.Command(service.currentApp,Display.DEFAULT_DISPLAY,context,target,service.mainTapPoints(points,target,Display.DEFAULT_DISPLAY),service.tapPressMillis(1),service.effectiveInterval(),until,true,0,service.contacts().spread));}
  catch(IllegalArgumentException invalid){hold("対象画面と入力位置が一致しません");}
 }
}
