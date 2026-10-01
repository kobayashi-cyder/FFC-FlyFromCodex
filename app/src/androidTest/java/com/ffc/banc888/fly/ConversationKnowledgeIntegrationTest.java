package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConversationKnowledgeIntegrationTest {
    @Test
    public void generalQuestionProducesSubstantiveSolarAnswer() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.BANC888_FLY_AGENT&&window.AndroidResearch&&window.FFC_THREADS)", 12000));

            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__convDone=false;window.__convText='';window.__convTool='';"
                            + "setTimeout(function(){try{"
                            + "var p=window.BANC888_FLY_AGENT.run('太陽光発電について教えてください',3,{logChat:false});"
                            + "window.__convText=String(p&&p.finalText||'');"
                            + "window.__convTool=String(p&&p.selected&&p.selected.tool||'');"
                            + "}catch(e){window.__convText='ERROR:'+String(e)}finally{window.__convDone=true}},0);"
                            + "return true})()", 3000));

            assertTrue("Conversation did not finish within the bounded research window",
                    TestSupport.awaitJs(scenario, "window.__convDone===true", 12000));

            assertTrue("General question did not use the research path",
                    TestSupport.awaitJs(scenario, "window.__convTool==='research.wiki'", 1000));

            assertTrue("Conversation returned a canned or empty reply",
                    TestSupport.awaitJs(scenario,
                            "window.__convText.length>40"
                                    + "&&!window.__convText.includes('会話機能を復元しています')"
                                    + "&&(window.__convText.includes('太陽')||window.__convText.includes('発電'))",
                            1000));
        }
    }
}
