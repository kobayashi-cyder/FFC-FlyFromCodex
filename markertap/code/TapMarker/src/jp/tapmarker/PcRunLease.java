package jp.tapmarker;

/** Ownership follows a particular virtual-window generation, never a later phone run. */
final class PcRunLease {
 static final long TIMEOUT_MS=20000;
 String token=""; int generation=-1; long heartbeat; boolean expired;
 static boolean validToken(String value){return value!=null&&value.matches("[a-f0-9]{32}");}
 static boolean validPackage(String value){return value!=null&&value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")&&!"jp.tapmarker.lite".equals(value);}
 void prepare(String value,long now){if(!validToken(value))throw new IllegalArgumentException("PCの接続情報が無効です");if(!token.isEmpty()&&!token.equals(value))throw new IllegalStateException("別のPC接続が使用中です");token=value;heartbeat=now;expired=false;}
 void started(int value){generation=value;}
 boolean matches(String value){return !token.isEmpty()&&token.equals(value);}
 boolean owns(String value,int current){return matches(value)&&generation>=0&&generation==current;}
 void beat(String value,long now){if(!matches(value))throw new SecurityException("このPCが開始した処理ではありません");heartbeat=now;}
 boolean timedOut(long now){return !token.isEmpty()&&!expired&&now-heartbeat>TIMEOUT_MS;}
 void clear(){token="";generation=-1;heartbeat=0;expired=false;}
}
