package com.ffc.banc888.fly;

import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConnectomeMobileIntegrationTest {
    @Test
    public void packagedKernelReusesWorkspaceAndExecutesThroughConnectome() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue("Native runtime did not become ready", TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                "(function(){try{"
                + "var k=new FFCProxyCore.ConnectomeSelector(BANC888_FLY_AGENT.connectome);"
                + "var a=k.activity,b=k.next,credit=k.credit,times=[],pick;"
                + "for(var i=0;i<16;i++){var t=performance.now();"
                + "pick=k.select([{tool:'chat.compose',excitation:1.1},{tool:'micro.compile',excitation:.8}]);"
                + "times.push(performance.now()-t);}"
                + "times.sort(function(x,y){return x-y});var s=k.stats();"
                + "console.log('CONNECTOME_MOBILE '+JSON.stringify({p50Ms:times[8],p95Ms:times[15],stats:s}));"
                + "var mux=new FFCProxyCore.ConnectomeMultiplexer(BANC888_FLY_AGENT.connectome,{maxLanes:8});"
                + "var batch=mux.selectMany([{lane:'A',candidates:[{tool:'micro.compile'}]},"
                + "{lane:'B',candidates:[{tool:'micro.compile'}]}]);"
                + "mux.reinforce('micro.compile',1,{lane:'A'});"
                + "var lanes=mux.snapshot().lanes;"
                + "var isolated=lanes.some(function(x){return x.id==='B'&&Object.keys(x.factors).length===0});"
                + "var result=FFC_PROXY_AGENT.execute('計算: 2 + 3',{threadCode:'A',context:''});"
                + "return !!(pick&&pick.tool==='micro.compile'&&pick.connectome.trace.length===6"
                + "&&s.traceFrames===96&&s.engine==='sparse-float64-v1'&&s.workspaceBytes<4096"
                + "&&credit===k.credit&&((a===k.activity&&b===k.next)||(b===k.activity&&a===k.next))"
                + "&&result.status==='done'&&result.steps.length&&result.steps.every(function(x){return !!x.connectome})"
                + "&&isolated&&batch.every(function(x){return !!x.selection})&&mux.stats().sharedTopology&&FFC_PROXY_AGENT.status().connectomeRuntime.multiplexed&&FFC_PROXY_AGENT.status().connectomeRuntime.engine==='sparse-float64-v1');"
                + "}catch(e){console.error('CONNECTOME_MOBILE '+e.stack);return false}})()",15000));
        }
    }
}
