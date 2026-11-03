package jp.tapmarker;

import java.util.HashSet;

/**
 * Per-game, per-display evaluation connectome.
 *
 * Connections:
 *   fresh ad-positive frame -> measured CM dwell -> negative score
 *   registered claim -> post-claim reward evidence -> two fresh balance readings
 *   -> confirmed receipt -> completed cycle + confirmed reward quantity -> positive score
 *
 * A tap, a completed play phase, a reward-looking dialog, a restart or an
 * unverified balance change never credits a cycle. Inputs must be from the
 * registered game, and balance observations must be tagged with the same unit.
 * This intentionally exposes an evidence-only input interface instead of
 * inventing an OCR value when the game supplies no reliable balance reader.
 */
final class RewardEvaluationConnectome {
  static final long FRESH_MS=1500L, MAX_AD_GAP_MS=3000L;
  static final long CLAIM_TTL_MS=30000L, BASELINE_TTL_MS=60000L;
  static final long MAX_PROOF_GAP_MS=3000L;
  private static final double CYCLE_WEIGHT=100.0;
  private static final double QUANTITY_WEIGHT=20.0;
  private static final double AD_SECONDS_WEIGHT=4.0;

  private static final class Claim {
    final String id,unit;
    final long sentAt,baseline;
    long uiAt=-1, candidate=-1, candidateAt=-1;
    int confirmations;
    Claim(String id,String unit,long at,long baseline) {
      this.id=id;this.unit=unit;sentAt=at;this.baseline=baseline;
    }
  }

  private long lastAdFrame=-1,adMillis,confirmedCycles,confirmedQuantity;
  private boolean lastAdPositive;
  private String balanceUnit="";
  private long latestBalance=-1,latestBalanceAt=-1;
  private Claim pending;
  private final HashSet<String> creditedIds=new HashSet<>();

  synchronized void reset(){
    lastAdFrame=-1;adMillis=confirmedCycles=confirmedQuantity=0;
    lastAdPositive=false;balanceUnit="";latestBalance=latestBalanceAt=-1;
    pending=null;creditedIds.clear();
  }

  /** Counts only observed fresh-positive CM intervals (not blind or stale time). */
  synchronized void observeAd(long capturedAt,long now,boolean confirmedAd){
    if(!fresh(capturedAt,now)||capturedAt<=lastAdFrame)return;
    if(confirmedAd&&lastAdPositive&&lastAdFrame>0
        &&capturedAt-lastAdFrame<=MAX_AD_GAP_MS){
      adMillis=saturatingAdd(adMillis,capturedAt-lastAdFrame);
    }
    lastAdFrame=capturedAt;lastAdPositive=confirmedAd;
  }

  /** A safe re-launch never provides evidence of a collected reward. */
  synchronized void invalidatePending(){pending=null;}

  /**
   * Called only after the game has accepted a claim tap. A previously observed
   * balance from the same currency is required; without it, reward quantity
   * cannot be proven and no cycle is opened for scoring.
   */
  synchronized boolean claimSent(String receiptId,String unit,long at){
    if(receiptId==null||receiptId.isEmpty()||unit==null||unit.isEmpty()
        ||at<=0||creditedIds.contains(receiptId)||pending!=null
        ||!unit.equals(balanceUnit)||latestBalance<0||latestBalanceAt<=0
        ||latestBalanceAt>=at||at-latestBalanceAt>BASELINE_TTL_MS)return false;
    pending=new Claim(receiptId,unit,at,latestBalance);
    return true;
  }

  /**
   * Evidence of the actual post-claim reward response (not generic close).
   * The UI alone does not create a score or a completed cycle.
   */
  synchronized boolean claimAcknowledged(String receiptId,long at){
    if(pending==null||!pending.id.equals(receiptId)||at<=pending.sentAt
        ||at-pending.sentAt>CLAIM_TTL_MS)return false;
    pending.uiAt=at;return true;
  }

  /**
   * A game-specific trusted balance reader supplies integer minor-units.
   * A credit is accepted only after claim + reward UI proof + two distinct,
   * fresh, consistent POST-claim balance frames showing a positive delta.
   * Returns true exactly once when a receipt is credited.
   */
  synchronized boolean observeBalance(String unit,long value,long capturedAt,long now){
    if(unit==null||unit.isEmpty()||value<0||!fresh(capturedAt,now)
        ||capturedAt<=latestBalanceAt)return false;
    if(pending!=null&&now-pending.sentAt>CLAIM_TTL_MS)pending=null;
    if(!unit.equals(balanceUnit)){balanceUnit=unit;latestBalance=value;
      latestBalanceAt=capturedAt;pending=null;return false;}
    boolean credited=false;
    if(pending!=null&&pending.unit.equals(unit)&&pending.uiAt>pending.sentAt
        &&capturedAt>pending.uiAt&&capturedAt>pending.sentAt
        &&capturedAt-pending.sentAt<=CLAIM_TTL_MS
        &&value>pending.baseline){
      if(value==pending.candidate&&pending.candidateAt>0
          &&capturedAt-pending.candidateAt<=MAX_PROOF_GAP_MS){
        pending.confirmations++;
      }else{pending.candidate=value;pending.confirmations=1;}
      pending.candidateAt=capturedAt;
      if(pending.confirmations>=2&&confirmedCycles<Long.MAX_VALUE){
        long delta=value-pending.baseline;
        // Subtraction overflow or an impossible counter value must fail shut.
        if(delta>0&&confirmedQuantity<=Long.MAX_VALUE-delta
            &&creditedIds.add(pending.id)){
          confirmedQuantity+=delta;confirmedCycles++;credited=true;
        }
        pending=null;
      }
    }else if(pending!=null){pending.candidate=-1;pending.candidateAt=-1;
      pending.confirmations=0;}
    latestBalance=value;latestBalanceAt=capturedAt;
    return credited;
  }

  /** No added value from merely attempting a reward or killing an ad. */
  synchronized double score(){
    return CYCLE_WEIGHT*confirmedCycles
        +QUANTITY_WEIGHT*Math.log1p(confirmedQuantity)
        -AD_SECONDS_WEIGHT*(adMillis/1000.0);
  }
  synchronized long cycles(){return confirmedCycles;}
  synchronized long quantity(){return confirmedQuantity;}
  synchronized long adMillis(){return adMillis;}
  synchronized boolean awaitingProof(){return pending!=null;}
  synchronized String report(){
    return String.format(java.util.Locale.ROOT,
      "評価J=%.2f / 受領確定%d回 / 実測報酬%d / CM観測%.1f秒%s",
      score(),confirmedCycles,confirmedQuantity,adMillis/1000.0,
      pending==null?"":" / 受領検証待ち");
  }

  private static boolean fresh(long at,long now){
    return at>0&&now>=at&&now-at<FRESH_MS;
  }
  private static long saturatingAdd(long a,long b){
    return a>=Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;
  }
}
