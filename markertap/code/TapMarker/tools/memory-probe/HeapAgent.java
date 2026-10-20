package jp.tapmarker;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;
public final class HeapAgent {
 static Instrumentation meter;
 public static void premain(String options,Instrumentation instrumentation){meter=instrumentation;}
 static String size(Object...roots)throws Exception{
  IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();ArrayDeque<Object> queue=new ArrayDeque<>();for(Object root:roots)if(root!=null)queue.add(root);
  long total=0,vectors=0,count=0;
  while(!queue.isEmpty()){
   Object object=queue.removeLast();if(seen.put(object,Boolean.TRUE)!=null||object instanceof Class)continue;
   long bytes=meter.getObjectSize(object);total+=bytes;count++;Class<?> type=object.getClass();
   if(object instanceof float[]&&((float[])object).length>=768)vectors+=bytes;
   if(type.isArray()){
    if(!type.getComponentType().isPrimitive())for(Object child:(Object[])object)if(child!=null)queue.add(child);
   }else for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Field field:c.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()){
    field.setAccessible(true);Object child=field.get(object);if(child!=null)queue.add(child);
   }
  }
  return "total="+total+" template_vectors="+vectors+" non_template_objects="+(total-vectors)+" objects="+count;
 }
}
