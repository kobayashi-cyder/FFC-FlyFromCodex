package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MicrophoneIntegrationTest {
    @Test
    public void microphonePermissionAndAudioRecordInitialize() throws Exception {
        TestSupport.grantMicrophone();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.AndroidVoice&&window.AndroidDiagnostics)", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).permission==='granted'", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){var p=JSON.parse(AndroidDiagnostics.probeMicrophone());return p.initialized===true})()", 5000));
        }
    }
}
