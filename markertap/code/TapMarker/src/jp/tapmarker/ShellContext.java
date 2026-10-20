package jp.tapmarker;
import android.content.Context;import java.lang.reflect.*;
/** Called in the shell process only; do not inherit the system context's attribution. */
final class ShellContext {
 static Context create()throws Exception{
  Class<?> threadClass=Class.forName("android.app.ActivityThread");
  Object thread=threadClass.getMethod("currentActivityThread").invoke(null);
  if(thread==null)thread=threadClass.getMethod("systemMain").invoke(null);
  Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
  Context packageContext=system.createPackageContext("com.android.shell",0);
  Class<?> contextClass=Class.forName("android.app.ContextImpl");
  Field loadedField=contextClass.getDeclaredField("mPackageInfo");loadedField.setAccessible(true);
  Object loaded=loadedField.get(packageContext);
  Method factory=contextClass.getDeclaredMethod("createAppContext",threadClass,Class.forName("android.app.LoadedApk"));factory.setAccessible(true);
  Context result=(Context)factory.invoke(null,thread,loaded);
  if(!"com.android.shell".equals(result.getPackageName())||!"com.android.shell".equals(result.getOpPackageName()))throw new IllegalStateException("シェルの実行コンテキストを作成できません");
  return result;
 }
}
