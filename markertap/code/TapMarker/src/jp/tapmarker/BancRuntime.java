package jp.tapmarker;

import android.content.*;
import android.graphics.Rect;
import android.os.SystemClock;
import java.util.*;
import org.json.*;

/** Per-game/per-graph memory and Android adapter shared by single and virtual runners. */
final class BancRuntime {
 final TapService s;final String pkg,scope,key;final BancAutonomy core;
 static BancKnowHow sharedKnowledge;static int savedSharedRevision;
 BancAutonomy.Ui ui=BancAutonomy.Ui.empty();int savedRevision;String saveError="";
 BancRuntime(TapService s,String pkg,String scope,PhaseEngine engine){this.s=s;this.pkg=pkg;this.scope=scope;this.key="memory:"+scope+":"+BancGraphIdentity.of(scope,engine);this.core=new BancAutonomy(knowledge(s),scope);load();core.reset(SystemClock.uptimeMillis());}
 boolean enabled(){return s.getSharedPreferences("bancAutonomy",0).getBoolean("enabled",true);}
 boolean pending(){return enabled()&&core.pending();}
 void cancel(){core.cancel();save();}
 void progress(long at){core.normalProgress(at);}
 List<PhaseEngine.Region> regions(PhaseEngine e,List<PhaseEngine.Region> normal,long now){
  ArrayList<PhaseEngine.Region> result=new ArrayList<>(normal);
  if(enabled()&&core.scanDue(e,now))for(PhaseEngine.Region r:BancAutonomy.recoveryRegions(e))if(!result.contains(r))result.add(r);
  return result;
 }
 /** Normal samples retain their dedicated layout adapters. Fill missing scan anchors exactly. */
 void sample(PhaseEngine e,Map<String,float[]> samples,int w,int h,RegionSampler.Pixels pixels,long at,Rect imageScope){
  if(!enabled())return;float[] bounds=new float[]{0,0,1,1};
  if(imageScope!=null&&imageScope.left>=0&&imageScope.top>=0&&imageScope.right<=w&&imageScope.bottom<=h&&!imageScope.isEmpty())bounds=new float[]{imageScope.left/(float)w,imageScope.top/(float)h,imageScope.right/(float)w,imageScope.bottom/(float)h};
  samples.put(BancAutonomy.OVERVIEW,RegionSampler.sample(w,h,bounds,pixels));
  if(!core.sampleScan(e,at))return;
  for(PhaseEngine.Region r:BancAutonomy.recoveryRegions(e))if(!samples.containsKey(r.id)&&BancAutonomy.validBounds(r.bounds)){float[] adaptive=samples.get(RecordedRepairSampling.ADAPTIVE);if(r.windowLocal&&adaptive!=null&&adaptive[0]!=1&&e.activeRegions().contains(r))continue;float[] scope=samples.get(RecordedRepairSampling.WINDOW);if(r.windowLocal&&!e.windowRelative&&scope!=null){final int left=(int)scope[0],top=(int)scope[1];samples.put(r.id,RegionSampler.sample((int)scope[2],(int)scope[3],r.bounds,(x,y)->pixels.get(left+x,top+y)));}else samples.put(r.id,RegionSampler.sample(w,h,r.bounds,pixels));}
  samples.put(BancAutonomy.SCAN,new float[]{1});
 }
 BancAutonomy.Decision review(PhaseEngine e,Map<String,float[]> samples,int display,Rect rect,long at,Set<String> blocked){
  if(!enabled()){core.cancel();return BancAutonomy.Decision.none();}
  core.transferEnabled=s.getSharedPreferences("bancAutonomy",0).getBoolean("shareKnowHow",true);long now=SystemClock.uptimeMillis();ui=core.scanDue(e,now)?BancUiControls.read(s,pkg,display,rect):BancAutonomy.Ui.empty();
  if(AdExitPolicy.adState(e.current)&&!AdExitPolicy.gameVisible(e,samples)){ArrayList<BancAutonomy.Control> exits=new ArrayList<>();for(BancAutonomy.Control c:ui.controls)if(c.kind.equals("close"))exits.add(c);ui=new BancAutonomy.Ui(ui.context,exits);}
  {ArrayList<BancAutonomy.Control> permitted=new ArrayList<>();for(BancAutonomy.Control c:ui.controls)if(CoinNoAdPolicy.allowsUi(pkg,c.label,ui.context)&&!AdCloseAutomaton.ownsNamed(e,c.label,c.bounds,samples))permitted.add(c);ui=new BancAutonomy.Ui(ui.context,permitted);}
  BancAutonomy.Decision d=core.review(e,samples,ui,at,now,blocked);save();return d;
 }
 boolean activate(BancAutonomy.Decision d,PhaseEngine e,Map<String,float[]> samples,int display,Rect rect){
  if(!enabled()||!core.begin(d,e,ui,samples,SystemClock.uptimeMillis()))return false;
  if(BancUiControls.activate(s,pkg,display,rect,ui,d.control)){core.sent(SystemClock.uptimeMillis());return true;}
  core.fail(SystemClock.uptimeMillis(),"ボタンが入力直前に変化・入力拒否 / 再確認");save();return false;
 }
 void load(){
  try{JSONObject memory=new JSONObject(s.getSharedPreferences("bancAutonomy",0).getString(key,"{}"));if(memory.optInt("schema",0)!=1)return;
   JSONArray factors=memory.optJSONArray("gains");if(factors!=null&&factors.length()==16){double[] values=new double[16];for(int i=0;i<16;i++)values[i]=factors.getDouble(i);core.selector.restore(values);}
   JSONArray skills=memory.optJSONArray("skills");if(skills!=null)for(int i=0;i<Math.min(128,skills.length());i++){JSONObject row=skills.getJSONObject(i);BancAutonomy.Skill skill=new BancAutonomy.Skill();skill.id=row.getString("id");skill.context=row.getString("context");skill.kind=row.getString("kind");skill.label=row.getString("label");skill.destination=row.optString("destination","");skill.before=AppLogic.read(row.getJSONArray("before"));skill.bounds=AppLogic.read(row.getJSONArray("bounds"));skill.attempts=row.getInt("attempts");skill.successes=row.getInt("successes");skill.failures=row.getInt("failures");skill.rewardSum=row.getDouble("reward");boolean valid=skill.before.length==768&&BancAutonomy.validBounds(skill.bounds)&&skill.attempts>=0&&skill.successes>=0&&skill.failures>=0&&skill.successes+skill.failures==skill.attempts&&Double.isFinite(skill.rewardSum)&&BancAutonomy.safeKind(skill.label,skill.context).equals(skill.kind);for(float value:skill.before)valid&=Float.isFinite(value)&&value>=0&&value<=1;if(valid)core.skills.put(skill.id,skill);}
  }catch(Exception damaged){saveError="学習記憶の読込に失敗 / 登録済み設定で継続";core.skills.clear();}
 }
 void save(){saveKnowledge(s);if(savedRevision==core.revision)return;
  try{JSONObject memory=new JSONObject();memory.put("schema",1);memory.put("scope",scope);memory.put("source",BancSelector.SOURCE);JSONArray factors=new JSONArray();for(double factor:core.selector.gains())factors.put(factor);memory.put("gains",factors);JSONArray skills=new JSONArray();
   for(BancAutonomy.Skill skill:core.skills.values()){JSONObject row=new JSONObject();row.put("id",skill.id);row.put("context",skill.context);row.put("kind",skill.kind);row.put("label",skill.label);row.put("destination",skill.destination==null?"":skill.destination);row.put("before",AppLogic.array(skill.before));row.put("bounds",AppLogic.array(skill.bounds));row.put("attempts",skill.attempts);row.put("successes",skill.successes);row.put("failures",skill.failures);row.put("reward",skill.rewardSum);skills.put(row);}memory.put("skills",skills);
   s.getSharedPreferences("bancAutonomy",0).edit().putString(key,memory.toString()).apply();savedRevision=core.revision;saveError="";
  }catch(Exception failure){saveError="学習記憶の保存に失敗 / 今回の観察は継続";}
 }
 String report(){return core.report()+(saveError.isEmpty()?"":"\n"+saveError);}
 static synchronized BancKnowHow knowledge(TapService s){
  if(sharedKnowledge!=null)return sharedKnowledge;sharedKnowledge=new BancKnowHow();
  try{JSONObject memory=new JSONObject(s.getSharedPreferences("bancAutonomy",0).getString("sharedKnowHowV1","{}"));if(memory.optInt("schema",0)!=1)return sharedKnowledge;JSONArray rows=memory.optJSONArray("procedures");List<BancKnowHow.Row> values=new ArrayList<>();if(rows!=null)for(int i=0;i<Math.min(160,rows.length());i++){JSONObject item=rows.getJSONObject(i);BancKnowHow.Row row=new BancKnowHow.Row();row.kind=item.getString("kind");row.source=item.getString("sourceGame");row.attempts=item.getInt("attempts");row.successes=item.getInt("successes");row.failures=item.getInt("failures");row.rewardSum=item.getDouble("rewardSum");row.latencySum=item.getDouble("resultLatencySum");row.latencySquared=item.getDouble("resultLatencySquared");values.add(row);}sharedKnowledge.restore(values);
  }catch(Exception damaged){sharedKnowledge.restore(Collections.emptyList());}return sharedKnowledge;
 }
 static synchronized void saveKnowledge(TapService s){
  if(sharedKnowledge==null||savedSharedRevision==sharedKnowledge.revision)return;
  try{JSONObject memory=new JSONObject();memory.put("schema",1);JSONArray rows=new JSONArray();for(BancKnowHow.Row row:sharedKnowledge.snapshot()){JSONObject item=new JSONObject();item.put("kind",row.kind);item.put("sourceGame",row.source);item.put("attempts",row.attempts);item.put("successes",row.successes);item.put("failures",row.failures);item.put("rewardSum",row.rewardSum);item.put("resultLatencySum",row.latencySum);item.put("resultLatencySquared",row.latencySquared);rows.put(item);}memory.put("procedures",rows);s.getSharedPreferences("bancAutonomy",0).edit().putString("sharedKnowHowV1",memory.toString()).apply();savedSharedRevision=sharedKnowledge.revision;
  }catch(Exception failure){/* Keep the live pool and retry on the next observation. */}
 }
}
