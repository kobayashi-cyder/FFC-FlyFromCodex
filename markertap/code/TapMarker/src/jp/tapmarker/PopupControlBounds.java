package jp.tapmarker;

/** A wholly visible control, or the visible part of an explicitly named Close. */
final class PopupControlBounds {
 static int[] visible(int[] b,int[] scope,boolean clippedClose){
  if(b==null||scope==null||b.length!=4||scope.length!=4)return null;
  int sw=scope[2]-scope[0],sh=scope[3]-scope[1],bw=b[2]-b[0],bh=b[3]-b[1];
  if(sw<=0||sh<=0||bw<=0||bh<=0||bw>sw*.82f||bh>sh*.22f)return null;
  int l=Math.max(b[0],scope[0]),t=Math.max(b[1],scope[1]),r=Math.min(b[2],scope[2]),z=Math.min(b[3],scope[3]);
  if(r<=l||z<=t)return null;
  boolean complete=l==b[0]&&t==b[1]&&r==b[2]&&z==b[3];
  if(!complete&&(!clippedClose||r-l<bw*.6f||z-t<Math.max(4,sh*.006f)))return null;
  return new int[]{l,t,r,z};
 }
 private PopupControlBounds(){}
}
