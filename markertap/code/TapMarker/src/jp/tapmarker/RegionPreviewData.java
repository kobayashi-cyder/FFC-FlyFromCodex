package jp.tapmarker;
import java.util.*;
/** Immutable small display patches; pixels outside the plan are never read. */
final class RegionPreviewData {
 static final int SIDE=32;
 final int nativeWidth,nativeHeight,width,height;final String scope;final long capturedAt;final List<Patch> patches;
 static final class Patch {
  final int x,y,width,height;final int[] pixels;final BitSet present=new BitSet();
  Patch(int x,int y,int w,int h){this.x=x;this.y=y;width=w;height=h;pixels=new int[w*h];}
  String key(){return x+":"+y;}
 }
 RegionPreviewData(RegionPlan plan,int w,int h,long at,List<Patch> patches){nativeWidth=plan.width;nativeHeight=plan.height;width=w;height=h;scope=plan.previewKey;capturedAt=at;this.patches=Collections.unmodifiableList(patches);}
 static RegionPreviewData capture(RegionPlan plan,int requestedWidth,long at,RegionSampler.Pixels source){
  return capture(plan,requestedWidth,at,source,null);
 }
 static RegionPreviewData capture(RegionPlan plan,int requestedWidth,long at,RegionSampler.Pixels source,PreviewConnectome.Schedule schedule){
  if(requestedWidth<=0||source==null)throw new IllegalArgumentException("preview");
  int width=Math.min(plan.width,requestedWidth),height=Math.max(1,Math.round(width*plan.height/(float)plan.width));
  if(schedule!=null&&(schedule.width!=width||schedule.height!=height||!schedule.scope.equals(plan.previewKey)))
   throw new IllegalArgumentException("stale preview schedule");
  List<Patch> patches=new ArrayList<>();
  for(int ty=0;ty<height;ty+=SIDE)for(int tx=0;tx<width;tx+=SIDE){
   if(schedule!=null&&!schedule.includes(tx,ty))continue;
   Patch patch=null;int pw=Math.min(SIDE,width-tx),ph=Math.min(SIDE,height-ty);
   for(int y=0;y<ph;y++){int sy=Math.min(plan.height-1,(int)((ty+y+.5)*plan.height/height));
    for(int x=0;x<pw;x++){int sx=Math.min(plan.width-1,(int)((tx+x+.5)*plan.width/width));
     if(!plan.contains(sx,sy))continue;if(patch==null)patch=new Patch(tx,ty,pw,ph);
     int index=y*pw+x;patch.pixels[index]=source.get(sx,sy);patch.present.set(index);
    }
   }
   if(patch!=null)patches.add(patch);
  }
  return new RegionPreviewData(plan,width,height,at,patches);
 }
 /** UI-owned cache. Commit only after display, generation, scope and freshness checks. */
 static final class Cache {
  final Map<String,int[]> pixels=new LinkedHashMap<>();String scope="";int width,height,nativeWidth,nativeHeight;long capturedAt;
  boolean resetFor(RegionPreviewData data){
   boolean reset=!scope.equals(data.scope)||width!=data.width||height!=data.height||nativeWidth!=data.nativeWidth||nativeHeight!=data.nativeHeight;
   if(reset){pixels.clear();scope=data.scope;width=data.width;height=data.height;nativeWidth=data.nativeWidth;nativeHeight=data.nativeHeight;capturedAt=0;}
   return reset;
  }
  int[] merged(Patch patch){
   int[] previous=pixels.get(patch.key());boolean changed=previous==null;
   if(previous!=null)for(int i=patch.present.nextSetBit(0);i>=0;i=patch.present.nextSetBit(i+1))if(previous[i]!=patch.pixels[i]){changed=true;break;}
   if(!changed)return null;int[] next=previous==null?new int[patch.pixels.length]:previous.clone();
   for(int i=patch.present.nextSetBit(0);i>=0;i=patch.present.nextSetBit(i+1))next[i]=patch.pixels[i];
   return next;
  }
  boolean update(Patch patch){int[] next=merged(patch);if(next==null)return false;pixels.put(patch.key(),next);return true;}
  void clear(){pixels.clear();scope="";capturedAt=0;}
 }
}
