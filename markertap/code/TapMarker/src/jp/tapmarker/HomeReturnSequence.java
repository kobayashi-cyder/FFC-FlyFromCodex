package jp.tapmarker;

/** HOME and launch are separate, cancellable steps. Never launch over an unsettled HOME. */
final class HomeReturnSequence {
 static final long SETTLE_MS=300,HOME_DEADLINE_MS=1500;
 interface Host {
  boolean valid();
  long now();
  boolean home();
  boolean homeVisible();
  void launch();
  void failed(String reason);
  void later(Runnable task,long delay);
 }
 static void start(Host host,boolean viaHome){
  if(!host.valid())return;
  if(!viaHome){host.launch();return;}
  if(!host.home()){host.failed("ホーム操作が受理されないため復帰を再確認");return;}
  final long sent=host.now();
  host.later(()->settle(host,sent),SETTLE_MS);
 }
 static void settle(Host host,long sent){
  if(!host.valid())return;
  long elapsed=host.now()-sent;
  if(elapsed>=SETTLE_MS&&host.homeVisible()){host.launch();return;}
  if(elapsed>=HOME_DEADLINE_MS){host.failed("ホーム画面を確認できないため復帰を再確認");return;}
  host.later(()->settle(host,sent),100);
 }
 private HomeReturnSequence(){}
}
