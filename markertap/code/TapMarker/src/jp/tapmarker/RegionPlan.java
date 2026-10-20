package jp.tapmarker;
import java.util.*;
/** Native-coordinate dependency tiles. Distant areas never become a bounding box. */
final class RegionPlan {
 static final int SIDE=32;
 final int width,height,columns;final String scope;private final BitSet tiles;
 private RegionPlan(int w,int h,String scope,BitSet tiles){width=w;height=h;columns=(w+SIDE-1)/SIDE;this.scope=scope;this.tiles=(BitSet)tiles.clone();}
 static RegionPlan full(int w,int h,String scope){Builder b=new Builder(w,h,scope);b.add(0,0,w,h);return b.build();}
 static RegionPlan regions(int w,int h,String scope,List<PhaseEngine.Region> regions){
  Builder b=new Builder(w,h,scope);StringBuilder identity=new StringBuilder(scope);
  for(PhaseEngine.Region r:regions){int[] bounds=RegionMath.bounds(r.bounds,w,h);b.add(bounds[0],bounds[1],bounds[2]-bounds[0],bounds[3]-bounds[1]);identity.append('/').append(r.id).append(Arrays.toString(bounds));}
  return new RegionPlan(w,h,identity.toString(),b.tiles);
 }
 boolean contains(int x,int y){return x>=0&&y>=0&&x<width&&y<height&&tiles.get((y/SIDE)*columns+x/SIDE);}
 int tileCount(){return tiles.cardinality();}
 long coveredPixels(){long n=0;for(int bit=tiles.nextSetBit(0);bit>=0;bit=tiles.nextSetBit(bit+1)){int x=(bit%columns)*SIDE,y=(bit/columns)*SIDE;n+=(long)Math.min(SIDE,width-x)*Math.min(SIDE,height-y);}return n;}
 static final class Builder {
  final int width,height,columns;final String scope;final BitSet tiles;
  Builder(int w,int h,String scope){if(w<=0||h<=0||scope==null)throw new IllegalArgumentException("region plan");width=w;height=h;columns=(w+SIDE-1)/SIDE;this.scope=scope;tiles=new BitSet();}
  Builder(RegionPlan plan){width=plan.width;height=plan.height;columns=plan.columns;scope=plan.scope;tiles=(BitSet)plan.tiles.clone();}
  void add(int x,int y,int w,int h){
   int left=Math.max(0,x),top=Math.max(0,y),right=(int)Math.min(width,(long)x+w),bottom=(int)Math.min(height,(long)y+h);
   if(w<=0||h<=0||right<=left||bottom<=top)return;
   for(int ty=top/SIDE;ty<=(bottom-1)/SIDE;ty++)tiles.set(ty*columns+left/SIDE,ty*columns+(right-1)/SIDE+1);
  }
  RegionPlan build(){return new RegionPlan(width,height,scope,tiles);}
 }
}
