package jp.tapmarker;

/** Sampling timestamps, not pixel buffers, determine before/after membership. */
final class DiagnosticCapturePolicy {
 static final long PERIOD_MS=1000,POST_GAP_MS=250;
 long lastAt=-1,actionAt=-1,lastPost=-1;int posts;boolean controlPending;
 boolean beginAction(String type,long now){if(!controlPending&&type.equals("タップ"))return false;controlPending=false;action(now);return true;}
 void action(long now){actionAt=now;lastPost=-1;posts=2;}
 String kind(long at,boolean force){
  if(at<0||at<=lastAt&&!force)return "";
  if(force)return "before";
  if(posts>0&&at>actionAt&&(lastPost<0||at-lastPost>=POST_GAP_MS))return "after";
  return lastAt<0||at-lastAt>=PERIOD_MS?"sample":"";
 }
 void accepted(long at,String kind){lastAt=Math.max(lastAt,at);if(kind.equals("after")){posts--;lastPost=at;}}
 static int[] size(int width,int height){if(width<=0||height<=0)throw new IllegalArgumentException("invalid diagnostic dimensions");float scale=Math.min(1,Math.min(540f/width,1440f/height));return new int[]{Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale))};}
}
