package jp.tapmarker;
import java.util.*;
/** Stub field/interface shapes checked against AOSP Android 11/12; no device injection. */
public class DisplayTaskGuardTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static class Component {final String pkg;Component(String pkg){this.pkg=pkg;}public String getPackageName(){return pkg;}}
 public static class AppInfo {public int uid;AppInfo(int uid){this.uid=uid;}}
 public static class ActivityInfo {public AppInfo applicationInfo;ActivityInfo(int uid){applicationInfo=new AppInfo(uid);}}
 public static class StackInfo {public int displayId,userId;public boolean visible;public Component topActivity;StackInfo(int id,String pkg,int user,boolean visible){displayId=id;userId=user;this.visible=visible;topActivity=pkg==null?null:new Component(pkg);}}
 public static class TaskInfo {public int displayId,userId;public Component topActivity;public ActivityInfo topActivityInfo;}
 public static class RootTaskInfo extends TaskInfo {public boolean visible;RootTaskInfo(int id,String pkg,int uid,boolean visible){displayId=id;userId=uid/100000;this.visible=visible;topActivity=pkg==null?null:new Component(pkg);topActivityInfo=new ActivityInfo(uid);}}
 public interface OldTasks {List<?> getAllStackInfosOnDisplay(int display);}
 public interface NewTasks {List<?> getAllRootTaskInfosOnDisplay(int display);}
 public static class Legacy implements OldTasks {final Map<Integer,List<?>> windows=new HashMap<>();int calls,lastDisplay;public List<?> getAllStackInfosOnDisplay(int id){calls++;lastDisplay=id;return windows.get(id);}}
 public static class Modern implements NewTasks {final Map<Integer,List<?>> windows=new HashMap<>();int calls;boolean fail;public List<?> getAllRootTaskInfosOnDisplay(int id){calls++;if(fail)throw new IllegalStateException("binder failed");return windows.get(id);}}
 public static void main(String[] args)throws Exception{
  Legacy old=new Legacy();Modern current=new Modern();DisplayTaskGuard a=new DisplayTaskGuard(old,OldTasks.class),b=new DisplayTaskGuard(current,NewTasks.class);
  check(a.methodName.equals("getAllStackInfosOnDisplay")&&b.methodName.equals("getAllRootTaskInfosOnDisplay"),"Android 11/12 names resolved separately");
  for(int id=1;id<=24;id++){
   String pkg="game."+id;int uid=10000+id;old.windows.put(id,Arrays.asList(new StackInfo(id,pkg,0,true)));current.windows.put(id,Arrays.asList(new RootTaskInfo(id,pkg,uid,true)));
   check(a.accepts(id,uid,pkg)&&b.accepts(id,uid,pkg),"each virtual lane reads its own live app");
   check(old.lastDisplay==id,"Android 11 lookup never uses global or display-zero focus");
   check(!a.accepts(id,uid,"game.other")&&!b.accepts(id,uid,"game.other"),"foreign packages denied");
   check(!a.accepts(id,110000+id,pkg)&&!b.accepts(id,110000+id,pkg),"same package in a different user denied");
   check(!b.accepts(id,uid+100,pkg),"Android 12 includes the actual top activity UID");
  }
  old.windows.put(1,Arrays.asList(new StackInfo(1,"hidden",0,false),new StackInfo(1,"game.1",0,true)));
  check(a.accepts(1,10001,"game.1"),"invisible previous tasks do not block the current app");
  old.windows.put(1,Arrays.asList(new StackInfo(1,"browser",0,true),new StackInfo(1,"game.1",0,true)));
  check(!a.accepts(1,10001,"game.1"),"visible browser above the game cannot be skipped");
  old.windows.put(1,Arrays.asList(new StackInfo(0,"game.1",0,true)));check(!a.accepts(1,10001,"game.1"),"wrong display in a returned snapshot denied");
  old.windows.put(1,Arrays.asList(new StackInfo(1,null,0,true)));check(!a.accepts(1,10001,"game.1"),"missing top component denied");
  old.windows.put(1,Collections.emptyList());check(!a.accepts(1,10001,"game.1"),"removed display/task denied");
  old.windows.put(1,null);check(!a.accepts(1,10001,"game.1"),"null task query denied");
  old.windows.put(1,Arrays.asList((Object)null));check(!a.accepts(1,10001,"game.1"),"null task entry denied");
  RootTaskInfo root=new RootTaskInfo(1,"game.1",10001,true);root.topActivityInfo=null;current.windows.put(1,Arrays.asList(root));check(!b.accepts(1,10001,"game.1"),"missing Android 12 activity identity denied");
  root.topActivityInfo=new ActivityInfo(10001);root.topActivityInfo.applicationInfo=null;check(!b.accepts(1,10001,"game.1"),"missing Android 12 application identity denied");
  int before=old.calls;check(!a.accepts(0,10001,"game.1")&&!a.accepts(-1,10001,"game.1")&&!a.accepts(1,0,"game.1")&&!a.accepts(1,10001,null)&&old.calls==before,"invalid arguments stop before a binder call");
  current.fail=true;boolean failed=false;try{b.accepts(1,10001,"game.1");}catch(java.lang.reflect.InvocationTargetException denied){failed=true;}check(failed,"binder failure is propagated instead of reusing old identity");
  TargetedInput input=new TargetedInput(TargetedInputTest.LegacyManager.class,TargetedInputTest.Event.class,(id,uid)->a.accepts(id,uid,"game.1"));
  TargetedInputTest.Event event=new TargetedInputTest.Event();input.display(event,1);old.windows.put(1,Arrays.asList(new StackInfo(1,"game.1",0,true)));
  check(input.inject(event,1,10001),"legacy adapter plus real task guard can inject a confirmed app");int inputs=TargetedInputTest.LegacyManager.INSTANCE.calls;
  old.windows.put(1,Arrays.asList(new StackInfo(1,"browser",0,true)));check(!input.inject(event,1,10001)&&TargetedInputTest.LegacyManager.INSTANCE.calls==inputs,"guard rereads a changed live target before the next input");
  System.out.println("DisplayTaskGuardTest: "+checks+" checks passed; Android 11/12 API-shaped stubs, device injection untested");
 }
}
