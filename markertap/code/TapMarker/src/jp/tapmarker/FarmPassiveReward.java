package jp.tapmarker;

/**
 * Farm reward receipts can credit before "close" is pressed. Use a previously
 * read, playable-screen pt baseline and a 2-frame reward-popup proof instead
 * of opening a claim merely by tapping Close. The connectome still requires a
 * separate two-frame, positive, stable pt balance delta before awarding.
 */
final class FarmPassiveReward {
  private long baseline=-1,baselineAt=-1,pendingAt=-1,lastSeenAt=-1;
  private String receipt="";
  private final KurashiruRewardProof uiProof=new KurashiruRewardProof();

  void reset(){
    baseline=baselineAt=pendingAt=lastSeenAt=-1;receipt="";
    uiProof.reset();
  }

  /** Called per fresh game frame, before the new pt is fed to the connectome. */
  String observe(boolean playable,boolean receiptVisible,long pt,
                 long capturedAt,long now,RewardEvaluationConnectome objective) {
    if(objective==null||capturedAt<=lastSeenAt||capturedAt<=0||now<capturedAt
        ||now-capturedAt>=RewardEvaluationConnectome.FRESH_MS)return "";
    lastSeenAt=capturedAt;
    if(pendingAt>0&&(now<pendingAt||now-pendingAt>RewardEvaluationConnectome.CLAIM_TTL_MS)){
      SharedPointClaimArbiter.release(objective,receipt);
      objective.invalidatePending();reset();
      lastSeenAt=capturedAt;
    }
    if(playable&&!receiptVisible&&receipt.isEmpty()&&pt>=0){
      baseline=pt;baselineAt=capturedAt;
      uiProof.reset();
      return "";
    }
    if(!receiptVisible){
      uiProof.observe(false,capturedAt,now);
      return receipt;
    }
    if(receipt.isEmpty()){
      if(baseline<0||baselineAt<=0||capturedAt<=baselineAt
          ||capturedAt-baselineAt>RewardEvaluationConnectome.BASELINE_TTL_MS)return "";
      String id="farm-pt-"+capturedAt;
      if(!SharedPointClaimArbiter.begin(objective,id,now))return "";
      if(!objective.claimFromPriorPlay(id,"pt",now,baseline,baselineAt)){
        SharedPointClaimArbiter.release(objective,id);
        return "";
      }
      pendingAt=now;receipt=id;baseline=baselineAt=-1;
      uiProof.reset();
    }
    if(uiProof.observe(true,capturedAt,now))
      objective.claimAcknowledged(receipt,now);
    return receipt;
  }

  void completed(){reset();}
}
