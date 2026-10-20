package jp.tapmarker;
import java.util.*;
public final class LearnedLayoutTest {
 static int checks;static void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
 static int uniquePixel(float u,float v){
  int r=(int)(30+u*70),g=(int)(45+v*95),b=(int)(70+u*v*90);
  if(u>.15f&&u<.85f&&v>.1f&&v<.23f){r=220;g=170;b=80;if((u>.25f&&u<.32f&&v>.12f&&v<.2f)||(u>.45f&&u<.65f&&v>.145f&&v<.18f)||(u>.73f&&u<.78f&&v>.18f)){r=45;g=70;b=150;}}
  if(Math.pow((u-.5)/.23,2)+Math.pow((v-.75)/.105,2)<1){r=55;g=190;b=95;if((u>.36&&u<.45&&v>.72&&v<.78)||(u>.53&&u<.66&&v>.73&&v<.76)){r=240;g=250;b=245;}}
  return 0xff000000|r<<16|g<<8|b;
 }
 public static void main(String[] args){
  ObservationLearner.Frame a=ObservationLearnerTest.frame(0),b=ObservationLearnerTest.frame(1);ObservationLearner learner=new ObservationLearner();learner.add(ObservationLearnerTest.step(a,b,.33f,.75f));PhaseEngine e=learner.compile(true).engine;LearnedLayout layout=new LearnedLayout();
  Map<String,float[]> samples=layout.sample(e,a.width,a.height,(x,y)->a.pixels[y*a.width+x],0);check(samples.size()==e.activeRegions().size(),"only active primary and auxiliary regions sampled");
  PhaseEngine.Watch w=e.phases.get(e.initial).watches.get(0);float[] local=LearnedInput.local(w,e.regions.get(w.regionId),layout,720,1600);check(Math.abs(local[0]-237.6f)<.01&&local[1]==1200,"tap scales to the actual window size");float[] offset=LearnedInput.offset(local,30,860);check(offset[0]>267&&offset[1]==2060,"split-screen offset applied once");check(LearnedInput.inside(offset,30,860,720,1600),"offset remains in its own window");
  Map<String,float[]> unknown=layout.sample(e,b.width,b.height,(x,y)->b.pixels[y*b.width+x],1000);check(unknown.isEmpty(),"unknown display supplies no actionable match");check(layout.point(w.touchPoint,720,1600)==null,"unknown display disables tapping");
  samples=layout.sample(e,a.width,a.height,(x,y)->a.pixels[y*a.width+x],1100);check(!samples.isEmpty(),"returning display resumes without restarting");
  LearnedLayout nativeLayout=new LearnedLayout();Map<String,float[]> nativeSamples=nativeLayout.sample(e,320,720,(x,y)->ObservationLearnerTest.pixel(x,y,0,320,720),0);check(!nativeSamples.isEmpty(),"templates recorded at reduced resolution match native window pixels");
  LearnedLayout calibrated=new LearnedLayout();final float sx=1.04f,sy=1.12f,left=(1-sx)/2,top=-.04f;
  Map<String,float[]> corrected=calibrated.sample(e,320,720,(x,y)->{float u=(x/320f-left)/sx,v=(y/720f-top)/sy;return u<0||u>=1||v<0||v>=1?0xff000000:ObservationLearnerTest.pixel((int)(u*320),(int)(v*720),0,320,720);},0);
  check(corrected.isEmpty()&&calibrated.status.contains("候補"),"ambiguous repeated patterns hold instead of guessing a transform");check(calibrated.point(w.touchPoint,320,720)==null,"ambiguous calibration cannot produce input coordinates");
  ObservationLearner.Frame unique=ObservationLearner.Frame.capture(320,720,(x,y)->uniquePixel(x/320f,y/720f));ObservationLearner distinctLearner=new ObservationLearner();distinctLearner.add(ObservationLearnerTest.step(unique,b,.5f,.75f));PhaseEngine distinct=distinctLearner.compile(true).engine;LearnedLayout fitted=new LearnedLayout();
  Map<String,float[]> fittedSamples=fitted.sample(distinct,320,720,(x,y)->{float u=(x/320f-left)/sx,v=(y/720f-top)/sy;return u<0||u>=1||v<0||v>=1?0xff000000:uniquePixel(u,v);},0);
  check(!fittedSamples.isEmpty(),"distinct local anchors absorb content insets and scale changes");float[] fittedPoint=fitted.point(new float[]{.5f,.75f},320,720);check(Math.abs(fittedPoint[0]-(left+.5f*sx)*320)<12&&Math.abs(fittedPoint[1]-(top+.75f*sy)*720)<14,"input uses the same calibrated transform as its condition");
  w.action=PhaseEngine.CURVE;w.relativeCurve=true;w.curve=new float[]{.2f,.25f,.7f,.8f};float[] curve=LearnedInput.local(w,e.regions.get(w.regionId),layout,400,800);check(curve[0]==80&&curve[1]==200&&curve[2]==280&&curve[3]==640,"curve scales both axes");
  w.action=PhaseEngine.MARKERS;w.markerIds=new String[]{"one","two"};w.markerPoints=new float[]{.2f,.3f,.8f,.7f};float[] points=LearnedInput.local(w,e.regions.get(w.regionId),layout,400,800);check(points.length==4&&points[3]==560,"multiple markers share corrected local coordinates");
  check(!LearnedInput.inside(new float[]{Float.NaN,10},0,0,400,800),"NaN points rejected");check(!LearnedInput.inside(new float[]{400,10},0,0,400,800),"off-window points rejected");
  w.action=PhaseEngine.CENTER;w.condition=PhaseEngine.CHANGE;w.guardIds=new String[0];w.guardReferences=new float[0][];e.enter(e.initial,0);LearnedLayout changeLayout=new LearnedLayout();Map<String,float[]> baseline=changeLayout.sample(e,a.width,a.height,(x,y)->a.pixels[y*a.width+x],0);check(!baseline.isEmpty(),"edited change rules sample the active region without matching the old image");e.observe(baseline,0);Map<String,float[]> changed=changeLayout.sample(e,b.width,b.height,(x,y)->b.pixels[y*b.width+x],100);e.observe(changed,100);e.observe(changed,200);check(e.poll()==w,"edited change rule fires on a stable changed display");
  w.condition=PhaseEngine.DISAPPEAR;w.reference=a.sample(e.regions.get(w.regionId).bounds);PhaseEngine.Region guard=new PhaseEngine.Region();guard.id="guard-test";guard.name="guard";guard.bounds=new float[]{.2f,.18f,.8f,.31f};e.regions.put(guard.id,guard);w.guardIds=new String[]{guard.id};w.guardReferences=new float[][]{a.sample(guard.bounds)};e.enter(e.initial,0);Map<String,float[]> absent=new HashMap<>();absent.put(w.regionId,b.sample(e.regions.get(w.regionId).bounds));absent.put(guard.id,b.sample(guard.bounds));e.observe(absent,0);e.observe(absent,100);check(e.poll()==null,"disappearance cannot fire merely because an auxiliary condition is absent");
  GesturePath path=new GesturePath(new float[]{0,0,100,0,100,300},401,401,1000);float[] halfway=path.position(.5f);check(halfway[0]==100&&halfway[1]==100,"virtual curves use arc-length timing");check(path.position(1)[1]==300,"curve reaches its final point");
  try{new GesturePath(new float[]{10,10,500,10},400,800,100);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  try{new GesturePath(new float[]{10,10},400,800,10001);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  System.out.println("PASS "+checks+" learned window and targeted curve input checks");
 }
}
