package jp.tapmarker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
/**
 * Only the explicitly selected privileged virtual display option is routed
 * to the existing built-in, user-authorized ADB setup.  The old external
 * Shizuku path is not invoked as a fallback.
 */
public final class SafeEmbeddedOpenBridge {
 private static Object get(Object o,String name)throws Exception {
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){
   try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}
   catch(NoSuchFieldException ignored){}
  }
  throw new NoSuchFieldException(name);
 }
 public static boolean open(Object windows,boolean privileged) {
  if(!privileged)return false; // original unprivileged path
  try{
   Object pilot=get(get(windows,"s"),"pilot");
   Object wireless=get(pilot,"wireless");
   Method guide=wireless.getClass().getDeclaredMethod("guide",boolean.class);
   guide.setAccessible(true);
   guide.invoke(wireless,true);
  }catch(Exception|LinkageError fail) {
   // Never fall through to missing external privilege provider on an ABI error.
   try{
    Object pilot=get(get(windows,"s"),"pilot");
    Method note=pilot.getClass().getDeclaredMethod("note",String.class);
    note.setAccessible(true);note.invoke(pilot,"内蔵ADB接続の案内を開けません。通常画面を使用してください");
   }catch(Exception ignored){}
  }
  return true;
 }
 private SafeEmbeddedOpenBridge(){}
}
