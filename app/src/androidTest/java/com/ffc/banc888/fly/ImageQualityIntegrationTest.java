package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImageQualityIntegrationTest {
    @Test
    public void explicitImageRequestUsesMultipleCandidatesAndSelectsOnlyPassingResult() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue("Native runtime did not become ready", TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.FFC_CAPABILITIES&&window.FFCImageQuality&&window.BANC888_FLY_AGENT)", 12000));

            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__imgDone=false;window.__imgOk=false;window.__imgMeta=null;"
                            + "setTimeout(function(){try{"
                            + "var r=window.FFC_CAPABILITIES.handle('猫の画像を作成して',{threadCode:'A',context:''});"
                            + "var v=r&&r.value;"
                            + "window.__imgMeta=v&&v.validation||null;"
                            + "var pool=v&&Array.isArray(v.candidates)?v.candidates:[];"
                            + "var max=pool.length?Math.max.apply(null,pool.map(function(x){return Number(x.score||0)})):-1;"
                            + "window.__imgOk=!!(r&&r.handled&&v&&v.validation&&v.validation.pass"
                            + "&&v.validation.candidateCount>=26&&v.validation.passedCount>=1"
                            + "&&v.validation.generationCount===3"
                            + "&&Array.isArray(v.validation.generationTrace)&&v.validation.generationTrace.length===3"
                            + "&&Number(v.validation.evolutionGain)>=0"
                            + "&&v.validation.score>=72&&v.selection&&pool.length===v.validation.passedCount"
                            + "&&pool.every(function(x){return x.pass===true})"
                            + "&&Number(v.selection.score)===max"
                            + "&&pool.some(function(x){return Number(x.generation)>=1})"
                            + "&&v.validation.metrics&&v.validation.metrics.categoryIntegrity===1"
                            + "&&Array.isArray(v.validation.metrics.categoryChecks)"
                            + "&&v.validation.metrics.categoryChecks.some(function(x){return x.kind==='cat'&&x.pass===true"
                            + "&&x.parts&&x.parts.head&&x.parts.head.pass&&x.parts.body&&x.parts.body.pass&&x.parts.tail&&x.parts.tail.pass}));"
                            + "}catch(e){window.__imgMeta={error:String(e)}}finally{window.__imgDone=true}},0);"
                            + "return true})()", 3000));

            assertTrue("Image candidate pipeline did not finish",
                    TestSupport.awaitJs(scenario, "window.__imgDone===true", 60000));
            assertTrue("No passing ranked image candidate was selected",
                    TestSupport.awaitJs(scenario, "window.__imgOk===true", 1000));
        }
    }
}
