package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand("pm grant com.ffc.banc888.fly " + Manifest.permission.RECORD_AUDIO)) {
            Thread.sleep(400);
        }

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
                    "JSON.parse(AndroidVoice.status()).permission==='granted'", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){var p=JSON.parse(AndroidDiagnostics.probeMicrophone());return p.ok===true&&p.initialized===true&&p.readSamples>0&&p.nonZeroSamples>0&&p.peakAbs>0})()", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){"
                            + "window.__bancListenReady=false;window.__bancListenError='';"
                            + "var n=window.BANC888_NATIVE_VOICE;"
                            + "var ready=n.onListening,err=n.onError;"
                            + "n.onListening=function(){window.__bancListenReady=true;if(ready)return ready.apply(this,arguments)};"
                            + "n.onError=function(code,msg){window.__bancListenError=String(code||'')+':'+String(msg||'');if(err)return err.apply(this,arguments)};"
                            + "return AndroidVoice.startListening('ja-JP')===true"
                            + "})()", 5000));

            assertTrue(awaitJs(scenario,
                    "window.__bancListenReady===true", 12000));

            assertTrue(awaitJs(scenario,
                    "window.__bancListenError===''", 2000));

            assertTrue(awaitJs(scenario,
                    "(function(){var s=JSON.parse(AndroidVoice.status());return s.recognizerReadyAtMs>0&&(s.recognizerBackend==='on-device'||s.recognizerBackend==='system')})()", 5000));

            assertTrue(awaitJs(scenario,
                    "AndroidVoice.stopListening()===true", 5000));

            assertTrue(awaitJs(scenario,
                    "(function(){window.__bancRuntimePingOk=false;AndroidRuntime.ping().then(function(r){window.__bancRuntimePingOk=!!(r&&r.ok)});return true})()", 10000));
            assertTrue(awaitJs(scenario,
                    "window.__bancRuntimePingOk===true", 10000));

            assertTrue(awaitJs(scenario,
                    "AndroidRuntime.transport().mode==='webmessage'&&AndroidRuntime.transport().epoch>=1", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){window.__bancRuntimeCapsOk=false;AndroidRuntime.capabilities().then(function(r){window.__bancRuntimeCapsOk=!!(r&&r.ok&&r.capabilities&&r.capabilities.nativePushEvents&&r.epoch>=1)});return true})()", 10000));
            assertTrue(awaitJs(scenario,
                    "window.__bancRuntimeCapsOk===true", 10000));

            assertTrue(awaitJs(scenario,
                    "document.getElementById('uiSettingsBody')&&document.getElementById('bancWebRuntimePanel')&&document.getElementById('uiSettingsBody').contains(document.getElementById('bancWebRuntimePanel'))", 10000));

            assertTrue(awaitJs(scenario,
                    "window.FFCConversationOutput.speech({threadIds:[1],reason:'same',confidence:1},'こんにちは',window.FFCThreadCore.excelCode,{includeRoute:false})==='こんにちは'", 10000));

            // UI-routing check only: real microphone and recognizer readiness were verified above.
            assertTrue(awaitJs(scenario,
                    "(function(){var x=document.getElementById('flyVoiceSpeak');if(x)x.checked=false;return AndroidDiagnostics.simulateVoiceResult('こんにちは',0.95)===true})()", 10000));

            assertTrue(awaitJs(scenario,
                    "document.getElementById('flyVoiceTranscript')&&document.getElementById('flyVoiceTranscript').textContent==='こんにちは'", 10000));

            assertTrue(awaitJs(scenario,
                    "(function(){var x=document.getElementById('flyVoiceSpeak');if(x)x.checked=true;return true})()", 5000));

            assertTrue(awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).ttsReady===true", 15000));

            assertTrue(awaitJs(scenario,
                    "(function(){"
                            + "window.__bancTtsDone=false;window.__bancTtsError='';"
                            + "var n=window.BANC888_NATIVE_VOICE;"
                            + "var done=n.onTtsDone,err=n.onTtsError;"
                            + "n.onTtsDone=function(){window.__bancTtsDone=true;if(done)return done.apply(this,arguments)};"
                            + "n.onTtsError=function(msg){window.__bancTtsError=String(msg||'error');if(err)return err.apply(this,arguments)};"
                            + "window.__bancTtsRequested=AndroidVoice.speak('音声テストです','ja-JP',1,1);"
                            + "return window.__bancTtsRequested===true"
                            + "})()", 5000));

            assertTrue(awaitJs(scenario,
                    "window.__bancTtsDone===true&&window.__bancTtsError===''", 20000));

            assertTrue(awaitJs(scenario,
                    "(function(){var s=JSON.parse(AndroidVoice.status());return s.ttsReady===true&&s.state==='IDLE'&&(!s.lastTtsError)&&s.lastTtsErrorCode===0})()", 5000));
        }
    }
}
