package com.ffc.banc888.fly;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.net.Uri;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ChatJsonIntegrationTest {
    @Test public void composerProducesExportableConversationWithoutAutomaticSharing() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFC_THREADS&&window.FFCConversationExport&&document.querySelector('body.chat-layout')&&document.getElementById('conversationSidebar'))",15000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFC_THREADS.create();var i=document.getElementById('ffcThreadText');i.value='PythonでCSVファイルを読み込むコードを生成して';document.getElementById('ffcThreadSend').click();return i.tagName==='TEXTAREA'})()",5000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){var t=FFC_THREADS.state.threads.find(function(x){return x.id===FFC_THREADS.state.activeId});return t.messages.some(function(m){return m.role==='assistant'&&m.meta&&m.meta.generation&&m.meta.generation.output.text.indexOf('csv.DictReader')>=0})})()",30000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){window.__savedChatJson=FFCConversationExport.export();return __savedChatJson.ok&&__savedChatJson.result.shared===false&&document.getElementById('conversationJsonShare').disabled===false})()",5000));
            String uri=readJs(scenario,"__savedChatJson.result.fileUri");
            AtomicReference<MainActivity> ref=new AtomicReference<>();scenario.onActivity(ref::set);
            String text;
            try(InputStream input=ref.get().getContentResolver().openInputStream(Uri.parse(uri))) {
                assertNotNull(input);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))!=-1)out.write(buffer,0,n);text=out.toString(StandardCharsets.UTF_8.name());
            }
            JSONObject payload=new JSONObject(text);
            assertEquals("BANC888-conversations",payload.getString("schema"));assertEquals(1,payload.getInt("conversationCount"));assertEquals(2,payload.getInt("messageCount"));
            assertEquals("user",payload.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").getJSONObject(0).getString("role"));
            assertTrue(text.contains("csv.DictReader"));assertFalse(text.contains("__BANC_NATIVE_TOKEN"));
            assertTrue(TestSupport.awaitJs(scenario,"JSON.parse(AndroidFiles.shareSaved('../private.json','application/json')).ok===false",3000));
            assertTrue(TestSupport.awaitJs(scenario,"JSON.parse(__BancFiles.saveText('wrong-token','{}','test.json','application/json')).error==='native bridge denied'",3000));
        }
    }
    private String readJs(ActivityScenario<MainActivity> scenario,String expression) throws Exception {
        AtomicReference<String> value=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        scenario.onActivity(activity->{android.view.ViewGroup content=activity.findViewById(android.R.id.content);((android.webkit.WebView)content.getChildAt(0)).evaluateJavascript(expression,r->{value.set(r);done.countDown();});});
        assertTrue(done.await(5,TimeUnit.SECONDS));return new org.json.JSONArray("["+value.get()+"]").getString(0);
    }
}
