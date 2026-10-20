package jp.tapmarker;
/** Persistent mode plus cancellable, bounded background retries. */
final class WirelessModePolicy {
 boolean automatic,paused,sessionRecovery;long retryAt;int failures;
 void mode(boolean enabled){automatic=enabled;paused=false;sessionRecovery=false;failures=0;retryAt=0;}
 boolean shouldConnect(boolean wifi,boolean backend,boolean working,long now){return (automatic||sessionRecovery)&&!paused&&wifi&&!backend&&!working&&now>=retryAt;}
 void attempted(long now){retryAt=now+20000;}
 void failed(long now){failures=Math.min(failures+1,4);retryAt=now+Math.min(60000,10000L*(1L<<failures));}
 void connected(){failures=0;retryAt=0;sessionRecovery=false;}
 void requestSessionRecovery(){if(!paused){sessionRecovery=true;retryAt=0;}}
 void cancelSessionRecovery(){sessionRecovery=false;}
 boolean cancelForWifiLoss(boolean wifi,boolean working,boolean quiet,boolean backend){return !wifi&&working&&quiet&&!backend;}
 void pause(){paused=true;sessionRecovery=false;}
 void resume(){paused=false;retryAt=0;}
}
