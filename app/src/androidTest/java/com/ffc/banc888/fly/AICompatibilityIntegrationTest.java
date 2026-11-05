package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AICompatibilityIntegrationTest {
    @Test public void jsonRequestRunsPackagedThreeStagePipeline() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){if(window.__compatStarted)return true;window.__compatStarted=true;"
                + "FFCExecution.setMode('offline');"
                + "FFC_PROXY_AGENT.request({messages:[{role:'user',content:'本文を要約して'}],"
                + "source:'会議は10月3日です。田中さんは開発を担当します。',threadCode:'COMPAT_TEST'})"
                + ".then(function(r){window.__compatResult=r}).catch(function(e){window.__compatError=String(e)});return true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){var r=window.__compatResult,c=r&&r.compatibility;return !!(r&&r.status==='done'"
                + "&&r.tool==='content.understand'&&r.choices.length===1"
                + "&&r.choices[0].message.content.indexOf('10月3日')>=0"
                + "&&c.instruction.connectome.trace.length===6&&c.plan.connectome.trace.length===6"
                + "&&c.information.source.indexOf('田中')>=0)})()", 15000));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){FFC_PROXY_AGENT.request({messages:[{role:'user',content:'hello'}],stream:true})"
                + ".then(function(r){window.__compatUnsupported=r});return true})()", 3000));
            assertTrue(TestSupport.awaitJs(scenario,
                "window.__compatUnsupported&&__compatUnsupported.status==='unsupported'&&__compatUnsupported.choices.length===0", 3000));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){FFC_PROXY_AGENT.request({messages:[{role:'user',content:'計算: 2 + 3'}],threadCode:'COMPAT_MATH'})"
                + ".then(function(r){window.__compatMath=r});return true})()", 3000));
            assertTrue(TestSupport.awaitJs(scenario,
                "window.__compatMath&&__compatMath.status==='done'&&__compatMath.choices[0].message.content==='計算結果: 5'", 5000));
        }
    }
    @Test public void cognitionIsMountedAndUnknownActionsStayBlocked() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                "!!window.FFCCognitionCore&&!!window.FFCCognitionMultimodal&&typeof FFC_PROXY_AGENT.cognitionPlan==='function'"
                + "&&typeof FFC_PROXY_AGENT.cognitionRequest==='function'"
                + "&&FFC_PROXY_AGENT.cognitionCapabilities().domains.length===4"
                + "&&!!document.getElementById('bancCognitionCard')", 10000));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){if(window.__cognitionTestStarted)return true;window.__cognitionTestStarted=true;"
                + "var task={schema:1,facts:{stage:0},goal:{stage:2},actions:["
                + "{id:'first',tool:'unregistered.action',requires:{stage:0},effects:{stage:1},args:{}},"
                + "{id:'second',tool:'unregistered.action',requires:{stage:1},effects:{stage:2},args:{}}]};"
                + "window.__cognitionPreview=FFC_PROXY_AGENT.cognitionPlan(task);"
                + "FFC_PROXY_AGENT.cognitionSolve(task,{execute:true,threadCode:'COGNITION_TEST'})"
                + ".then(function(r){window.__cognitionActual=r});return true})()", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                "window.__cognitionPreview&&__cognitionPreview.status==='planned'"
                + "&&__cognitionPreview.steps.length===2", 5000));
            assertTrue(TestSupport.awaitJs(scenario,
                "window.__cognitionActual&&__cognitionActual.status==='blocked'"
                + "&&__cognitionActual.reason==='permission_denied'"
                + "&&__cognitionActual.trace.length===0", 5000));
        }
    }

}
