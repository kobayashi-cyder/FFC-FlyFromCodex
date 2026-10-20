package jp.tapmarker;

import java.io.*;import java.util.*;import java.util.zip.*;

/** Real control pixels; independent moves, missing markers and changed geometry are synthetic. */
public final class MarkerlessRelocationTest {
 static int checks;static Map<String,float[]> targets;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static VirtualPresetProfileTest.Image image(String file,String name)throws Exception{
  InputStream stream=new FileInputStream("tests/"+file);if(file.endsWith(".gz"))stream=new GZIPInputStream(stream);
  try(DataInputStream in=new DataInputStream(stream)){int n=in.readInt();for(int i=0;i<n;i++){String key=in.readUTF();VirtualPresetProfileTest.Image im=new VirtualPresetProfileTest.Image(in);if(key.equals(name))return im;}}throw new AssertionError(name);
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final String app;
  Run(String app,String reference,String state)throws Exception{this.app=app;Map<String,float[]> refs=VirtualPresetProfileTest.canonical(reference);if(app.equals(PoipoiPreset.APP))PoipoiPreset.install(e,refs);else if(app.equals(BoxPreset.APP))BoxPreset.install(e,refs);else if(app.equals(ShoppingPreset.APP))ShoppingPreset.install(e,refs);else if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else if(app.equals(FarmPreset.APP))FarmPreset.install(e,refs);else UsagiPreset.install(e,refs);e.strictRuntime=true;layout=AdaptiveLayout.install(e,app,refs,VirtualRecoveryReplayTest.evidence);layout.configureTargets(app,targets);e.enter(state,0);}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image im,RegionSampler.Pixels pixels,long now){return layout.sampleRelative(e.activeRegions(),im.width,im.height,pixels,now);}
 }
 static final class Piece {
  final VirtualPresetProfileTest.Image im;final float[] original,located;
  Piece(VirtualPresetProfileTest.Image image,float[] old,float[] moved){im=image;original=old;located=moved;}
 }
 static RegionSampler.Pixels pieces(final int width,final int height,final Piece... pieces){return (x,y)->{float u=x/(float)width,v=y/(float)height;for(int i=pieces.length-1;i>=0;i--){Piece p=pieces[i];float[] b=p.located;if(u>=b[0]&&u<b[2]&&v>=b[1]&&v<b[3]){int sx=Math.min(p.im.width-1,(int)((p.original[0]+(u-b[0])/(b[2]-b[0])*(p.original[2]-p.original[0]))*p.im.width)),sy=Math.min(p.im.height-1,(int)((p.original[1]+(v-b[1])/(b[3]-b[1])*(p.original[3]-p.original[1]))*p.im.height));return p.im.get(sx,sy);}}return 0xff34384b;};}
 static void normal()throws Exception{
  String[][] rows={{UsagiPreset.APP,"tests/usagi-replay.bin","virtual-recovery-replay.bin","usagi-main",UsagiPreset.PREFIX+"main"},{FarmPreset.APP,"tests/farm-replay.bin","virtual-recovery-replay.bin","farm-main",FarmPreset.P+"main"},{CoinPreset.APP,"tests/video6720-references.bin","video6720-replay.bin.gz","17",CoinPreset.P+"main"},{ShoppingPreset.APP,"tests/video6717-references.bin","video6717-replay.bin.gz","40",ShoppingPreset.P+"main"},{BoxPreset.APP,"tests/video6724-references.bin","video6724-replay.bin.gz","17.0",BoxPreset.P+"main"},{PoipoiPreset.APP,"tests/image6727-references.bin","video6725-replay.bin.gz","26.0",PoipoiPreset.P+"main"}};
  for(String[] row:rows){Run r=new Run(row[0],row[1],row[4]);VirtualPresetProfileTest.Image im=image(row[2],row[3]);Map<String,float[]> samples=r.sample(im,im,1000);float[] p=r.layout.mainPointRelative(samples,im.width,im.height);check(p!=null,"play object located without any marker list: "+row[0]);float[] box=targets.get(row[0]+":main:bounds");check(p[0]>box[0]*im.width&&p[0]<box[2]*im.width&&p[1]>box[1]*im.height&&p[1]<box[3]*im.height,"main touch lies on its own visual object: "+row[0]);check(samples.containsKey(AdaptiveLayout.MAIN+ContentRelocator.LOCATED),"fresh object identity accompanies input: "+row[0]);
   int[] hidden=RegionMath.bounds(box,im.width,im.height);RegionSampler.Pixels absent=(x,y)->x>=hidden[0]&&x<hidden[2]&&y>=hidden[1]&&y<hidden[3]?0xff171b24:im.get(x,y);samples=r.sample(im,absent,3000);check(r.layout.mainPointRelative(samples,im.width,im.height)==null,"old cached point revoked when object disappears: "+row[0]);samples=r.sample(im,im,3500);check(r.layout.mainPointRelative(samples,im.width,im.height)!=null,"fresh reappearance resumes without new markers: "+row[0]);
   System.out.println("markerless normal: "+row[0]);
  }
 }
 static void independentDialog()throws Exception{
  VirtualPresetProfileTest.Image im=image("image6727-replay.bin.gz","6727");
  float[] heading={.03f,.08f,.53f,.18f},button={.60f,.77f,.90f,.81f};RegionSampler.Pixels screen=pieces(im.width,im.height,new Piece(im,PoipoiPreset.DECLINE[0],heading),new Piece(im,PoipoiPreset.DECLINE[1],button));
  Run r=new Run(PoipoiPreset.APP,"tests/image6727-references.bin",PoipoiPreset.P+"decline");PhaseEngine.Watch watch=r.e.rule(PoipoiPreset.P+"decline-tap");Map<String,float[]> samples=null;for(int i=0;i<5;i++){samples=r.sample(im,screen,1000+i*1600);if(watch.distance(samples.get(watch.regionId))<=watch.threshold)break;}
  check(watch.distance(samples.get(watch.regionId))<=watch.threshold,"dialog title and decline found despite unrelated positions");float[] p=r.layout.pointRelative(watch,samples,im.width,im.height);check(p!=null&&p[0]>button[0]*im.width&&p[0]<button[2]*im.width&&p[1]>button[1]*im.height&&p[1]<button[3]*im.height,"tap follows relocated decline rather than the old button or optional ad");check(r.e.confirmsAction(watch,r.e.current,r.e.epoch,samples),"independent match passes actual engine preflight");
  RegionSampler.Pixels absent=pieces(im.width,im.height,new Piece(im,PoipoiPreset.DECLINE[0],heading));samples=r.sample(im,absent,12000);check(!r.e.confirmsAction(watch,r.e.current,r.e.epoch,samples),"missing decline cancels action despite a remaining title");
 }
 static void independentMain()throws Exception{
  VirtualPresetProfileTest.Image im=image("video6725-replay.bin.gz","26.0");float[] head={.64f,.04f,.82f,.067f},mission={.65f,.22f,.81f,.246f},target={.04f,.48f,.67f,.712f};
  RegionSampler.Pixels screen=pieces(im.width,im.height,new Piece(im,PoipoiPreset.GAME[0],head),new Piece(im,PoipoiPreset.GAME[1],mission),new Piece(im,targets.get(PoipoiPreset.APP+":main:bounds"),target));
  Run r=new Run(PoipoiPreset.APP,"tests/image6727-references.bin",PoipoiPreset.P+"main");Map<String,float[]> samples=null;float[] p=null;for(int i=0;i<8;i++){samples=r.sample(im,screen,1000+i*1600);p=r.layout.mainPointRelative(samples,im.width,im.height);if(p!=null&&PoipoiPreset.playable(r.e,samples))break;}
  check(PoipoiPreset.playable(r.e,samples),"independent game anchors reacquire play state");check(p!=null&&p[0]>target[0]*im.width&&p[0]<target[2]*im.width&&p[1]>target[1]*im.height&&p[1]<target[3]*im.height,"play object moves independently of both headings and receives new main point");
 }
 static void independentMission()throws Exception{
  String[][] rows={{UsagiPreset.APP,"tests/usagi-replay.bin","virtual-recovery-replay.bin","usagi-main",UsagiPreset.PREFIX+"mission-tap",UsagiPreset.PREFIX+"ミッション中央をタップ"},{CoinPreset.APP,"tests/video6720-references.bin","video6720-replay.bin.gz","17",CoinPreset.P+"mission-tap",CoinPreset.P+"mission-button-tap"},{ShoppingPreset.APP,"tests/video6717-references.bin","video6717-replay.bin.gz","40",ShoppingPreset.P+"mission-tap",ShoppingPreset.P+"mission-button-tap"}};
  for(String[] row:rows){Run r=new Run(row[0],row[1],row[4]);VirtualPresetProfileTest.Image im=image(row[2],row[3]);float[] box=targets.get(row[0]+":mission:bounds"),moved={.63f,.43f,.63f+box[2]-box[0],.43f+box[3]-box[1]};RegionSampler.Pixels piece=pieces(im.width,im.height,new Piece(im,box,moved));
   RegionSampler.Pixels screen=(x,y)->{float u=x/(float)im.width,v=y/(float)im.height;if(u>=moved[0]&&u<moved[2]&&v>=moved[1]&&v<moved[3])return piece.get(x,y);if(u>=box[0]&&u<box[2]&&v>=box[1]&&v<box[3])return 0xff34384b;return im.get(x,y);};

   PhaseEngine.Watch watch=r.e.rule(row[5]);check(watch!=null,"mission control exists");float[] point=null;for(int i=0;i<4;i++){Map<String,float[]> samples=r.sample(im,screen,1000+i*1600);point=r.layout.pointRelative(watch,samples,im.width,im.height);if(point!=null)break;}
   if(point==null)System.out.println("mission missing "+row[0]+" tracks="+r.layout.relocator.tracks.keySet());check(point!=null&&point[0]>moved[0]*im.width&&point[0]<moved[2]*im.width&&point[1]>moved[1]*im.height&&point[1]<moved[3]*im.height,"mission input follows its own moved control: "+row[0]);
   RegionSampler.Pixels absent=(x,y)->{float u=x/(float)im.width,v=y/(float)im.height;return u>=box[0]&&u<box[2]&&v>=box[1]&&v<box[3]?0xff34384b:im.get(x,y);};Map<String,float[]> samples=r.sample(im,absent,9000);check(r.layout.pointRelative(watch,samples,im.width,im.height)==null,"missing mission control cannot use a stale location: "+row[0]);
  }
 }
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  normal();independentDialog();independentMain();independentMission();
  System.out.println("MarkerlessRelocationTest: "+checks+" checks passed; real pixels with synthetic absent targets and independently moved objects; no Android device execution");
 }
}
