package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutonomyIntegrationTest {
    @Test
    public void runtimeDiscoversToolsAndAcquiresReusableSkill() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue("Native runtime did not become ready", TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.FFCAutonomyCore&&window.FFC_CAPABILITIES&&window.FFC_PROXY_AGENT&&window.BANC888_FLY_AGENT)", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){try{window.FFC_CAPABILITIES.syncAutonomy();"
                            + "var s=window.FFC_CAPABILITIES.autonomyStatus();"
                            + "return !!(s&&s.discoveredTools>=window.BANC888_FLY_AGENT.state.tools.size&&s.discoveredTools>=40"
                            + "&&window.FFC_PROXY_AGENT.version==='2.1-autonomy');}catch(e){return false}})()", 5000));

            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){try{"
                            + "window.FFC_PROXY_AGENT.execute('計算: 2 + 3',{threadCode:'A',context:''});"
                            + "window.FFC_PROXY_AGENT.execute('計算: 2 + 3',{threadCode:'A',context:''});"
                            + "var s=window.FFC_CAPABILITIES.autonomyStatus();"
                            + "var p=window.FFC_PROXY_AGENT.proposal('計算: 2 + 3',{threadCode:'A',context:''});"
                            + "return !!(s&&s.learnedSkills>=1&&p&&p.proposal&&String(p.proposal.source).indexOf('autonomy-skill:')===0);"
                            + "}catch(e){return false}})()", 8000));
        }
    }
}
