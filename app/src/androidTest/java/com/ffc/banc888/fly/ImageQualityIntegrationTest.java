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
                            + "&&v.validation.candidateCount>=8&&v.validation.passedCount>=1"
                            + "&&v.validation.score>=72&&v.selection&&pool.length===v.validation.passedCount"
                            + "&&pool.every(function(x){return x.pass===true})"
                            + "&&Number(v.selection.score)===max);"
                            + "}catch(e){window.__imgMeta={error:String(e)}}finally{window.__imgDone=true}},0);"
                            + "return true})()", 3000));

            assertTrue("Image candidate pipeline did not finish",
                    TestSupport.awaitJs(scenario, "window.__imgDone===true", 15000));
            assertTrue("No passing ranked image candidate was selected",
                    TestSupport.awaitJs(scenario, "window.__imgOk===true", 1000));
        }
    }
}
