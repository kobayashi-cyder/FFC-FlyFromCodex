package jp.tapmarker;

/**
 * Process-wide guard for the possibly shared Kurashiru pt balance.
 * If another game claims during a pending receipt, both observations are
 * attribution-ambiguous: the older claim is tainted and the new one is denied.
 * A lease expires after the reward-verification window.
 *
 * This prevents one shared pt increase from being credited twice across the
 * physical and virtual lanes. It cannot prove server-side attribution.
 */
final class SharedPointClaimArbiter {
  private static Object owner;
  private static String id="";
  private static long deadline=-1;
  private static boolean ambiguous;
  private static final long MAX_MS=30000L;

  static synchronized boolean begin(Object lane,String receipt,long now){
    if(lane==null||receipt==null||receipt.isEmpty()||now<=0)return false;
    expire(now);
    if(owner!=null){
      if(owner!=lane||!id.equals(receipt)){
        ambiguous=true;
        return false;
      }
      return !ambiguous;
    }
    owner=lane;id=receipt;deadline=now+MAX_MS;ambiguous=false;return true;
  }

  static synchronized boolean allowed(Object lane,String receipt,long now){
    expire(now);
    return owner==lane&&id.equals(receipt)&&!ambiguous;
  }

  static synchronized void release(Object lane,String receipt){
    if(owner==lane&&(receipt==null||id.equals(receipt)))clear();
  }

  private static void expire(long now){
    if(owner!=null&&(now<0||now>=deadline))clear();
  }
  private static void clear(){owner=null;id="";deadline=-1;ambiguous=false;}
  private SharedPointClaimArbiter(){}
}
