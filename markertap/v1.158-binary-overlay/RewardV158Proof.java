package jp.tapmarker;

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

