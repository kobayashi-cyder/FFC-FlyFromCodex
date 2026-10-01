package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoiceUiIntegrationTest {
    @Test
    public void recognizedTextRoutesIntoVoiceUi() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.FFCConversationOutput&&window.FFCThreadCore&&window.AndroidDiagnostics)", 10000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "window.FFCConversationOutput.speech({threadIds:[1],reason:'same',confidence:1},'こんにちは',window.FFCThreadCore.excelCode,{includeRoute:false})==='こんにちは'", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){var x=document.getElementById('flyVoiceSpeak');if(x)x.checked=false;return AndroidDiagnostics.simulateVoiceResult('こんにちは',0.95)===true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "document.getElementById('flyVoiceTranscript')&&document.getElementById('flyVoiceTranscript').textContent==='こんにちは'", 5000));
        }
    }
}
