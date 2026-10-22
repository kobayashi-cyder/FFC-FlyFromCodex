package jp.tapmarker;

/** Invoked only by this shell's registered owner Binder death recipient. */
final class ShellOwnerDeath {
 private ShellOwnerDeath() {}
 static void close(VirtualShell shell) {
  OwnerDeathCleanup.run(new OwnerDeathCleanup.Host() {
   public void scheduleHardStop(Runnable stop, long delayMillis) {
    Thread watchdog = new Thread(() -> {
     android.os.SystemClock.sleep(delayMillis);
     stop.run();
    }, "MarkerTap-owner-death-hard-stop");
    watchdog.setDaemon(true);
    watchdog.start();
   }
   public void cleanup() { shell.closeAll(); }
   public void terminate() {
    // This code runs in MarkerTap's backend, never in the external game.
    android.os.Process.killProcess(android.os.Process.myPid());
   }
  });
 }
}
