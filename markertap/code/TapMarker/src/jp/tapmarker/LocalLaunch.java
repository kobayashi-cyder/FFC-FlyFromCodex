package jp.tapmarker;
/** Fixed bootstrap arguments, without accepting commands or another app's APK. */
final class LocalLaunch {
 static boolean validToken(String token){return token!=null&&token.matches("[a-f0-9]{32}");}
 static String command(String apk,String token){
  if(apk==null||!apk.matches("/data/app/[A-Za-z0-9_./+=~-]+/base\\.apk")||apk.contains("/../")||apk.contains("/./")||!validToken(token))throw new IllegalArgumentException("起動情報を確認できません");
  return "shell:CLASSPATH='"+apk+"' /system/bin/app_process /system/bin jp.tapmarker.VirtualStart '"+apk+"' "+token;
 }
}
