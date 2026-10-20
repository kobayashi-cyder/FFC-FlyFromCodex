package jp.tapmarker;
import java.util.*;

/** Discrete contact points through the real executor with asynchronous DOWN/UP-shaped completions. */
public final class DiscreteTapPositionTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Tapper.Command command(float x,float y,long expiry){return new Tapper.Command("game",0,"play",new int[]{0,0,540,1200},new float[]{x,y},1,1,expiry,true);}
 public static void main(String[] args){
  Tapper.Command a=command(100.2f,200.7f,2000),b=command(100.3f,200.8f,3000);check(Arrays.equals(a.point(0),new float[]{100,201}),"fractional target is fixed to one integer-pixel contact");check(a.sameRoute(b),"subpixel recognition noise does not create continuous coordinate changes or restart the tapper");
  Tapper.Command edge=command(539.99f,1199.99f,2000);check(Arrays.equals(edge.point(0),new float[]{539,1199}),"rounding at the right/bottom edge stays inside the target");
  int[] window={30,40,570,1252};PhaseEngine e=new PhaseEngine();PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="main";e.phases.put(p.id,p);e.enter(p.id,0);ContinuousTapSession session=new ContinuousTapSession();session.arm(e,window,new float[]{300.25f,1018.75f});check(Arrays.equals(session.point(),new float[]{300,1019}),"main-session contact uses the same discrete position");
  TapperTest.Device d=new TapperTest.Device();Tapper tapper=d.tapper();tapper.update(a);d.until(0);check(Arrays.equals(d.points.get(0),new float[]{100,201}),"driver receives a single frozen contact point");tapper.update(command(110.8f,210.2f,3000));check(Arrays.equals(d.points.get(0),new float[]{100,201}),"a new target cannot move an in-flight press");d.until(5);
  check(d.maxPending==1,"next press waits for the preceding release/completion");check(d.points.size()>=2&&Arrays.equals(d.points.get(1),new float[]{111,210}),"next press jumps to the new point after release, with no interpolated positions");for(float[] point:d.points)check(Arrays.equals(point,new float[]{100,201})||Arrays.equals(point,new float[]{111,210}),"no contact is generated along the path between targets");check(d.starts.get(1)==1,"discrete contact does not add a fixed timing delay to fastest mode");
  System.out.println("DiscreteTapPositionTest: "+checks+" checks passed; integer-pixel contact, immutable in-flight press, serial release and unchanged requested fastest cadence; Android acceptance untested");
 }
}
