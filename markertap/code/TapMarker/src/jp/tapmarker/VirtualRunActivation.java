package jp.tapmarker;
/** Starts a prepared display session without cancelling its preparation or inputs. */
final class VirtualRunActivation {
 interface Host {
  boolean running();
  void preparePhysical();
  boolean arm();
  void activate();
 }
 static boolean start(Host host){
  if(host.running())return true;
  host.preparePhysical();
  if(!host.arm())return false;
  host.activate();return true;
 }
 static boolean manualAllowed(boolean running,boolean preparing){return !running&&!preparing;}
}
