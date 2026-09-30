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
                    "!!(window.FFC_THREADS&&window.FFC_PROXY_AGENT&&window.FFC_CAPABILITIES&&window.FFCConversationOutput&&window.FFC_WEBVIEW_RUNTIME&&window.AndroidRuntime)", 30000));

            assertTrue(awaitJs(scenario,
                    "typeof AndroidVoice!=='undefined'&&typeof AndroidFiles!=='undefined'&&typeof AndroidResearch!=='undefined'&&typeof AndroidDev!=='undefined'&&typeof AndroidDiagnostics!=='undefined'", 10000));

            assertTrue(awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).threadRouter===true", 10000));

            assertTrue(awaitJs(scenario,
                    "JSON.parse(AndroidDiagnostics.status()).shellVersion===70&&JSON.parse(AndroidDiagnostics.status()).bridgeSchema===4&&JSON.parse(AndroidDiagnostics.status()).webShell.runtimeVersion===7", 10000));

            assertTrue(awaitJs(scenario,
                    "JSON.parse(__BancVoice.status('wrong-token')).error==='native bridge denied'", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){window.__bancRuntimePingOk=false;AndroidRuntime.ping().then(function(r){window.__bancRuntimePingOk=!!(r&&r.ok)});return true})()", 10000));
            assertTrue(awaitJs(scenario,
                    "window.__bancRuntimePingOk===true", 10000));

            assertTrue(awaitJs(scenario,
                    "window.FFCConversationOutput.speech({threadIds:[1],reason:'same',confidence:1},'こんにちは',window.FFCThreadCore.excelCode,{includeRoute:false})==='こんにちは'", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){var x=document.getElementById('flyVoiceSpeak');if(x)x.checked=false;return AndroidDiagnostics.simulateVoiceResult('こんにちは',0.95)===true})()", 10000));

            assertTrue(awaitJs(scenario,
                    "document.getElementById('flyVoiceTranscript')&&document.getElementById('flyVoiceTranscript').textContent==='こんにちは'", 10000));
        }
    }
}
