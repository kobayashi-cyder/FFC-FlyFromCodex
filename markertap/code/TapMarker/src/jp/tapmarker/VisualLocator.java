package jp.tapmarker;

import java.util.*;

/** Searches one application's pixels. No screen position or relation to another control is required. */
final class VisualLocator {
 static final class Match {
  final float[] bounds,raw;final float distance;
  Match(float[] b,float[] r,float d){bounds=b;raw=r;distance=d;}
 }
 static final class Candidate {int x,y,w,h;float score;Candidate(int x,int y,int w,int h,float s){this.x=x;this.y=y;this.w=w;this.h=h;score=s;}}
 static final int POOL=24;
 static final float[] SCALES={1,.8f,1.25f,.64f,1.55f,.5f,1.9f};
 static float distance(float[] ref,float[] raw,float[] mask){
  if(ref==null||raw==null||ref.length!=768||raw.length!=768)return Float.POSITIVE_INFINITY;
  if(mask==null)return PhaseEngine.blockDistance(ref,raw);
  double sum=0;int n=0;
  for(int y=0;y<16;y+=2)for(int x=0;x<16;x+=2){boolean use=true;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++)if(mask[(y+dy)*16+x+dx]<.5f)use=false;if(!use)continue;
   for(int c=0;c<3;c++){double d=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int i=((y+dy)*16+x+dx)*3+c;d+=ref[i]-raw[i];}sum+=d*d/16;n++;}
  }return n<36?Float.POSITIVE_INFINITY:(float)(sum/n);
 }
 static boolean informative(float[] ref,float[] mask){
  if(ref==null||ref.length!=768||mask!=null&&mask.length!=256)return false;
  double mean=0,square=0;int n=0;
  for(int i=0;i<256;i++)if(mask==null||mask[i]>=.5f){float v=(ref[i*3]+ref[i*3+1]+ref[i*3+2])/3;mean+=v;square+=v*v;n++;}
  return n>=64&&square/n-Math.pow(mean/n,2)>.003;
 }
 static Match verify(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] bounds,float tolerance){
  return verify(width,height,pixels,ref,mask,bounds,tolerance,false);
 }
 static Match verify(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] bounds,float tolerance,boolean detailed){
  return verify(width,height,pixels,ref,mask,bounds,tolerance,detailed,null);
 }
 static Match verify(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] bounds,float tolerance,boolean detailed,java.util.function.Predicate<float[]> accepts){
  if(bounds==null||!AdaptiveLayout.inside(bounds)||!informative(ref,mask))return null;
  float[] raw=RegionSampler.sample(width,height,bounds,pixels);float d=metric(ref,raw,mask,detailed);
  return d<=tolerance&&(accepts==null||accepts.test(raw))?new Match(bounds.clone(),raw,d):null;
 }
 static float metric(float[] ref,float[] raw,float[] mask,boolean detailed){return detailed&&mask==null?FlyCircuit.distance(ref,raw):distance(ref,raw,mask);}
 /** A pressed control usually moves by a few pixels. Check that finite neighbourhood before a global search. */
 static Match nearby(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] expected,float tolerance){
  if(expected==null||!AdaptiveLayout.inside(expected))return null;
  float cx=(expected[0]+expected[2])/2,cy=(expected[1]+expected[3])/2,bw=expected[2]-expected[0],bh=expected[3]-expected[1];Match best=null;
  for(float scale:new float[]{1,.9f,1.1f})for(float dy:new float[]{0,-.35f,.35f})for(float dx:new float[]{0,-.06f,.06f}){
   float[] b={cx+dx*bw-bw*scale/2,cy+dy*bh-bh*scale/2,cx+dx*bw+bw*scale/2,cy+dy*bh+bh*scale/2};
   Match found=verify(width,height,pixels,ref,mask,b,Float.POSITIVE_INFINITY);
   if(found!=null&&(best==null||found.distance<best.distance))best=found;
  }
  if(best==null)return null;
  Candidate c=new Candidate(Math.round(best.bounds[0]*width),Math.round(best.bounds[1]*height),Math.max(6,Math.round((best.bounds[2]-best.bounds[0])*width)),Math.max(4,Math.round((best.bounds[3]-best.bounds[1])*height)),0);
  // Subpixel glyph sampling has local minima. Refine just this control, keeping it within its own neighbourhood.
  for(int round=0;round<5;round++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){
   Candidate next=new Candidate(c.x,c.y,c.w,c.h,0);int dx=Math.max(1,Math.round(bw*width/12)/(1<<round)),dy=Math.max(1,Math.round(bh*height/3)/(1<<round));
   if(axis==0)next.x+=sign*dx;if(axis==1)next.y+=sign*dy;if(axis==2){next.w+=sign*dx;next.x-=sign*dx/2;}if(axis==3){next.h+=sign*dy;next.y-=sign*dy/2;}
   if(next.w<6||next.h<4||Math.abs((next.x+next.w/2f)/width-cx)>bw*.35f||Math.abs((next.y+next.h/2f)/height-cy)>bh*.8f||next.w< bw*width*.75f||next.w>bw*width*1.25f||next.h<bh*height*.6f||next.h>bh*height*1.4f)continue;
   Match found=verify(width,height,pixels,ref,mask,bounds(next,width,height),Float.POSITIVE_INFINITY);
   if(found!=null&&found.distance<best.distance){best=found;c=next;}
  }return best.distance<=tolerance?best:null;
 }
 static float[] bounds(Candidate c,int width,int height){return new float[]{c.x/(float)width,c.y/(float)height,(c.x+c.w)/(float)width,(c.y+c.h)/(float)height};}
 static float quick(int x,int y,int bw,int bh,RegionSampler.Pixels pixels,float[] means,int[] cells,float ceiling){
  float sum=0;int n=0;
  for(int cell:cells){int cx=cell%4,cy=cell/4;int a=pixels.get(x+Math.min(bw-1,(int)((cx+.25f)*bw/4)),y+Math.min(bh-1,(int)((cy+.25f)*bh/4))),b=pixels.get(x+Math.min(bw-1,(int)((cx+.75f)*bw/4)),y+Math.min(bh-1,(int)((cy+.25f)*bh/4))),c0=pixels.get(x+Math.min(bw-1,(int)((cx+.25f)*bw/4)),y+Math.min(bh-1,(int)((cy+.75f)*bh/4))),d0=pixels.get(x+Math.min(bw-1,(int)((cx+.75f)*bw/4)),y+Math.min(bh-1,(int)((cy+.75f)*bh/4)));
   for(int c=0;c<3;c++){int shift=16-c*8;float v=(((a>>shift)&255)+((b>>shift)&255)+((c0>>shift)&255)+((d0>>shift)&255))/(4*255f),d=means[cell*3+c]-v;sum+=d*d;n++;}
   if(n>=12&&sum/n>ceiling*2.5f)return sum/n;
  }return n==0?Float.POSITIVE_INFINITY:sum/n;
 }
 static void offer(List<Candidate> pool,Candidate c){
  int at=0;while(at<pool.size()&&pool.get(at).score<=c.score)at++;if(at>=POOL)return;
  // Keep spatial alternatives; a cluster of adjacent pixels must not exhaust the pool.
  for(int i=0;i<pool.size();i++){Candidate old=pool.get(i);if(Math.abs(old.x-c.x)<Math.max(2,c.w/5)&&Math.abs(old.y-c.y)<Math.max(2,c.h/4)&&Math.abs(old.w-c.w)<c.w*.12f&&Math.abs(old.h-c.h)<c.h*.15f){if(c.score>=old.score)return;pool.remove(i);break;}}
  at=0;while(at<pool.size()&&pool.get(at).score<=c.score)at++;pool.add(at,c);if(pool.size()>POOL)pool.remove(POOL);
 }
 static Match find(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] expected,float tolerance){
  return find(width,height,pixels,ref,mask,expected,tolerance,false);
 }
 static Match find(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] expected,float tolerance,boolean detailed){
  return find(width,height,pixels,ref,mask,expected,tolerance,detailed,null);
 }
 static Match find(int width,int height,RegionSampler.Pixels pixels,float[] ref,float[] mask,float[] expected,float tolerance,boolean detailed,java.util.function.Predicate<float[]> accepts){
  if(width<16||height<16||expected==null||!informative(ref,mask)||!Float.isFinite(tolerance)||tolerance<=0)return null;
  float[] means=new float[48];ArrayList<Integer> selected=new ArrayList<>();
  for(int y=0;y<4;y++)for(int x=0;x<4;x++){int count=0;for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++){int cell=(y*4+dy)*16+x*4+dx;if(mask!=null&&mask[cell]<.5f)continue;count++;for(int c=0;c<3;c++)means[(y*4+x)*3+c]+=ref[cell*3+c];}if(count>=12){selected.add(y*4+x);for(int c=0;c<3;c++)means[(y*4+x)*3+c]/=count;}}
  if(selected.size()<4)return null;int[] cells=new int[selected.size()];for(int i=0;i<cells.length;i++)cells[i]=selected.get(i);
  int originalW=Math.max(4,Math.round((expected[2]-expected[0])*width)),originalH=Math.max(4,Math.round((expected[3]-expected[1])*height));
  List<Candidate> pool=new ArrayList<>();Set<Long> sizes=new HashSet<>();
  for(float scale:SCALES)for(float aspect:new float[]{1,.82f,1.22f}){
   int bw=Math.max(8,Math.round(originalW*scale)),bh=Math.max(6,Math.round(originalH*scale*aspect));if(bw>width||bh>height||!sizes.add(((long)bw<<32)|bh))continue;
   int stepX=Math.max(2,bw/8),stepY=Math.max(2,bh/6);
   for(int y=0;y<=height-bh;y=Math.min(height-bh,y+stepY)){
    for(int x=0;x<=width-bw;x=Math.min(width-bw,x+stepX)){
     float ceiling=pool.size()<POOL?.16f:Math.max(.018f,pool.get(pool.size()-1).score);float q=quick(x,y,bw,bh,pixels,means,cells,ceiling);
     if(q<=ceiling)offer(pool,new Candidate(x,y,bw,bh,q));if(x==width-bw)break;
    }if(y==height-bh)break;
   }
  }
  List<Match> matches=new ArrayList<>();
  for(Candidate seed:pool){Candidate c=seed;float[] raw=RegionSampler.sample(width,height,bounds(c,width,height),pixels);float best=metric(ref,raw,mask,detailed);
   for(int round=0;round<7;round++){
    int dx=Math.max(1,(c.w/8)>>round),dy=Math.max(1,(c.h/6)>>round),dw=Math.max(1,(c.w/8)>>round),dh=Math.max(1,(c.h/6)>>round);
    for(int pass=0;pass<2;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){
     Candidate next=new Candidate(c.x,c.y,c.w,c.h,0);if(axis==0)next.x+=sign*dx;if(axis==1)next.y+=sign*dy;if(axis==2){next.w+=sign*dw;next.x-=sign*dw/2;}if(axis==3){next.h+=sign*dh;next.y-=sign*dh/2;}
     if(next.x<0||next.y<0||next.w<6||next.h<4||next.x+next.w>width||next.y+next.h>height)continue;
     float[] sample=RegionSampler.sample(width,height,bounds(next,width,height),pixels);float d=metric(ref,sample,mask,detailed);if(d<best){c=next;raw=sample;best=d;}
    }
   }
   if(best<=tolerance&&(accepts==null||accepts.test(raw)))matches.add(new Match(bounds(c,width,height),raw,best));
  }
  Collections.sort(matches,(a,b)->Float.compare(a.distance,b.distance));if(matches.isEmpty())return null;
  Match best=matches.get(0);
  for(int i=1;i<matches.size();i++){Match other=matches.get(i);float ax=(best.bounds[0]+best.bounds[2])/2,ay=(best.bounds[1]+best.bounds[3])/2,bx=(other.bounds[0]+other.bounds[2])/2,by=(other.bounds[1]+other.bounds[3])/2;
   if((Math.abs(ax-bx)>(best.bounds[2]-best.bounds[0])*.55f||Math.abs(ay-by)>(best.bounds[3]-best.bounds[1])*.65f)&&other.distance<=best.distance*1.35f+tolerance*.12f)return null;
  }return best;
 }
 private VisualLocator(){}
}
