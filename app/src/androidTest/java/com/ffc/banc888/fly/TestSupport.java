package com.ffc.banc888.fly;

import android.Manifest;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class TestSupport {
    private TestSupport() {}

    static void grantMicrophone() throws Exception {
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand("pm grant com.ffc.banc888.fly " + Manifest.permission.RECORD_AUDIO)) {
            Thread.sleep(250);
        }
    }

    static boolean awaitJs(ActivityScenario<MainActivity> scenario, String expression, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> value = new AtomicReference<>("");
            scenario.onActivity(activity -> {
                ViewGroup root = activity.findViewById(android.R.id.content);
                WebView webView = (WebView) root.getChildAt(0);
                webView.evaluateJavascript(expression, result -> {
                    value.set(result);
                    latch.countDown();
                });
            });
            latch.await(2, TimeUnit.SECONDS);
            if ("true".equals(value.get())) return true;
            Thread.sleep(150);
        }
        return false;
    }
}
