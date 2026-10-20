package jp.tapmarker;
import java.lang.reflect.*;import java.util.*;import java.util.concurrent.ConcurrentHashMap;

/** Android 11 StackInfo / Android 12 RootTaskInfo, obtained for one owned display only.
 * Old input APIs cannot check a UID atomically. Read the live top visible task
 * before every event and refuse absent, foreign, or unreadable task information.
 */
final class DisplayTaskGuard {
 private final Object service;private final Method tasks;final String methodName;
 private final Map<Class<?>,Fields> fields=new ConcurrentHashMap<>();
 DisplayTaskGuard(Object service,Class<?> serviceType)throws Exception{
  this.service=service;Method method;
  try{method=serviceType.getMethod("getAllRootTaskInfosOnDisplay",int.class);}
  catch(NoSuchMethodException old){method=serviceType.getMethod("getAllStackInfosOnDisplay",int.class);}
  tasks=method;methodName=method.getName();
 }
 static final class Fields {
  final Field display,user,visible,top,activity;
  Fields(Class<?> type)throws Exception{
   display=type.getField("displayId");user=type.getField("userId");visible=type.getField("visible");top=type.getField("topActivity");
   Field info;try{info=type.getField("topActivityInfo");}catch(NoSuchFieldException android11){info=null;}activity=info;
  }
 }
 boolean accepts(int id,int uid,String pkg)throws Exception{
  if(id<=0||uid<=0||pkg==null||pkg.isEmpty())return false;
  Object result=tasks.invoke(service,id);if(!(result instanceof List))return false;
  // AOSP returns tasks in display z-order, from top to bottom. Do not select a
  // lower matching task when another app is visible above it.
  for(Object task:(List<?>)result){
   if(task==null)return false;Class<?> type=task.getClass();Fields f=fields.get(type);if(f==null){f=new Fields(type);fields.put(type,f);}
   if(f.display.getInt(task)!=id)return false;if(!f.visible.getBoolean(task))continue;
   if(f.user.getInt(task)!=uid/100000)return false;
   Object component=f.top.get(task);if(component==null)return false;
   Object packageName=component.getClass().getMethod("getPackageName").invoke(component);if(!pkg.equals(packageName))return false;
   if(f.activity!=null){Object activity=f.activity.get(task);if(activity==null)return false;Object app=activity.getClass().getField("applicationInfo").get(activity);if(app==null||app.getClass().getField("uid").getInt(app)!=uid)return false;}
   return true;
  }
  return false;
 }
}
