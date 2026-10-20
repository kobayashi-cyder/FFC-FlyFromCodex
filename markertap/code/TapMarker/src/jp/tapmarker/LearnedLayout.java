package jp.tapmarker;

import java.util.*;

/** Window-local evidence, with independent object search when a shared layout no longer fits. */
public final class LearnedLayout {
 final ContentRelocator content=new ContentRelocator();
 final Map<String,float[]> independentlyLocated=new HashMap<>(),recordedBounds=new HashMap<>();
 boolean independent;
 public static final class Transform {
  final float sx,sy,left,top;
  Transform(float x,float y,float t){sx=x;sy=y;left=(1-x)/2;top=t;}
  float[] bounds(float[] r){return new float[]{left+r[0]*sx,top+r[1]*sy,left+r[2]*sx,top+r[3]*sy};}
  float[] point(float[] p,int width,int height){float x=left+p[0]*sx,y=top+p[1]*sy;return x<0||x>=1||y<0||y>=1?null:new float[]{x*width,y*height};}
 }
 static final class Anchor {String id;float[] bounds,ref;float threshold;}
 Transform transform=new Transform(1,1,0);String state="",status="学習領域を確認中";long searchedAt=-1;boolean confirmed;
 List<Anchor> anchors(PhaseEngine e,PhaseEngine.Watch w){ArrayList<Anchor> a=new ArrayList<>();if(w.condition==PhaseEngine.MATCH)add(a,e,w.regionId,w.reference,w.threshold);for(int i=0;i<w.guardIds.length&&i<w.guardReferences.length;i++)add(a,e,w.guardIds[i],w.guardReferences[i],w.threshold);return a;}
 static void add(List<Anchor> list,PhaseEngine e,String id,float[] ref,float threshold){PhaseEngine.Region r=e.regions.get(id);if(r==null||ref==null||ref.length!=768)return;Anchor a=new Anchor();a.id=id;a.bounds=r.bounds;a.ref=ref;a.threshold=threshold;list.add(a);}
 static boolean inside(float[] bounds){return bounds[0]>=0&&bounds[1]>=0&&bounds[2]<=1&&bounds[3]<=1&&bounds[2]>bounds[0]&&bounds[3]>bounds[1];}
 static float score(List<Anchor> anchors,Transform t,int width,int height,RegionSampler.Pixels pixels,boolean quick){
  float worst=0;for(Anchor a:anchors){float[] r=t.bounds(a.bounds);if(!inside(r))return Float.POSITIVE_INFINITY;
   if(quick){double distance=0;int[] b=RegionMath.bounds(r,width,height);for(int y=0;y<4;y++)for(int x=0;x<4;x++){int c=pixels.get(b[0]+Math.min(b[2]-b[0]-1,(int)((x+.5f)*(b[2]-b[0])/4)),b[1]+Math.min(b[3]-b[1]-1,(int)((y+.5f)*(b[3]-b[1])/4)));for(int color=0;color<3;color++){float mean=0;for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++)mean+=a.ref[((y*4+dy)*16+x*4+dx)*3+color];float delta=mean/16-((c>>(16-color*8))&255)/255f;distance+=delta*delta;}}worst=Math.max(worst,(float)(distance/48)/a.threshold);
   }else worst=Math.max(worst,FlyCircuit.distance(a.ref,RegionSampler.sample(width,height,r,pixels))/a.threshold);
  }return anchors.isEmpty()?Float.POSITIVE_INFINITY:worst;
 }
 public Map<String,float[]> sample(PhaseEngine e,int width,int height,RegionSampler.Pixels pixels,long now){return sampleFrame(e,width,height,FrameSampleCache.wrap(width,height,pixels),now);}
 private Map<String,float[]> sampleFrame(PhaseEngine e,int width,int height,RegionSampler.Pixels pixels,long now){
  independent=false;independentlyLocated.clear();recordedBounds.clear();
  if(!state.equals(e.current)){state=e.current;confirmed=false;searchedAt=-1;}
  PhaseEngine.Phase phase=e.phases.get(e.current);Map<String,float[]> output=new HashMap<>();if(phase==null||phase.terminal)return output;
  Transform chosen=null;float best=Float.POSITIVE_INFINITY;List<Anchor> chosenAnchors=null;
  for(PhaseEngine.Watch watch:phase.watches){if(!e.eligible(watch))continue;List<Anchor> anchors=anchors(e,watch);if(anchors.isEmpty()&&watch.condition!=PhaseEngine.MATCH){chosen=transform;chosenAnchors=anchors;best=0;break;}if(anchors.isEmpty())continue;Transform identity=new Transform(1,1,0);float exact=score(anchors,identity,width,height,pixels,false);
   if(exact<=1){chosen=identity;chosenAnchors=anchors;best=exact;break;}
   float prior=score(anchors,transform,width,height,pixels,false);if(prior<=1&&prior<best){chosen=transform;chosenAnchors=anchors;best=prior;}
  }
  if(chosen==null&&(searchedAt<0||now-searchedAt>=1000)){
   searchedAt=now;ArrayList<Transform> pool=new ArrayList<>();ArrayList<Float> scores=new ArrayList<>();ArrayList<List<Anchor>> evidence=new ArrayList<>();
   for(PhaseEngine.Watch watch:phase.watches){List<Anchor> anchors=anchors(e,watch);if(anchors.size()<2)continue;
    for(float sx:new float[]{.92f,1,1.08f})for(int scale=0;scale<=7;scale++)for(int shift=0;shift<=8;shift++){
     Transform candidate=new Transform(sx,.78f+scale*.08f,-.14f+shift*.035f);float value=score(anchors,candidate,width,height,pixels,true);int at=0;while(at<scores.size()&&scores.get(at)<=value)at++;if(at<8){pool.add(at,candidate);scores.add(at,value);evidence.add(at,anchors);if(pool.size()>8){pool.remove(8);scores.remove(8);evidence.remove(8);}}
    }
   }
   Transform runnerUp=null;float runnerScore=Float.POSITIVE_INFINITY;
   for(int i=0;i<pool.size();i++){Transform candidate=pool.get(i);List<Anchor> anchors=evidence.get(i);float value=score(anchors,candidate,width,height,pixels,false);
    for(int round=0;round<6;round++){float step=(float)Math.pow(.5,round);Transform improved=candidate;float improvedValue=value;
     for(Transform nearby:new Transform[]{new Transform(candidate.sx,candidate.sy+.04f*step,candidate.top),new Transform(candidate.sx,candidate.sy-.04f*step,candidate.top),new Transform(candidate.sx,candidate.sy,candidate.top+.018f*step),new Transform(candidate.sx,candidate.sy,candidate.top-.018f*step),new Transform(candidate.sx+.04f*step,candidate.sy,candidate.top),new Transform(candidate.sx-.04f*step,candidate.sy,candidate.top)}){float v=score(anchors,nearby,width,height,pixels,false);if(v<improvedValue){improved=nearby;improvedValue=v;}}
     candidate=improved;value=improvedValue;
    }
    if(value<best){runnerUp=chosen;runnerScore=best;chosen=candidate;best=value;chosenAnchors=anchors;}else if(value<runnerScore){runnerUp=candidate;runnerScore=value;}
   }
   if(best>1)chosen=null;
   if(chosen!=null&&runnerUp!=null&&runnerScore<=1&&Math.abs(chosen.top-runnerUp.top)+Math.abs(chosen.sy-runnerUp.sy)*.5f>.035f){chosen=null;status="位置の候補が複数あるため保留";}
  }
  confirmed=chosen!=null;if(chosen==null){Map<String,float[]> relocated=relocate(e,width,height,pixels,now);if(!relocated.isEmpty())return relocated;if(!status.contains("候補"))status="記録した対象を画面全体から再探索中";return output;}transform=chosen;status=chosen.sx==1&&chosen.sy==1&&chosen.top==0?"記録表示に一致":"ウインドウ内の位置差を補正";
  for(PhaseEngine.Region r:e.activeRegions()){float[] bounds=transform.bounds(r.bounds);if(inside(bounds))output.put(r.id,RegionSampler.sample(width,height,bounds,pixels));}
  return output;
 }
 Map<String,float[]> relocate(PhaseEngine e,int width,int height,RegionSampler.Pixels pixels,long now){
  Map<String,float[]> result=new HashMap<>();PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null)return result;content.budget=2;
  int offset=phase.watches.isEmpty()?0:(int)(content.cursor++%phase.watches.size());
  for(int k=0;k<phase.watches.size();k++){PhaseEngine.Watch watch=phase.watches.get((offset+k)%phase.watches.size());if(watch.condition!=PhaseEngine.MATCH||!e.eligible(watch)||watch.reference==null||watch.reference.length!=768)continue;
   List<Anchor> anchors=anchors(e,watch);if(anchors.isEmpty())continue;Map<String,float[]> raw=new HashMap<>(),bounds=new HashMap<>();boolean complete=true;
   for(Anchor anchor:anchors){VisualLocator.Match match=content.locate(anchor.id,anchor.ref,null,anchor.bounds,Math.min(.012f,anchor.threshold),width,height,pixels,now,true);if(match==null||FlyCircuit.distance(anchor.ref,match.raw)>anchor.threshold){complete=false;break;}raw.put(anchor.id,match.raw);bounds.put(anchor.id,match.bounds);}
   if(!complete||watch.distance(raw.get(watch.regionId))>watch.threshold||!e.guardsMatch(watch,raw)||!groundedAction(watch,e.regions.get(watch.regionId)))continue;
   independent=true;confirmed=true;independentlyLocated.putAll(bounds);for(Anchor anchor:anchors)recordedBounds.put(anchor.id,anchor.bounds.clone());status="記録した対象と補助条件を個別に再認識";return raw;
  }return result;
 }
 static boolean groundedAction(PhaseEngine.Watch watch,PhaseEngine.Region region){
  if(region==null)return false;float[] points=watch.action==PhaseEngine.CENTER?watch.touchPoint:watch.action==PhaseEngine.MARKERS?watch.markerPoints:watch.action==PhaseEngine.CURVE?watch.curve:null;
  if(points==null||watch.action==PhaseEngine.CENTER&&points.length==0)return true;if(watch.action==PhaseEngine.CURVE&&!watch.relativeCurve||!ObservationLearner.validPoints(points))return false;
  for(int i=0;i<points.length;i+=2)if(points[i]<region.bounds[0]||points[i]>region.bounds[2]||points[i+1]<region.bounds[1]||points[i+1]>region.bounds[3])return false;return true;
 }
 public float[] pointFor(String id,float[] point,int width,int height){
  if(!independent)return point(point,width,height);if(!confirmed||point==null||point.length!=2)return null;
  float[] original=recordedBounds.get(id),located=independentlyLocated.get(id);
  if(original==null||located==null||point[0]<original[0]||point[0]>original[2]||point[1]<original[1]||point[1]>original[3])return null;
  float x=located[0]+(point[0]-original[0])/(original[2]-original[0])*(located[2]-located[0]),y=located[1]+(point[1]-original[1])/(original[3]-original[1])*(located[3]-located[1]);
  return x>=0&&y>=0&&x<1&&y<1?new float[]{x*width,y*height}:null;
 }
 public float[] pathFor(String id,float[] points,int width,int height){if(!independent)return path(points,width,height);if(!ObservationLearner.validPoints(points))return null;float[] output=new float[points.length];for(int i=0;i<points.length;i+=2){float[] p=pointFor(id,new float[]{points[i],points[i+1]},width,height);if(p==null)return null;output[i]=p[0];output[i+1]=p[1];}return output;}
 public void relative(){independent=false;independentlyLocated.clear();recordedBounds.clear();transform=new Transform(1,1,0);confirmed=true;status="相対位置で実行（時間条件）";}
 public float[] point(float[] p,int width,int height){return !independent&&confirmed&&p!=null&&p.length==2?transform.point(p,width,height):null;}
 public float[] path(float[] points,int width,int height){if(!confirmed||!ObservationLearner.validPoints(points))return null;float[] path=new float[points.length];for(int i=0;i<points.length;i+=2){float[] p=point(new float[]{points[i],points[i+1]},width,height);if(p==null)return null;path[i]=p[0];path[i+1]=p[1];}return path;}
}
