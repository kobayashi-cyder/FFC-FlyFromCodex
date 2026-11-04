package jp.tapmarker;
import java.util.Map;

/**
 * Allowlist of collection actions only. Opening missions, closing a dialog,
 * viewing a reward, and tapping a booster are never receipt claims.
 */
final class KurashiruClaimPolicy {
  static boolean isClaim(String pkg,String watchId,boolean acceptedCenter){
    if(!acceptedCenter||watchId==null||pkg==null)return false;
    if(pkg.equals(KaitakuPreset.APP))
      return watchId.equals("kaitaku-default-missions-get");
    if(pkg.equals("com.kurashiru.fue.usagi"))
      return watchId.equals("usagi-default-ゲットのみタップ")
        ||watchId.equals("usagi-default-通常獲得の中央");
    if(pkg.equals("com.kurashiru.coin.drop"))
      return watchId.equals("coin-default-get-tap");
    if(pkg.equals("com.kurashiru.shopping.usagi"))
      return watchId.equals("shopping-default-get-tap")
        ||watchId.equals("shopping-default-arrival-collect");
    if(pkg.equals("com.kurashiru.box.merge"))
      return watchId.equals("box-default-claim-tap");
    if(pkg.equals("com.kurashiru.poipoi.usagi"))
      return watchId.equals("poipoi-default-get-tap")
        ||watchId.equals("poipoi-default-offline-tap");
    // The farm and Kaitaku rules need receipt-specific evidence; never count
    // a generic gameplay tap, reward-close or visual point gain as a claim.
    return false;
  }

  /**
   * Positive, game-specific reward appearance. Exactly two distinct fresh
   * frames are required by KurashiruRewardProof before acknowledgment.
   */
  static boolean rewardVisible(String pkg,PhaseEngine engine,Map<String,float[]> samples){
    if(pkg==null||engine==null||samples==null)return false;
    // Kaitaku exposes text-scoped receipt proof, not a generic close button.
    if(pkg.equals(KaitakuPreset.APP)){
      float[] receipt=samples.get("kaitaku-default-receipt-proof");
      return receipt!=null&&receipt.length==1&&receipt[0]==1f;
    }
    String[] ids;
    if(pkg.equals("com.kurashiru.fue.usagi"))
      ids=new String[]{"usagi-default-獲得を確認","usagi-default-mission-open-reward"};
    else if(pkg.equals("com.kurashiru.nouen.usagi"))
      ids=new String[]{"farm-default-reward-shown","farm-default-check-reward",
          "farm-default-reward-close-tap","farm-default-verify-reward"};
    else if(pkg.equals("com.kurashiru.coin.drop"))
      ids=new String[]{"coin-default-claim-shown","coin-default-mission-reward-shown"};
    else if(pkg.equals("com.kurashiru.shopping.usagi"))
      ids=new String[]{"shopping-default-claim-wait-reward","shopping-default-missions-reward","shopping-default-arrival-wait-reward"};
    else if(pkg.equals("com.kurashiru.box.merge"))
      ids=new String[]{"box-default-main-reward","box-default-check-reward","box-default-ad-reward"};
    else if(pkg.equals("com.kurashiru.poipoi.usagi"))
      ids=new String[]{"poipoi-default-missions-reward","poipoi-default-claim-wait-reward"};
    else return false;
    for(String id:ids){
      PhaseEngine.Watch w=engine.rule(id);
      if(w==null||w.condition!=PhaseEngine.MATCH||w.reference==null||w.reference.length!=1536)continue;
      float[] seen=samples.get(w.regionId);
      if(seen!=null&&seen.length==1536&&w.distance(seen)<=w.threshold)return true;
    }
    return false;
  }
  private KurashiruClaimPolicy(){}
}

final class KurashiruRewardProof {
  private long last=-1,first=-1;
  private int streak;
  void reset(){last=first=-1;streak=0;}
  boolean observe(boolean positive,long capturedAt,long now){
    if(capturedAt<=last||capturedAt<=0||capturedAt>now||now-capturedAt>=1500)return false;
    if(!positive){reset();last=capturedAt;return false;}
    if(first<=0||capturedAt-first>3000){first=capturedAt;streak=0;}
    last=capturedAt;
    streak++;
    return streak>=2;
  }
}
