package jp.tapmarker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only evaluation sidecar for authenticated v1.157 DEX. All legacy
 * automation and panel classes are kept byte-for-byte at class-code level.
 *
 * Award a cycle ONLY after (a) a distinct completed allowlisted center action
 * or the farm passive-receipt gate, (b) two independent fresh reward UI frames,
 * and (c) two fresh identical, post-UI, higher HUD balance screenshots.
 *
 * No OCR, no proof, stale frames, or ambiguous cross-game claim => no credit.
 */
final class RewardV158Observer {
  private static final Map<Object,Lane> LANES=
      Collections.synchronizedMap(new WeakHashMap<Object,Lane>());

  private static final class Lane {
    final RewardEvaluationConnectome evaluation=new RewardEvaluationConnectome();
    final FarmPassiveReward farm=new FarmPassiveReward();
    final KurashiruRewardProof proof=new KurashiruRewardProof();
    String pkg="",receipt="",lastCompleted="";
    boolean initialized,playable,reward;
    long lastSample=-1;
    long completedAt=-1;
  }
  private static Lane lane(Object owner){
    synchronized(LANES){
      Lane l=LANES.get(owner);
      if(l==null){l=new Lane();LANES.put(owner,l);}
      return l;
    }
  }
  private static Object get(Object obj,String name)throws Exception{
    if(obj==null)return null;
    for(Class<?> t=obj.getClass();t!=null;t=t.getSuperclass())
      try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
      catch(NoSuchFieldException ignored){}
    throw new NoSuchFieldException(name);
  }
  private static Object call(Object obj,String name,Object arg)throws Exception{
    if(obj==null)return null;
    for(Class<?> t=obj.getClass();t!=null;t=t.getSuperclass())
      for(Method m:t.getDeclaredMethods())if(m.getName().equals(name)
          &&m.getParameterTypes().length==1){
        m.setAccessible(true);return m.invoke(obj,arg);
      }
    throw new NoSuchMethodException(name);
  }
  private static String str(Object o){return o==null?"":String.valueOf(o);}
  private static boolean allowed(String pkg,String id){
    if(id==null||id.isEmpty()||pkg==null)return false;
    if(pkg.equals("com.kurashiru.kaitaku.usagi"))
      return id.equals("kaitaku-default-missions-get");
    if(pkg.equals("com.kurashiru.fue.usagi"))
      return id.equals("usagi-default-ゲットのみタップ")
          ||id.equals("usagi-default-通常獲得の中央");
    if(pkg.equals("com.kurashiru.coin.drop"))
      return id.equals("coin-default-get-tap");
    if(pkg.equals("com.kurashiru.shopping.usagi"))
      return id.equals("shopping-default-get-tap")
          ||id.equals("shopping-default-arrival-collect");
    if(pkg.equals("com.kurashiru.box.merge"))
      return id.equals("box-default-claim-tap");
    if(pkg.equals("com.kurashiru.poipoi.usagi"))
      return id.equals("poipoi-default-get-tap")
          ||id.equals("poipoi-default-offline-tap");
    return false;
  }
  private static String[] rewardIds(String pkg){
    if(pkg.equals("com.kurashiru.fue.usagi"))
      return new String[]{"usagi-default-獲得を確認","usagi-default-mission-open-reward"};
    if(pkg.equals("com.kurashiru.nouen.usagi"))
      return new String[]{"farm-default-reward-shown","farm-default-check-reward",
          "farm-default-reward-close-tap","farm-default-verify-reward"};
    if(pkg.equals("com.kurashiru.coin.drop"))
      return new String[]{"coin-default-claim-shown","coin-default-mission-reward-shown"};
    if(pkg.equals("com.kurashiru.shopping.usagi"))
      return new String[]{"shopping-default-claim-wait-reward",
          "shopping-default-missions-reward","shopping-default-arrival-wait-reward"};
    if(pkg.equals("com.kurashiru.box.merge"))
      return new String[]{"box-default-main-reward","box-default-check-reward",
          "box-default-ad-reward"};
    if(pkg.equals("com.kurashiru.poipoi.usagi"))
      return new String[]{"poipoi-default-missions-reward",
          "poipoi-default-claim-wait-reward"};
    return new String[0];
  }
  /** No generic close or mission tab can count as reward proof. */
  private static boolean rewardVisible(String pkg,Object model,Map<String,float[]> samples){
    if(pkg.equals("com.kurashiru.kaitaku.usagi")){
      float[] proof=samples.get("kaitaku-default-receipt-proof");
      return proof!=null&&proof.length==1&&proof[0]==1f;
    }
    for(String id:rewardIds(pkg)){
      try{
        Object w=call(model,"rule",id);
        if(w==null||((Number)get(w,"condition")).intValue()!=1)continue;
        float[] reference=(float[])get(w,"reference");
        if(reference==null||reference.length!=1536)continue;
        float[] seen=samples.get(str(get(w,"regionId")));
        if(seen==null||seen.length!=1536)continue;
        float distance=((Number)call(w,"distance",seen)).floatValue();
        float threshold=((Number)get(w,"threshold")).floatValue();
        if(Float.isFinite(distance)&&Float.isFinite(threshold)&&distance<=threshold)
          return true;
      }catch(Exception|LinkageError unavailable){}
    }
    return false;
  }
  static void observe(Object owner,String pkg,Object model,Map<String,float[]> samples,
       long capturedAt,long now,boolean positiveAd,boolean playable){
    if(owner==null||pkg==null||model==null||samples==null||capturedAt<=0||
        now<capturedAt||now-capturedAt>=1500)return;
    Lane l=lane(owner);
    synchronized(l){
      if(!pkg.equals(l.pkg)){
        invalidateLocked(l);l.evaluation.reset();l.pkg=pkg;
        l.initialized=false;l.lastCompleted="";l.lastSample=-1;
      }
      l.evaluation.observeAd(capturedAt,now,positiveAd);
      try{
        String completed=str(get(model,"lastCompletedRule"));
        if(!l.initialized){
          l.lastCompleted=completed;l.initialized=true;
        }else if(!completed.equals(l.lastCompleted)){
          l.lastCompleted=completed;
          // lastCompletedRule is written only by PhaseEngine.finish(), after
          // the action completion callback, not on a requested gesture.
          if(allowed(pkg,completed)){
            Object w=call(model,"rule",completed);
            if(w!=null&&((Number)get(w,"action")).intValue()==0){
              String id=pkg+":"+completed+":"+now;
              if(SharedPointClaimArbiter.begin(l.evaluation,id,now)){
                if(l.evaluation.claimSent(id,"pt",now)){
                  l.receipt=id;l.proof.reset();
                }else SharedPointClaimArbiter.release(l.evaluation,id);
              }
            }
          }
        }
      }catch(Exception|LinkageError unavailable){/* never invent a receipt */}
      if(capturedAt<=l.lastSample)return;
      l.lastSample=capturedAt;
      boolean reward=rewardVisible(pkg,model,samples);
      l.playable=playable&&!positiveAd;
      l.reward=reward;
      if(!l.receipt.isEmpty()&&l.proof.observe(reward,capturedAt,now))
        l.evaluation.claimAcknowledged(l.receipt,now);
    }
  }
  static void balance(Object owner,String pkg,long amount,long capturedAt,long now){
    if(owner==null||pkg==null||amount<0)return;
    Lane l=lane(owner);
    synchronized(l){
      if(!pkg.equals(l.pkg))return;
      if(pkg.equals("com.kurashiru.nouen.usagi")&&
          l.lastSample>0&&capturedAt>=l.lastSample&&
          capturedAt-l.lastSample<RewardEvaluationConnectome.FRESH_MS){
        String passive=l.farm.observe(l.playable,l.reward,amount,
            capturedAt,now,l.evaluation);
        if(!passive.isEmpty())l.receipt=passive;
      }
      if(!l.receipt.isEmpty() &&
          !SharedPointClaimArbiter.allowed(l.evaluation,l.receipt,now)){
        invalidateLocked(l);
      }
      if(l.evaluation.observeBalance("pt",amount,capturedAt,now)){
        SharedPointClaimArbiter.release(l.evaluation,l.receipt);
        l.receipt="";l.proof.reset();l.farm.completed();
      }
    }
  }
  static void invalidate(Object owner){
    if(owner==null)return;
    Lane l=lane(owner);
    synchronized(l){invalidateLocked(l);}
  }
  private static void invalidateLocked(Lane l){
    SharedPointClaimArbiter.release(l.evaluation,l.receipt);
    l.receipt="";l.evaluation.invalidatePending();
    l.proof.reset();l.farm.reset();
  }
  static long cycles(Object owner){
    if(owner==null)return 0;
    synchronized(LANES){
      Lane l=LANES.get(owner);
      if(l==null)return 0;
      synchronized(l){return l.evaluation.cycles();}
    }
  }
  static double score(Object owner){
    if(owner==null)return 0;
    synchronized(LANES){
      Lane l=LANES.get(owner);
      if(l==null)return 0;
      synchronized(l){return l.evaluation.score();}
    }
  }
  static boolean verifying(Object owner){
    if(owner==null)return false;
    synchronized(LANES){
      Lane l=LANES.get(owner);
      if(l==null)return false;
      synchronized(l){return !l.receipt.isEmpty()&&l.evaluation.awaitingProof();}
    }
  }
  static String report(Object owner){
    if(owner==null)return "未観測";
    synchronized(LANES){
      Lane l=LANES.get(owner);
      if(l==null)return "未観測";
      synchronized(l){return l.evaluation.report();}
    }
  }
  private RewardV158Observer(){}
}
