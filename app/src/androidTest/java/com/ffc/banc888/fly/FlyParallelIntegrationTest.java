package com.ffc.banc888.fly;
import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FlyParallelIntegrationTest {
 @Test public void workersRunIndependentThreadCodeAndSettingsStaySettings() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   assertTrue(TestSupport.awaitNativeReady(scenario));
   assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCFlyParallel&&window.FFCExecution&&document.getElementById('ffcCleanSettings'))",12000));
   assertTrue(TestSupport.awaitJs(scenario,"FFCExecution.status().mode==='offline'&&document.getElementById('ffcInternalRuntime').hidden&&!document.getElementById('ffcCleanSettings').contains(document.getElementById('a1111VideoGenerate'))",3000));
   assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCFlyParallel.configure(4);window.__workerResults=null;Promise.all(['A','B','C','D'].map(function(lane){return FFC_PROXY_AGENT.executeAsync('PythonでCSVファイルを読み込むコードを生成して',{threadCode:lane,context:''})})).then(function(results){window.__workerResults=results}).catch(function(e){window.__workerError=String(e)});return true})()",3000));
   assertTrue(TestSupport.awaitJs(scenario,"!!(window.__workerResults&&__workerResults.length===4&&__workerResults.every(function(r){return r.status==='done'&&r.value.validation.pass&&r.value.text.indexOf('csv.DictReader')>=0})&&FFCFlyParallel.status().workers===4)",30000));
   assertTrue(TestSupport.awaitJs(scenario,"(function(){var r=document.getElementById('ffcExecutionMode').getBoundingClientRect();return r.height>=44&&r.left>=0&&r.right<=innerWidth})()",3000));
  }
 }
}
