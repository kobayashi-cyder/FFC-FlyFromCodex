package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class BridgeIntegrationTest {
    private boolean awaitJs(ActivityScenario<MainActivity> scenario, String expression, long timeoutMs) throws Exception {
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
            latch.await(3, TimeUnit.SECONDS);
            if ("true".equals(value.get())) return true;
            Thread.sleep(350);
        }
        return false;
    }

    @Test
    public void nativeBridgesAndAgentRuntimeAreActuallyLinked() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(awaitJs(scenario,
                    "!!(window.FFC_THREADS&&window.FFC_PROXY_AGENT&&window.FFC_CAPABILITIES&&window.FFCConversationOutput)", 30000));
            assertTrue(awaitJs(scenario,
                    "typeof AndroidVoice!=='undefined'&&typeof AndroidFiles!=='undefined'&&typeof AndroidResearch!=='undefined'&&typeof AndroidDev!=='undefined'", 10000));
            assertTrue(awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).threadRouter===true", 10000));
            assertTrue(awaitJs(scenario,
                    "JSON.parse(AndroidDev.status()).canLive===true", 10000));
            assertTrue(awaitJs(scenario,
                    "window.FFCConversationOutput.speech({threadIds:[1],reason:'same',confidence:1},'こんにちは',window.FFCThreadCore.excelCode,{includeRoute:false})==='こんにちは'", 10000));
        }
    }
}
