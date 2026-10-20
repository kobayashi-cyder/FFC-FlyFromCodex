package jp.tapmarker;
/** All start controls share this ordering; virtual sessions never consult the phone's foreground app. */
final class AutomationRouter {
 static void control(Host host){start(host,!host.virtualSession());}
 interface Host {
  boolean automationRunning();
  boolean virtualSession();
  void stopAutomation();
  void startVirtualAutomation();
  void startSingleAutomation();
 }
 static void start(Host host,boolean toggle){
  if(host.automationRunning()){if(toggle)host.stopAutomation();return;}
  if(host.virtualSession()){host.startVirtualAutomation();return;}
  host.startSingleAutomation();
 }
}
