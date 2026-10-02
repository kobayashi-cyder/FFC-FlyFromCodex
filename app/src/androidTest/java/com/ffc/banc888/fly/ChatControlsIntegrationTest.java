package com.ffc.banc888.fly;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ChatControlsIntegrationTest {
    @Test public void portraitButtonsSwitchDespiteInvalidA1111AndOpenSettings() throws Exception {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCAttachments&&document.getElementById('ffcCleanSettings')&&document.getElementById('ffcExecutionMode'))",15000));
            assertTrue(TestSupport.awaitJs(scenario,"['conversationSidebarToggle','uiGearButton','ffcAttachButton','ffcQuickListen','ffcQuickStop','ffcExecutionMode','ffcThreadSend'].every(function(id){var e=document.getElementById(id),r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return r.height>=44&&r.width>=44&&r.left>=0&&r.top>=0&&r.right<=innerWidth&&r.bottom<=innerHeight&&(hit===e||e.contains(hit))})",3000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){window.__oldConfigure=FFCA1111Video.configure;FFCA1111Video.configure=function(){throw Error('invalid stored endpoint')};FFCExecution.setMode('offline');return true})()",3000));
            tap(scenario,"ffcExecutionMode");
            assertTrue("Mode button did not become online",TestSupport.awaitJs(scenario,"FFCExecution.status().mode==='online'",3000));
            tap(scenario,"ffcExecutionMode");
            assertTrue("Mode button did not return offline",TestSupport.awaitJs(scenario,"FFCExecution.status().mode==='offline'",3000));
            tap(scenario,"uiGearButton");
            assertTrue(TestSupport.awaitJs(scenario,"document.getElementById('uiSettingsDrawer').classList.contains('open')&&document.getElementById('ffcInstalledVersion').textContent.indexOf('7.11')>=0&&document.getElementById('ffcInternalRuntime').hidden",3000));
            tap(scenario,"uiSettingsClose");
            assertTrue(TestSupport.awaitJs(scenario,"!document.getElementById('uiSettingsDrawer').classList.contains('open')",3000));
            assertTrue(TestSupport.awaitJs(scenario,"(function(){FFCA1111Video.configure=__oldConfigure;return true})()",3000));
        }
    }
    @Test public void androidSelectedFileCanBeReadSummarizedAndExported() throws Exception {
        Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,"!!(window.FFCAttachments&&document.getElementById('ffcAttachButton'))",15000));
            AtomicReference<Uri> selected=new AtomicReference<>();
            scenario.onActivity(activity->{try {
                File dir=new File(activity.getCacheDir(),"exports");dir.mkdirs();File file=new File(dir,"会議資料.md");
                try(FileOutputStream out=new FileOutputStream(file)){out.write("会議は10月3日。参加者は田中と佐藤。田中が10月10日までに試作品を提出する。".getBytes(StandardCharsets.UTF_8));}
                selected.set(FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file));
            }catch(Exception e){throw new AssertionError(e);}});
            IntentFilter filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("*/*");
            Intent result=new Intent().setData(selected.get()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Instrumentation.ActivityMonitor monitor=instrument.addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_OK,result),true);
            try {
                tap(scenario,"ffcAttachButton");
                boolean received=TestSupport.awaitJs(scenario,"FFCAttachments.peek(FFC_THREADS.state.activeId).length===1&&FFCAttachments.peek(FFC_THREADS.state.activeId)[0].text.indexOf('10月10日')>=0",10000);
                assertTrue("Android FileReader did not receive selected UTF-8 document: "+read(scenario,"document.getElementById('ffcAttachmentStatus').textContent"),received);
                assertEquals(1,monitor.getHits());
            } finally { instrument.removeMonitor(monitor); }
            assertTrue(TestSupport.awaitJs(scenario,"(function(){var i=document.getElementById('ffcThreadText');i.value='添付資料の内容を要約してください';return true})()",3000));
            tap(scenario,"ffcThreadSend");
            assertTrue("Attached facts were not used in response",TestSupport.awaitJs(scenario,"(function(){var t=FFC_THREADS.state.threads.find(function(t){return t.id===FFC_THREADS.state.activeId});return !t.pending&&t.messages.some(function(m){return m.role==='assistant'&&m.text.indexOf('10月10日')>=0})})()",30000));
            assertTrue(TestSupport.awaitJs(scenario,"FFCAttachments.peek(FFC_THREADS.state.activeId).length===0&&FFCConversationExport.buildPayload().conversations[0].messages[0].metadata.attachments[0].text.indexOf('試作品')>=0",3000));
        }
    }
    private static String read(ActivityScenario<MainActivity> scenario,String expression) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        scenario.onActivity(activity->{WebView view=(WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);view.evaluateJavascript(expression,r->{value.set(r);done.countDown();});});
        assertTrue(done.await(5,TimeUnit.SECONDS));return value.get();
    }
    static void tap(ActivityScenario<MainActivity> scenario,String id) throws Exception {
        JSONArray point=new JSONArray(read(scenario,"(function(){var e=document.getElementById('"+id+"'),r=e.getBoundingClientRect();return[r.x+r.width/2,r.y+r.height/2,innerWidth]})()"));
        float[] screen=new float[2];scenario.onActivity(activity->{WebView view=(WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);int[] location=new int[2];view.getLocationOnScreen(location);float scale=view.getWidth()/(float)point.optDouble(2);screen[0]=location[0]+(float)point.optDouble(0)*scale;screen[1]=location[1]+(float)point.optDouble(1)*scale;});
        long time=SystemClock.uptimeMillis();Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,screen[0],screen[1],0),up=MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,screen[0],screen[1],0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{assertTrue("Touch down was rejected for "+id,instrument.getUiAutomation().injectInputEvent(down,true));assertTrue("Touch up was rejected for "+id,instrument.getUiAutomation().injectInputEvent(up,true));instrument.waitForIdleSync();}finally{down.recycle();up.recycle();}
    }
}
