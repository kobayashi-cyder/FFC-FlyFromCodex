package jp.tapmarker;

/** Bounds backend cleanup even if a native display release never returns. */
final class OwnerDeathCleanup {
 static final long HARD_STOP_MS = 5000;
 interface Host {
  void scheduleHardStop(Runnable stop, long delayMillis);
  void cleanup();
  void terminate();
 }
 private OwnerDeathCleanup() {}
 static void run(Host host) {
  host.scheduleHardStop(host::terminate, HARD_STOP_MS);
  try { host.cleanup(); }
  finally { host.terminate(); }
 }
}
