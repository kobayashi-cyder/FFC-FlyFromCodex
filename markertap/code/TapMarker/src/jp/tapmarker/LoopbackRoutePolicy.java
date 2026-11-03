package jp.tapmarker;
/**
 * Conservative, user-authorized local ADB route selection.
 * A last-known port is only recorded after the ADB Binder handshake succeeds.
 * This never starts adbd, scans ports, connects to another host, or grants shell.
 */
final class LoopbackRoutePolicy {
 static boolean validPort(int p){return p>0&&p<=65535;}
 static int eligiblePort(boolean previouslyPaired,int provenPort){
  return previouslyPaired&&validPort(provenPort)?provenPort:0;
 }
 static boolean canAttempt(boolean wifi,boolean paired,int provenPort){
  return wifi||eligiblePort(paired,provenPort)>0;
 }
 static boolean shouldDialCached(boolean paired,int provenPort){
  return eligiblePort(paired,provenPort)>0;
 }
 static boolean requireWifiDiscovery(boolean wifi){return wifi;}
}
