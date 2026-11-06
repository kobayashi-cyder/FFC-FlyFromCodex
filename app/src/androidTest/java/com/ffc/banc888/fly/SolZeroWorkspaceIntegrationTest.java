package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SolZeroWorkspaceIntegrationTest {
    @Test public void offlineSolZeroRunsAndTransfersExplicitDraftBothWays() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue("BANC runtime did not start",TestSupport.awaitNativeReady(scenario));
            assertTrue("SolZero launch control missing",TestSupport.awaitJs(scenario,
                "!!window.FFC_SOLZERO_WORKSPACE&&!!(document.getElementById('ffcSolZeroOpen')||document.getElementById('ffcSolZeroRescueOpen'))",12000));
            assertTrue("Opening SolZero failed",TestSupport.awaitJs(scenario,
                "(function(){var b=document.getElementById('ffcSolZeroOpen')||document.getElementById('ffcSolZeroRescueOpen');"
                + "var input=document.getElementById('ffcThreadText')||document.getElementById('bancRescueText');"
                + "if(!b||!input)return false;input.value='こんにちは';input.dispatchEvent(new Event('input',{bubbles:true}));"
                + "window.__bancMsgBefore=(FFC_THREADS?.state?.threads||[]).reduce(function(n,t){return n+t.messages.length},0);"
                + "b.click();return true})()",5000));
            assertTrue("Offline SolZero iframe did not initialize",TestSupport.awaitJs(scenario,
                "FFC_SOLZERO_WORKSPACE.status().loaded"
                + "&&document.getElementById('ffcSolZeroFrame').contentDocument.getElementById('chatForm')!==null"
                + "&&document.getElementById('ffcSolZeroFrame').contentDocument.getElementById('input')!==null",16000));
            assertTrue("BANC to SolZero handoff failed",TestSupport.awaitJs(scenario,
                "FFC_SOLZERO_WORKSPACE.handoff()"
                + "&&document.getElementById('ffcSolZeroFrame').contentDocument.getElementById('input').value==='こんにちは'",5000));
            assertTrue("SolZero command form could not be submitted",TestSupport.awaitJs(scenario,
                "(function(){var f=document.getElementById('ffcSolZeroFrame').contentDocument.getElementById('chatForm');"
                + "f.requestSubmit();return true})()",5000));
            assertTrue("SolZero engine did not produce an offline reply",TestSupport.awaitJs(scenario,
                "(function(){var d=document.getElementById('ffcSolZeroFrame').contentDocument;"
                + "return d.querySelectorAll('.message.user .bubble').length>0"
                + "&&d.querySelectorAll('.message.assistant .bubble').length>0"
                + "&&d.querySelector('.message.assistant .bubble').textContent.trim().length>0})()",12000));
            assertTrue("SolZero to BANC draft handoff failed",TestSupport.awaitJs(scenario,
                "FFC_SOLZERO_WORKSPACE.returnLast()"
                + "&&(document.getElementById('ffcThreadText')||document.getElementById('bancRescueText')).value.includes('SolZeroの回答・未検証')",5000));
            assertTrue("SolZero must not send BANC actions on its own",TestSupport.awaitJs(scenario,
                "(FFC_THREADS?.state?.threads||[]).reduce(function(n,t){return n+t.messages.length},0)===window.__bancMsgBefore"
                + "&&document.getElementById('ffcSolZeroWorkspace').hidden",5000));
        }
    }
}
