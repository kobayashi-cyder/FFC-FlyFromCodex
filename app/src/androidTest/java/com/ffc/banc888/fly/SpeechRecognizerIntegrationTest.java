package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class SpeechRecognizerIntegrationTest {
    @Test
    public void injectedPcmReachesSpeechRecognizerAndReturnsTranscript() throws Exception {
        TestSupport.grantMicrophone();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.AndroidVoice&&window.FFC_THREADS&&window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.__ffcThreadBound)", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__voiceErr='';window.__voiceText='';var n=window.BANC888_NATIVE_VOICE;var e=n.onError,r=n.onResult;n.onError=function(c,m){window.__voiceErr=String(c||'')+':'+String(m||'');if(e)return e.apply(this,arguments)};n.onResult=function(t){window.__voiceText=String(t||'').trim();if(r)return r.apply(this,arguments)};return true})()", 3000));

            File pcm = new File("/data/local/tmp/ci-mic-speech.raw");
            assertTrue("Injected speech PCM is missing", pcm.isFile() && pcm.length() > 0);

            try (ParcelFileDescriptor audio = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)) {
                AtomicBoolean started = new AtomicBoolean(false);
                scenario.onActivity(activity -> started.set(
                        activity.speechForTest().startListeningFromAudio("en-US", audio, 16000)));
                assertTrue("SpeechRecognizer rejected injected PCM source", started.get());

                assertTrue("Recognizer did not start processing or error within 5 seconds",
                        TestSupport.awaitJs(scenario,
                                "(function(){var s=JSON.parse(AndroidVoice.status());return s.recognizerReadyAtMs>0||s.recognitionActivityAtMs>0||window.__voiceErr!==''})()", 5000));

                assertTrue("Recognizer did not finish with transcript or error",
                        TestSupport.awaitJs(scenario,
                                "window.__voiceText.length>0||window.__voiceErr!==''", 20000));

                assertTrue("Recognizer returned an error instead of transcript",
                        TestSupport.awaitJs(scenario,
                                "window.__voiceErr===''&&window.__voiceText.length>0", 1000));

                assertTrue("Recognizer diagnostics did not record result",
                        TestSupport.awaitJs(scenario,
                                "(function(){var s=JSON.parse(AndroidVoice.status());return s.recognitionResultCount>0})()", 1000));
            }
        }
    }
}
