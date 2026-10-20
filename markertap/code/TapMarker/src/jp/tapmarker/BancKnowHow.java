package jp.tapmarker;
import java.util.*;

/** Transfer verified procedures and timing, without images, coordinates or target states. */
public final class BancKnowHow {
 public static final Set<String> KINDS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("close","resume","chest","claim","next")));
 public static final class Row {
  public String kind,source;public int attempts,successes,failures;public double rewardSum,latencySum,latencySquared;
  Row copy(){Row r=new Row();r.kind=kind;r.source=source;r.attempts=attempts;r.successes=successes;r.failures=failures;r.rewardSum=rewardSum;r.latencySum=latencySum;r.latencySquared=latencySquared;return r;}
 }
 public static final class Advice {
  public int attempts,successes,failures,sources;public double rewardSum,latencySum,latencySquared;
  public double reliability(){return successes/(double)Math.max(1,attempts);}
  public boolean verified(){return successes>=2&&reliability()>=.66&&rewardSum/Math.max(1,attempts)>=.05;}
  public double bias(){if(attempts<2)return 0;return Math.max(-.06,Math.min(.06,(reliability()-.5)*.08+rewardSum/attempts*.06));}
  public long resultWait(){if(!verified())return BancAutonomy.RESULT_MS;double mean=latencySum/Math.max(1,successes),variance=Math.max(0,latencySquared/Math.max(1,successes)-mean*mean);return Math.max(BancAutonomy.RESULT_MS,Math.min(30000,(long)Math.ceil(mean+2*Math.sqrt(variance)+3000)));}
  public long retryWait(){return failures>=2&&reliability()<.4?12000:8000;}
 }
 private final LinkedHashMap<String,Row> rows=new LinkedHashMap<>();public int revision;
 public synchronized void observe(String source,String kind,boolean verified,long resultLatency){
  if(source==null||source.isEmpty()||!KINDS.contains(kind))return;String key=source+"|"+kind;Row row=rows.get(key);if(row==null){row=new Row();row.kind=kind;row.source=source;rows.put(key,row);}if(row.attempts>=1000000)return;
  row.attempts++;if(verified){row.successes++;double latency=Math.max(0,Math.min(60000,resultLatency));row.latencySum+=latency;row.latencySquared+=latency*latency;row.rewardSum+=.2;}else{row.failures++;row.rewardSum-=.5;}
  revision++;while(rows.size()>160)rows.remove(rows.keySet().iterator().next());
 }
 public synchronized Advice forGame(String target,String kind){
  Advice advice=new Advice();if(!KINDS.contains(kind))return advice;
  for(Row row:rows.values())if(row.kind.equals(kind)&&!row.source.equals(target)){advice.attempts+=row.attempts;advice.successes+=row.successes;advice.failures+=row.failures;advice.rewardSum+=row.rewardSum;advice.latencySum+=row.latencySum;advice.latencySquared+=row.latencySquared;advice.sources++;}return advice;
 }
 public synchronized List<Row> snapshot(){ArrayList<Row> out=new ArrayList<>();for(Row row:rows.values())out.add(row.copy());return out;}
 public synchronized void restore(List<Row> values){rows.clear();if(values==null)return;for(Row row:values){if(rows.size()>=160)break;if(row==null||!KINDS.contains(row.kind)||row.source==null||row.source.isEmpty()||row.source.length()>200||row.attempts<0||row.attempts>1000000||row.successes<0||row.failures<0||row.successes+row.failures!=row.attempts||!Double.isFinite(row.rewardSum)||!Double.isFinite(row.latencySum)||!Double.isFinite(row.latencySquared)||row.latencySum<0||row.latencySquared<0||row.latencySum>row.successes*60000d||row.latencySquared>row.successes*60000d*60000d)continue;rows.put(row.source+"|"+row.kind,row.copy());}}
 public static String procedure(String kind){if(!KINDS.contains(kind))return "";return "対象アプリのボタンを特定 → 入力直前に再確認 → "+label(kind)+" → 独立した目印を2枚で確認 → 登録手順へ戻る";}
 public static String label(String kind){return "close".equals(kind)?"閉じる":"resume".equals(kind)?"ゲーム再開":"chest".equals(kind)?"宝箱を開ける":"claim".equals(kind)?"報酬受け取り":"next".equals(kind)?"次のステージ":"";}
 public synchronized String report(String target){ArrayList<String> usable=new ArrayList<>();for(String kind:new String[]{"close","resume","chest","claim","next"})if(forGame(target,kind).verified())usable.add(label(kind));return "他ゲームのノウハウ："+(usable.isEmpty()?"成功・失敗と待機時間を蓄積中":String.join("、",usable))+"\n画面・座標・完了条件は実行先で確認します。";}
}
