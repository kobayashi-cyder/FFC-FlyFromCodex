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

    static boolean awaitNativeReady(ActivityScenario<MainActivity> scenario) throws Exception {
        return awaitJs(scenario,
                "!!(window.AndroidVoice&&window.AndroidDiagnostics&&window.FFC_THREADS&&window.FFC_PROXY_AGENT)",
                30000);
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
            // Do not discard a legitimate asynchronous result after an arbitrary
            // two seconds and enqueue more evaluations behind the same busy UI.
            long remainingMs = Math.max(1L, end - System.currentTimeMillis());
            latch.await(remainingMs, TimeUnit.MILLISECONDS);
            if ("true".equals(value.get())) return true;
            Thread.sleep(150);
        }
        scenario.onActivity(activity -> {
            ViewGroup root = activity.findViewById(android.R.id.content);
            WebView webView = (WebView) root.getChildAt(0);
            webView.evaluateJavascript(
                    "JSON.stringify({ready:document.readyState,voice:typeof AndroidVoice,"
                            + "diagnostics:typeof AndroidDiagnostics==='undefined'?null:AndroidDiagnostics.status()})",
                    result -> android.util.Log.e("BANC888-WebView", "TEST timeout: " + expression + " actual: " + result));
        });
        return false;
    }
}
