package com.ffc.banc888.fly;
import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class ConversationKnowledgeIntegrationTest {
 @Test public void offlineChatAnswersKnownQuestionWithoutNetwork() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
   assertTrue("Native runtime did not become ready",TestSupport.awaitNativeReady(scenario));
   assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCExecution&&window.FFC_THREADS)",12000));
   assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCExecution.setMode('offline');window.__researchCalls=0;AndroidResearch.searchWikipedia=function(){__researchCalls++;throw Error('unexpected network')};var i=document.getElementById('ffcThreadText');i.value='太陽光発電について教えてください';document.getElementById('ffcThreadSend').click();return true})()",3000));
   assertTrue("Chat did not produce an offline solar answer",TestSupport.awaitJs(scenario,"(function(){var t=FFC_THREADS.state.threads.find(function(t){return t.id===FFC_THREADS.state.activeId}),m=t.messages[t.messages.length-1];return !t.pending&&m&&m.role==='assistant'&&m.text.length>80&&m.text.indexOf('太陽電池')>=0&&m.text.indexOf('交流')>=0&&m.meta.tool==='chat.compose'&&__researchCalls===0})()",15000));
  }
 }
}
