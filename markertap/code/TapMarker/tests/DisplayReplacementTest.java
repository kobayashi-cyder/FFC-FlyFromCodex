package jp.tapmarker;
import java.util.*;

public final class DisplayReplacementTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Host implements DisplayReplacement.Host {
  final List<String> calls=new ArrayList<>();final Set<Integer> displays=new HashSet<>(Arrays.asList(7,8));
  boolean valid=true,failLaunch;int cancelAt,step;
  void advance(){if(++step==cancelAt)valid=false;}
  public void release(int id){calls.add("release:"+id);displays.remove(id);advance();}
  public int create(){calls.add("create");displays.add(42);advance();return 42;}
  public void launch(int id)throws Exception{calls.add("launch:"+id);advance();if(failLaunch)throw new Exception("launch rejected");}
 }
 public static void main(String[] args)throws Exception{
  Host good=new Host();int id=DisplayReplacement.replace(7,()->good.valid,good);
  check(id==42&&good.displays.equals(new HashSet<>(Arrays.asList(8,42))),"one display replaced, another retained");
  check(good.calls.equals(Arrays.asList("release:7","create","launch:42")),"release/create/launch order is explicit");
  for(int step=1;step<=3;step++){
   Host cancelled=new Host();cancelled.cancelAt=step;boolean rejected=false;
   try{DisplayReplacement.replace(7,()->cancelled.valid,cancelled);}catch(IllegalStateException expected){rejected=true;}
   check(rejected,"stop checked between every mutation: "+step);
   check(cancelled.displays.equals(Collections.singleton(8)),"cancelled replacement leaks no new display or affects another lane: "+step);
  }
  Host early=new Host();early.valid=false;try{DisplayReplacement.replace(7,()->early.valid,early);throw new AssertionError("cancel");}catch(IllegalStateException expected){}
  check(early.calls.isEmpty()&&early.displays.size()==2,"pre-dispatch stop leaves both displays untouched");
  Host failed=new Host();failed.failLaunch=true;try{DisplayReplacement.replace(7,()->failed.valid,failed);throw new AssertionError("launch");}catch(Exception expected){}
  check(failed.displays.equals(Collections.singleton(8))&&failed.calls.contains("release:42"),"failed launch releases its new display");
  Host physical=new Host();try{DisplayReplacement.replace(0,()->true,physical);throw new AssertionError("physical");}catch(IllegalArgumentException expected){}
  check(physical.calls.isEmpty(),"physical display is never released");
  System.out.println("DisplayReplacementTest: "+checks+" checks; actual replacement orchestrator with fake displays, cancellation, failed launch and lane isolation; Android lifecycle untested");
 }
}
