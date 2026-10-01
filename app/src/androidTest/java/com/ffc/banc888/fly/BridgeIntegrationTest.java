package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BridgeIntegrationTest {
    @Test
    public void webViewBridgeRuntimeIsLinked() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.FFC_THREADS&&window.FFC_PROXY_AGENT&&window.FFC_CAPABILITIES&&window.FFCConversationOutput&&window.FFC_WEBVIEW_RUNTIME&&window.AndroidRuntime)", 30000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "typeof AndroidVoice!=='undefined'&&typeof AndroidFiles!=='undefined'&&typeof AndroidResearch!=='undefined'&&typeof AndroidDev!=='undefined'&&typeof AndroidDiagnostics!=='undefined'", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).threadRouter===true", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(AndroidDiagnostics.status()).shellVersion===70&&JSON.parse(AndroidDiagnostics.status()).bridgeSchema===4&&JSON.parse(AndroidDiagnostics.status()).webShell.runtimeVersion===7", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(__BancVoice.status('wrong-token')).error==='native bridge denied'", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__ping=false;AndroidRuntime.ping().then(function(r){window.__ping=!!(r&&r.ok)});return true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario, "window.__ping===true", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "AndroidRuntime.transport().mode==='webmessage'&&AndroidRuntime.transport().epoch>=1", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__caps=false;AndroidRuntime.capabilities().then(function(r){window.__caps=!!(r&&r.ok&&r.capabilities&&r.capabilities.nativePushEvents&&r.epoch>=1)});return true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario, "window.__caps===true", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "document.getElementById('uiSettingsBody')&&document.getElementById('bancWebRuntimePanel')&&document.getElementById('uiSettingsBody').contains(document.getElementById('bancWebRuntimePanel'))", 5000));
        }
    }
}
