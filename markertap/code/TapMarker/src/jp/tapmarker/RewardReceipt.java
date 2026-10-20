package jp.tapmarker;
import java.util.Map;

/** Input acceptance and later reward-display evidence are separate facts. Per runner only. */
final class RewardReceipt {
 boolean pending;long sentAt=-1,lastFrame=-1;int proof,frames;String status="";
 void reset(){pending=false;sentAt=lastFrame=-1;proof=frames=0;status="";}
 boolean started(PhaseEngine.Watch w,long now){
  if(w.action!=PhaseEngine.CENTER||!w.id.equals(PoipoiPreset.P+"get-tap")&&!w.id.equals(PoipoiPreset.P+"offline-tap"))return false;
  pending=true;sentAt=now;lastFrame=-1;proof=frames=0;status="獲得操作の入力完了 / 報酬獲得は未確認";return true;
 }
 String observe(PhaseEngine e,Map<String,float[]> samples,long at,long now){
  if(!pending||at<=sentAt||at<=lastFrame||now<at||now-at>=1500)return "";lastFrame=at;
  int current=0;PhaseEngine.Watch reward=e.rule(PoipoiPreset.P+"check-reward");float[] raw=samples.get(PoipoiPreset.P+"reward"),list=samples.get(PoipoiMissionEvidence.STATUS);
  // A generic X/Close or a native one-bit dialog flag is not proof of a reward.
  if(reward!=null&&raw!=null&&raw.length==1536&&reward.distance(raw)<=reward.threshold)current=1;
  else if(list!=null&&list.length==1&&list[0]==2)current=2;
  if(current==0){proof=frames=0;return "";}
  frames=current==proof?frames+1:1;proof=current;if(frames<2)return "";
  pending=false;status=current==1?"獲得操作後の報酬画面を確認 / 加算額は未確認":"獲得操作後の一覧変化を確認 / 加算額は未確認";return status;
 }
}
