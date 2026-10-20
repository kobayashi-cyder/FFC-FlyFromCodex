package jp.tapmarker;
import java.lang.reflect.Method;import java.lang.reflect.InvocationTargetException;
/** Use atomic UID targeting when available; old Android requires a live display/app guard. */
final class TargetedInput {
 interface TargetGuard {boolean accepts(int displayId,int uid)throws Exception;}
 private final Object manager;private final Method inject,getDisplay,setDisplay;private final TargetGuard guard;final boolean legacy;
 TargetedInput(Class<?> managerType,Class<?> eventType)throws Exception{
  this(managerType,eventType,null);
 }
 TargetedInput(Class<?> managerType,Class<?> eventType,TargetGuard legacyGuard)throws Exception{
  Method method;boolean old=false;
  try{method=managerType.getMethod("injectInputEvent",eventType,int.class,int.class);}
  catch(NoSuchMethodException unsupported){if(legacyGuard==null)throw unsupported;method=managerType.getMethod("injectInputEvent",eventType,int.class);old=true;}
  inject=method;legacy=old;guard=legacyGuard;
  getDisplay=eventType.getMethod("getDisplayId");setDisplay=eventType.getMethod("setDisplayId",int.class);
  manager=managerType.getMethod("getInstance").invoke(null);
 }
 void display(Object event,int id)throws Exception{
  if(id<=0)throw new IllegalArgumentException("本体画面への入力を拒否");
  setDisplay.invoke(event,id);verify(event,id);
 }
 void verify(Object event,int id)throws Exception{
  if(id<=0||((Number)getDisplay.invoke(event)).intValue()!=id)throw new IllegalArgumentException("入力先の画面IDが一致しません");
 }
 boolean inject(Object event,int id,int uid)throws Exception{
  if(uid<=0)throw new IllegalArgumentException("入力先アプリが無効です");
  verify(event,id);
  if(legacy){if(!guard.accepts(id,uid))return false;verify(event,id);return Boolean.TRUE.equals(inject.invoke(manager,event,2));}
  try{return Boolean.TRUE.equals(inject.invoke(manager,event,2,uid));}
  catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof IllegalArgumentException&&InputTargetRecovery.changed(cause.getMessage()))throw new IllegalStateException(InputTargetRecovery.MARKER+" display="+id+" uid="+uid+" / "+cause.getMessage(),cause);throw failure;}
 }
}
