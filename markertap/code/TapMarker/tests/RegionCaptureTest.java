package jp.tapmarker;
import java.nio.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class RegionCaptureTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static ByteBuffer plane(int w,int h,int row,int ps,int base,Random random,int[] expected){
  ByteBuffer b=ByteBuffer.allocateDirect(base+(h-1)*row+(w-1)*ps+4);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=0xff000000|random.nextInt(0x1000000);expected[y*w+x]=c;int p=base+y*row+x*ps;b.put(p,(byte)(c>>16));b.put(p+1,(byte)(c>>8));b.put(p+2,(byte)c);b.put(p+3,(byte)255);}
  b.position(base);return b;
 }
 static void crop(){
  Random random=new Random(119);
  for(int trial=0;trial<100;trial++){
   int w=3+random.nextInt(60),h=3+random.nextInt(80),ps=4+trial%4,row=w*ps+trial%17,base=trial%9;int[] expected=new int[w*h];
   ByteBuffer data=plane(w,h,row,ps,base,random,expected);RgbaRegionSource source=new RgbaRegionSource(data,w,h,row,ps,null);
   int x=random.nextInt(w),y=random.nextInt(h),cw=1+random.nextInt(w-x),ch=1+random.nextInt(h-y),stride=cw+3;int[] out=new int[stride*ch];Arrays.fill(out,123);
   source.read(x,y,cw,ch,out,stride);
   for(int j=0;j<ch;j++){for(int i=0;i<cw;i++)check(out[j*stride+i]==expected[(y+j)*w+x+i],"native ROI has exact channels");for(int i=cw;i<stride;i++)check(out[j*stride+i]==123,"destination padding remains untouched");}
   check(source.copiedPixels==(long)cw*ch&&data.position()==base,"only crop pixels copied and source position retained");
   data.limit(data.limit()-1);boolean refused=false;try{new RgbaRegionSource(data,w,h,row,ps,null);}catch(IllegalArgumentException correct){refused=true;}check(refused,"truncated last row is refused");
  }
  ByteBuffer translucent=ByteBuffer.wrap(new byte[]{16,32,48,(byte)128,1,2,3,0});AtomicInteger alpha=new AtomicInteger();
  RgbaRegionSource source=new RgbaRegionSource(translucent,2,1,8,4,(bytes,w,h,out,stride)->{check(bytes.length==w*h*4&&bytes[0]==16,"only cropped RGBA bytes reach platform converter");alpha.incrementAndGet();out[0]=0x80204060;if(w==2)out[1]=0;});
  int[] out=new int[4];source.read(0,0,2,1,out,4);check(alpha.get()==1&&out[0]==0x80204060&&out[1]==0,"alpha conversion output is preserved, including dirty transparent RGB");
 }
 static void regions(){
  RegionPlan.Builder b=new RegionPlan.Builder(1080,2424,"display:11:state:1");b.add(20,30,32,32);b.add(990,2300,40,40);RegionPlan plan=b.build();
  check(plan.contains(21,31)&&plan.contains(1029,2339),"separated ROI endpoints retained");check(!plan.contains(540,1200),"space between regions is not materialized");
  RegionPlan.Builder expanded=new RegionPlan.Builder(plan);expanded.add(400,100,32,32);
  check(!plan.contains(401,101)&&expanded.build().contains(401,101),"helper dependency expansion leaves original plan immutable");
  check(plan.coveredPixels()<1080L*2424/100,"small disjoint regions stay small");
  check(!plan.sameCoverage(expanded.build())&&!plan.previewKey.equals(expanded.build().previewKey),"runtime layout/helper dependency movement changes render identity");
  PhaseEngine engine=new PhaseEngine();PhaseEngine.Phase phase=new PhaseEngine.Phase();phase.id="work";engine.phases.put(phase.id,phase);
  for(int i=0;i<3;i++){PhaseEngine.Region region=new PhaseEngine.Region();region.id="r"+i;region.name=region.id;region.bounds=new float[]{i*.3f,.1f,i*.3f+.05f,.15f};engine.regions.put(region.id,region);}
  PhaseEngine.Watch watch=new PhaseEngine.Watch();watch.id="check";watch.regionId="r0";watch.guardIds=new String[]{"r1"};phase.watches.add(watch);engine.enter("work",1000);
  RegionPlan graph=RegionPlan.regions(1080,2424,"graph:work",engine.activeRegions());
  check(graph.contains(10,250)&&graph.contains(330,250)&&!graph.contains(660,250),"automaton plan selects current target and guard, excludes inactive region");
  String oldKey=graph.previewKey;engine.regions.get("r0").bounds=new float[]{.8f,.1f,.85f,.15f};
  check(!RegionPlan.regions(1080,2424,"graph:work",engine.activeRegions()).previewKey.equals(oldKey)&&graph.contains(10,250),"mutable graph bounds produce a new identity without mutating in-flight plan");
  RegionPlan.Builder edges=new RegionPlan.Builder(65,67,"edges");edges.add(64,66,4,4);check(edges.build().coveredPixels()==3&&edges.build().contains(64,66),"last native tile clips at exact screen dimensions");
 }
 static void circuit(){
  int w=1080,h=2424;int[] oracle=new int[w*h];ByteBuffer data=plane(w,h,w*4+16,4,7,new Random(91),oracle);
  RgbaRegionSource rgba=new RgbaRegionSource(data,w,h,w*4+16,4,null);long[] copied={0};RegionPlan.Builder dependencies=new RegionPlan.Builder(w,h,"native");
  TilePixels tiles=new TilePixels(w,h,(x,y,cw,ch,p,stride)->{rgba.read(x,y,cw,ch,p,stride);dependencies.add(x,y,cw,ch);copied[0]+=(long)cw*ch;},64);
  FrameCircuit circuit=new FrameCircuit(()->1000);FrameCircuit.Frame[] leak={null};FrameSampleCache[] cache={null};
  List<Runnable> ui=new ArrayList<>();float[][] regions={{.01f,.02f,.07f,.06f},{.9f,.9f,.96f,.94f}};
  circuit.run(new FrameCircuit.Request(()->true,workspace->new FrameCircuit.Input(w,h,1000,tiles),frame->{
   leak[0]=frame;cache[0]=(FrameSampleCache)FrameSampleCache.wrap(w,h,frame);
   for(float[] r:regions)check(Arrays.equals(RegionSampler.sample(w,h,r,new FramePixels.Raster(w,h,oracle,1000)),RegionSampler.sample(w,h,r,cache[0])),"recognition features match full native raster exactly");
   check(frame.get(400,200)==oracle[200*w+400],"graph-external helper reads actual source pixels");
   return ()->{};
  },ui::add,error->{throw new AssertionError(error);}));
  check(circuit.workspace.allocations==0&&circuit.retainedBytes==0,"ROI input allocates no full-frame circuit workspace");
  check(copied[0]<(long)w*h/20&&tiles.tiles.size()<=64,"native copy volume and tile retention are bounded");
  check(dependencies.build().contains(400,200)&&!dependencies.build().contains(500,1200),"dynamic helper tiles are recorded without filling the gap");
  boolean refused=false;try{leak[0].get(1,1);}catch(IllegalStateException correct){refused=true;}check(refused,"native frame lease revoked before UI publication");
  refused=false;try{RegionSampler.sample(w,h,regions[0],cache[0]);}catch(IllegalStateException correct){refused=true;}check(refused,"cached features cannot bypass lease revocation");
  ui.get(0).run();
  FrameCircuit stale=new FrameCircuit(()->3000);AtomicInteger reads=new AtomicInteger(),failures=new AtomicInteger();
  stale.run(new FrameCircuit.Request(()->true,workspace->new FrameCircuit.Input(w,h,1000,(x,y)->{reads.incrementAndGet();return 0;}),frame->{throw new AssertionError("stale native frame analyzed");},Runnable::run,error->failures.incrementAndGet()));
  check(reads.get()==0&&failures.get()==1,"stale native source cannot authorize analysis");
  System.out.println("Region copy experiment: "+copied[0]+" pixels / "+((long)w*h)+" native pixels; Java workspace="+circuit.retainedBytes+"; synthetic workload, not device performance");
 }
 static void preview(){
  RegionPlan.Builder b=new RegionPlan.Builder(128,256,"display11:state1");b.add(0,0,32,32);b.add(96,224,32,32);RegionPlan plan=b.build();AtomicInteger reads=new AtomicInteger();
  RegionSampler.Pixels source=(x,y)->{check(plan.contains(x,y),"preview never reads outside dependency tiles");reads.incrementAndGet();return 0xff000000|x<<8|y;};
  RegionPreviewData first=RegionPreviewData.capture(plan,128,1000,source);RegionPreviewData.Cache cache=new RegionPreviewData.Cache();
  check(cache.resetFor(first),"first frame resets cache");int changed=0;for(RegionPreviewData.Patch p:first.patches)if(cache.update(p))changed++;
  check(first.patches.size()==2&&changed==2&&reads.get()==2048,"disjoint preview avoids full-screen bitmap and gap reads");
  Map<String,int[]> identity=new HashMap<>(cache.pixels);RegionPreviewData same=RegionPreviewData.capture(plan,128,1100,source);check(!cache.resetFor(same),"same geometry/state retains preview");
  for(RegionPreviewData.Patch p:same.patches)check(!cache.update(p)&&cache.pixels.get(p.key())==identity.get(p.key()),"fresh identical pixels keep bitmap backing");
  RegionPreviewData moved=RegionPreviewData.capture(plan,128,1200,(x,y)->x==2&&y==3?0xffffffff:0xff000000|x<<8|y);
  changed=0;for(RegionPreviewData.Patch p:moved.patches)if(cache.update(p))changed++;check(changed==1,"single dirty region updates one display patch");
  RegionPlan.Builder changedLayout=new RegionPlan.Builder(plan);changedLayout.add(40,40,32,32);
  check(cache.resetFor(RegionPreviewData.capture(changedLayout.build(),128,1250,(x,y)->0xff112233))&&cache.pixels.isEmpty(),"helper/layout tile changes clear stale display patches");
  RegionPlan next=RegionPlan.full(128,256,"display12:state2");check(cache.resetFor(RegionPreviewData.capture(next,64,1300,(x,y)->0xff112233))&&cache.pixels.isEmpty(),"state/display/resize clears retained image history");
  int count=0;RegionPlan.Builder narrow=new RegionPlan.Builder(128,256,"narrow");narrow.add(1,1,1,1);
  RegionPreviewData masked=RegionPreviewData.capture(narrow.build(),64,1400,(x,y)->0xff123456);for(RegionPreviewData.Patch p:masked.patches)count+=p.present.cardinality();check(count==256,"only included source tiles generate preview pixels at reduced display scale");
  check(plan.contains(5,5)&&!plan.contains(64,128),"preview work cannot change native geometry or original plan");
 }
 public static void main(String[] args){crop();regions();circuit();preview();System.out.println("RegionCaptureTest: "+checks+" checks passed; crop/stride/alpha delegation/native features/leases/dirty patches; Android ImageReader and GPU not exercised");}
}
