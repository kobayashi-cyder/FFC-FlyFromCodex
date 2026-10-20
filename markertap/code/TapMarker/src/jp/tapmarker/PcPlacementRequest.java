package jp.tapmarker;

/** One phone selection is claimed once; cancellation invalidates late PC replies. */
final class PcPlacementRequest {
 static final long TIMEOUT_MS=120000;
 String id="",owner="",state="idle",error="";String[] packages=new String[0];long createdAt;
 void request(String next,String[] targets,long now){
  if(!PcRunLease.validToken(next)||targets==null||targets.length==0||targets.length>32)throw new IllegalArgumentException("PC配置の対象が無効です");
  java.util.HashSet<String> seen=new java.util.HashSet<>();
  for(String pkg:targets)if(!PcRunLease.validPackage(pkg)||!seen.add(pkg))throw new IllegalArgumentException("PC配置の対象が無効です");
  id=next;packages=targets.clone();owner="";error="";state="waiting";createdAt=now;
 }
 boolean expire(long now){if(("waiting".equals(state)||"claimed".equals(state))&&now-createdAt>=TIMEOUT_MS){state="expired";error="PC接続待ちが終了しました。配置方式の「PC」を選び直してください";return true;}return false;}
 String[] take(String expected,String token,long now){expire(now);if(!id.equals(expected)||!"waiting".equals(state)||!PcRunLease.validToken(token))return null;owner=token;state="claimed";return packages.clone();}
 boolean matches(String expected,String token,String[] targets,long now){expire(now);return id.equals(expected)&&owner.equals(token)&&"claimed".equals(state)&&java.util.Arrays.equals(packages,targets);}
 boolean finish(String expected,String token,String issue){if(!id.equals(expected)||!owner.equals(token)||!"claimed".equals(state))return false;error=issue==null?"":issue;state=error.isEmpty()?"accepted":"failed";return true;}
 boolean cancel(String reason){if(!"waiting".equals(state)&&!"claimed".equals(state))return false;state="cancelled";error=reason;return true;}
}
