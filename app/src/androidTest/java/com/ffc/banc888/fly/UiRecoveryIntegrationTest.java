package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UiRecoveryIntegrationTest {
    @Test public void missingThreadViewGetsOperableRescueAndRestoresNormalView() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue("Native runtime never became available",TestSupport.awaitNativeReady(scenario));
            assertTrue("Recovery runtime missing",TestSupport.awaitJs(scenario,
                "!!window.FFC_UI_RECOVERY&&typeof FFC_UI_RECOVERY.send==='function'",15000));
            assertTrue("Initial UI must be operable",TestSupport.awaitJs(scenario,
                "FFC_UI_RECOVERY.healthy()||FFC_UI_RECOVERY.diagnostics().fallbackVisible",12000));

            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){window.__savedThreadRepair=FFC_THREADS.repairUI;"
                + "FFC_THREADS.repairUI=function(){return false};"
                + "var h=document.getElementById('ffcThreadHub');if(h)h.remove();"
                + "FFC_UI_RECOVERY.repair();return true})()",4000));
            assertTrue("Missing composer must reveal rescue form",TestSupport.awaitJs(scenario,
                "(function(){var x=document.getElementById('bancRescueUI'),s=document.getElementById('bancRescueSend');"
                + "return x&&!x.hidden&&s&&s.getBoundingClientRect().height>=44"
                + "&&document.getElementById('bancRescueText')&&"
                + "['Chat','Code','Image','Video'].every(function(n){return !!document.getElementById('bancRescueMode'+n)});})()",6000));

            ChatControlsIntegrationTest.tap(scenario,"bancRescueModeCode");
            assertTrue(TestSupport.awaitJs(scenario,
                "document.getElementById('bancRescueText').value.indexOf('Python')>=0",3000));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){var e=document.getElementById('bancRescueText');e.value='条件: テスト';e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "window.__savedRescueRequest=FFC_PROXY_AGENT.cognitionRequest;"
                + "FFC_PROXY_AGENT.cognitionRequest=async function(req){return {status:'done',finalText:'実行済み: '+req}};return true})()",3000));
            ChatControlsIntegrationTest.tap(scenario,"bancRescueSend");
            assertTrue("Rescue Send was not wired",TestSupport.awaitJs(scenario,
                "document.getElementById('bancRescueLog').textContent.indexOf('実行済み')>=0"
                + "&&document.getElementById('bancRescueText').value===''",6000));

            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){FFC_PROXY_AGENT.cognitionRequest=__savedRescueRequest;"
                + "FFC_THREADS.repairUI=__savedThreadRepair;FFC_UI_RECOVERY.repair();return true})()",4000));
            assertTrue("Normal composer did not restore",TestSupport.awaitJs(scenario,
                "FFC_UI_RECOVERY.healthy()&&!FFC_UI_RECOVERY.diagnostics().fallbackVisible"
                + "&&!!document.getElementById('dadsTaskNav')",20000));
        }
    }
}
