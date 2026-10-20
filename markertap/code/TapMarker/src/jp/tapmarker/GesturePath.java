package jp.tapmarker;

/** Validated, bounded one-finger curves. */
public final class GesturePath {
 final float[] points;final double[] cumulative;final double length;
 public GesturePath(float[] p,int width,int height,long duration){if(p==null||p.length<2||p.length>512||p.length%2!=0||duration<1||duration>10000||!LearnedInput.inside(p,0,0,width,height))throw new IllegalArgumentException("画面外または無効な曲線");points=p.clone();cumulative=new double[p.length/2];for(int i=1;i<cumulative.length;i++)cumulative[i]=cumulative[i-1]+Math.hypot(p[i*2]-p[i*2-2],p[i*2+1]-p[i*2-1]);length=cumulative[cumulative.length-1];}
 public float[] position(float fraction){if(!Float.isFinite(fraction))throw new IllegalArgumentException("時間");double target=Math.max(0,Math.min(1,fraction))*length;int i=1;while(i<cumulative.length&&cumulative[i]<target)i++;if(i>=cumulative.length||length==0)return new float[]{points[points.length-2],points[points.length-1]};double segment=cumulative[i]-cumulative[i-1],f=segment==0?0:(target-cumulative[i-1])/segment;return new float[]{(float)(points[i*2-2]+(points[i*2]-points[i*2-2])*f),(float)(points[i*2-1]+(points[i*2+1]-points[i*2-1])*f)};}
}
