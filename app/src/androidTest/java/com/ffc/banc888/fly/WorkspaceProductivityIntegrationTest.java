package com.ffc.banc888.fly;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WorkspaceProductivityIntegrationTest {
    @Test public void draftRestoresAfterSwitchAndActivityRestart() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCProductivity&&document.getElementById('ffcConversationSearch'))",15000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCProductivity.clearDraft();var i=document.getElementById('ffcThreadText');i.value='再起動後に復元する下書き';i.dispatchEvent(new Event('input',{bubbles:true}));return JSON.parse(localStorage.getItem('FFC_THREAD_DRAFTS_V1')||'{}')[FFC_THREADS.state.activeId]==='再起動後に復元する下書き'})()",3000));
        }
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!window.FFCProductivity&&document.getElementById('ffcThreadText').value==='再起動後に復元する下書き'",15000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){var first=FFC_THREADS.state.activeId,i=document.getElementById('ffcThreadText');var second=FFC_THREADS.create().id;i.value='別会話の下書き';i.dispatchEvent(new Event('input',{bubbles:true}));FFC_THREADS.setActive(first);return i.value==='再起動後に復元する下書き'&&FFCProductivity.status().drafts[second]==='別会話の下書き'})()",3000));
        }
    }

    @Test public void japaneseSearchExplicitReuseAndArtifactActionsStayConversationScoped() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCProductivity&&window.FFCAttachments)",15000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){var t=FFC_THREADS.state.threads.find(function(x){return x.id===FFC_THREADS.state.activeId});t.messages.push({id:900001,role:'user',text:FFC_THREADS.core.excelCode(t.id)+': 日本語本文検索',time:Date.now(),meta:{attachments:[{name:'資料名_日本語.md',type:'text/markdown',size:20,text:'再利用する資料',truncated:false}]}},{id:900002,role:'assistant',text:'資料を作成しました',time:Date.now(),meta:{generation:{tool:'document.generate',output:{body:'# 再利用できる生成資料',format:'markdown',filename:'generated.md'}}}});FFC_THREADS.persist();FFC_THREADS.render();return FFCProductivity.search('資料名_日本語')===1})()",5000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCProductivity.clearSearch();return Array.from(document.querySelectorAll('#ffcThreadChips .ffcThreadChip')).every(function(x){return !x.hidden})})()",3000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCProductivity.openReuse();var b=document.querySelector('#ffcReuseDialog .ffcWorkspaceItem button');if(!b)return false;b.click();return FFCAttachments.peek(FFC_THREADS.state.activeId).some(function(f){return f.name==='資料名_日本語.md'})})()",3000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCProductivity.openArtifacts();var d=document.getElementById('ffcArtifactsDialog'),buttons=d.querySelectorAll('.ffcWorkspaceActions button');if(buttons.length<4)return false;d.querySelector('[data-open]').click();var opened=document.getElementById('ffcArtifactViewer')?.open===true;d.querySelector('[data-attach]').click();return opened&&FFCAttachments.peek(FFC_THREADS.state.activeId).some(function(f){return f.name==='generated.md'})})()",3000));
            assertTrue(TestSupport.awaitJs(scenario,"FFCProductivity.listAttachments('active').every(function(x){return x.threadId===FFC_THREADS.state.activeId})",3000));
        }
    }
}
