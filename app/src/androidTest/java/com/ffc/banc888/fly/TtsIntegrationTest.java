package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TtsIntegrationTest {
    @Test
    public void textToSpeechCompletesUtterance() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue("Native runtime did not become ready", TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(AndroidVoice.status()).ttsReady===true", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__ttsDone=false;window.__ttsErr='';var n=window.BANC888_NATIVE_VOICE;var d=n.onTtsDone,e=n.onTtsError;n.onTtsDone=function(){window.__ttsDone=true;if(d)return d.apply(this,arguments)};n.onTtsError=function(m){window.__ttsErr=String(m||'error');if(e)return e.apply(this,arguments)};return AndroidVoice.speak('voice test','en-US',1,1)===true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "window.__ttsDone===true||window.__ttsErr!==''", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "window.__ttsDone===true&&window.__ttsErr===''", 1000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){var s=JSON.parse(AndroidVoice.status());return s.ttsReady===true&&s.state==='IDLE'&&(!s.lastTtsError)&&s.lastTtsErrorCode===0})()", 3000));
        }
    }
}
