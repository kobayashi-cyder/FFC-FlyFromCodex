package jp.tapmarker;
import java.util.*;

public final class RecordedAutomatonGeneratorTest {
 static int checks;
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 static RecordedAutomatonGenerator.Model model(int... sequence){RecordedAutomatonGenerator g=new RecordedAutomatonGenerator();long at=0;for(int kind:sequence)for(int i=0;i<4;i++){g.add(at,ObservationLearnerTest.frame(kind));at+=500;}return g.finish();}
 static PhaseEngine.Watch observe(PhaseEngine e,ObservationLearner.Frame frame,long at){Map<String,float[]> samples=ObservationLearnerTest.samples(e,frame);e.observe(samples,at);check(e.poll()==null,"one observation never authorizes input or transition");e.observe(samples,at+300);return e.poll();}
 static void synthetic(){
  RecordedAutomatonGenerator.Model m=model(0,1,0,1,0,2);check(m.states.size()==3,"recurring displays are exactly three states");check(m.edges.size()==3,"repeated transitions are deduplicated");check(m.edges.get(0).count==2,"edge recurrence retained");
  RecordedAutomatonGenerator.Result r=RecordedAutomatonGenerator.compile(m);PhaseEngine e=r.engine;check(r.pending==2&&e.recordingReviewRequired,"unregistered inputs remain a draft");check(e.validate().isEmpty()&&!e.validateStart().isEmpty(),"draft structure valid but execution blocked");check(e.runtimeCopy().recordingReviewRequired,"review gate survives runtime copy");
  for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)check(w.action==PhaseEngine.WAIT,"recording cannot invent any gesture");
  PhaseEngine.Watch placeholder=observe(e,m.states.get(0).frame,0);check(placeholder!=null&&placeholder.action==PhaseEngine.WAIT&&placeholder.nextPhase.contains("verify"),"editable draft placeholder checks source and enters visual verification without input");e.finish(placeholder,400);
  PhaseEngine.Watch arrival=observe(e,m.states.get(1).frame,500);check(arrival!=null&&arrival.nextPhase.endsWith("state:1"),"passive verification recognizes recorded next display");
  m.annotate(0,PhaseEngine.CENTER,new float[]{.31f,.72f},60,1);m.annotate(1,PhaseEngine.WAIT,null,300,-1);r=RecordedAutomatonGenerator.compile(m);e=r.engine;check(r.pending==0&&!e.recordingReviewRequired&&e.validateStart().isEmpty(),"reviewed model can start");check(r.verificationStates==1&&e.phases.size()==4,"one input receives a dedicated confirmation node");
  PhaseEngine.Watch input=observe(e,m.states.get(0).frame,1000);check(input!=null&&input.action==PhaseEngine.CENTER&&input.touchPoint[0]==.31f&&input.touchPoint[1]==.72f,"only explicitly registered point is selected");e.finish(input,1400);check(e.current.contains("verify"),"input completion does not count as visual success");e.observe(ObservationLearnerTest.samples(e,m.states.get(0).frame),1500);e.observe(ObservationLearnerTest.samples(e,m.states.get(0).frame),1800);check(e.poll()==null,"unchanged source cannot confirm an outcome");PhaseEngine.Watch confirm=observe(e,m.states.get(1).frame,2000);check(confirm!=null&&confirm.action==PhaseEngine.WAIT,"actual expected screen verifies gesture");
  PhaseEngine.Watch passive=e.phases.get("record:state:1").watches.get(0);check(passive.regionId.equals(input.regionId)&&Arrays.equals(passive.guardIds,input.guardIds),"destination and source reuse the same region set");
  m.annotate(0,PhaseEngine.CURVE,new float[]{.2f,.3f,.6f,.7f},680,1);r=RecordedAutomatonGenerator.compile(m);input=observe(r.engine,m.states.get(0).frame,3000);check(input.action==PhaseEngine.CURVE&&input.relativeCurve&&input.duration==680,"reviewed swipe preserves path and duration");
  try{m.annotate(0,PhaseEngine.CENTER,new float[]{1,.3f},50,1);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  try{m.annotate(1,PhaseEngine.CENTER,new float[]{.2f,.3f},50,2);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  check(RecordedAutomatonGenerator.interval(0,3600000,500)*599>=3600000,"long recordings use bounded sampling");
  RecordedAutomatonGenerator g=new RecordedAutomatonGenerator();for(int i=0;i<600;i++)g.add(i*500,ObservationLearnerTest.frame(0));try{g.add(300000,ObservationLearnerTest.frame(1));throw new AssertionError();}catch(IllegalStateException expected){checks++;}
  try{g.finish();throw new AssertionError();}catch(IllegalStateException expected){checks++;}
  g=new RecordedAutomatonGenerator();long at=0;for(int kind:new int[]{0,0,0,0,2,0,0,0,1,1,1,1}){g.add(at,ObservationLearnerTest.frame(kind));at+=500;}check(g.finish().states.size()==2,"single-frame transient is not a state");
  ObservationLearner.Frame a=ObservationLearnerTest.frame(0);int[] small=a.pixels.clone();for(int y=a.height/3;y<a.height*2/5;y++)for(int x=a.width/3;x<a.width/2;x++)small[y*a.width+x]=((x+y)%2==0?0xffe02040:0xfff090c0);ObservationLearner.Frame b=new ObservationLearner.Frame(a.width,a.height,small);g=new RecordedAutomatonGenerator();at=0;for(ObservationLearner.Frame f:new ObservationLearner.Frame[]{a,a,a,a,b,b,b,b}){g.add(at,f);at+=500;}m=g.finish();check(m.states.size()==2&&RecordedAutomatonGenerator.compile(m).engine.validate().isEmpty(),"small persistent button change uses settled peer, not transition frame");
  g=new RecordedAutomatonGenerator();for(int i=0;i<8;i++){ObservationLearner.Frame f=ObservationLearnerTest.frame(i<4?0:1);f.excluded=new float[]{0,0,1,1};g.add(i*500,f);}try{g.finish();throw new AssertionError();}catch(IllegalStateException expected){checks++;}
  m=model(0,1);m.states.get(1).frame=m.states.get(0).frame;m.states.get(1).peer=m.states.get(0).peer;try{RecordedAutomatonGenerator.compile(m);throw new AssertionError();}catch(IllegalStateException expected){checks++;}
 }
 static void heldOut()throws Exception{
  Video6687ReplayTest.load();RecordedAutomatonGenerator g=new RecordedAutomatonGenerator();long at=0;
  // Genuine held-out MP4 pixels, replayed steadily to isolate recognition from Android decoding.
  for(String id:new String[]{"30-native","111.8-native","30-native"}){VirtualPresetProfileTest.Image image=Video6687ReplayTest.images.get(id);ObservationLearner.Frame f=ObservationLearner.Frame.capture(image.width,image.height,image);f.excluded=new float[]{.32f,.37f,1,.61f};for(int i=0;i<4;i++){g.add(at,f);at+=500;}}
  RecordedAutomatonGenerator.Model m=g.finish();check(m.states.size()==2&&m.edges.size()==2,"real held-out displays merge recurrent dialog and distinguish missions");RecordedAutomatonGenerator.Result r=RecordedAutomatonGenerator.compile(m);check(r.engine.validate().isEmpty()&&r.pending==2,"real pixel generation yields valid shared-ROI draft");for(PhaseEngine.Region roi:r.engine.regions.values())check(ObservationLearner.usable(m.states.get(0).frame,roi.bounds),"overlay excluded from generated predicates");
 }
 public static void main(String[] args)throws Exception{synthetic();heldOut();System.out.println("RecordedAutomatonGeneratorTest: "+checks+" checks passed; recurrence, bounded sampling, draft gate, no guessed gesture, explicit tap/swipe and post-input confirmation, transient/ambiguous rejection, real 6687 pixel replay; Android file picker/decoder and device input untested");}
}
