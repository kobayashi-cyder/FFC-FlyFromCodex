package com.ffc.banc888.fly;

import android.os.SystemClock;

import org.json.JSONObject;

final class WebRecoveryGuard {
    private static final long WINDOW_MS = 30_000L;
    private static final int MAX_RECOVERIES = 3;

    private static long windowStartedAt = 0L;
    private static int attempts = 0;
    private static String lastReason = "";

    private WebRecoveryGuard() {}

    static synchronized boolean tryAcquire(String reason) {
        long now = SystemClock.elapsedRealtime();
        if (windowStartedAt == 0L || now - windowStartedAt > WINDOW_MS) {
            windowStartedAt = now;
            attempts = 0;
        }
        lastReason = reason == null ? "" : reason;
        if (attempts >= MAX_RECOVERIES) return false;
        attempts++;
        return true;
    }

    static synchronized void markHealthy() {
        attempts = 0;
        windowStartedAt = SystemClock.elapsedRealtime();
        lastReason = "";
    }

    static synchronized String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("attempts", attempts);
            o.put("max", MAX_RECOVERIES);
            o.put("windowMs", WINDOW_MS);
            o.put("windowStartedAtElapsedMs", windowStartedAt);
            o.put("lastReason", lastReason);
        } catch (Exception ignored) {}
        return o.toString();
    }
}
