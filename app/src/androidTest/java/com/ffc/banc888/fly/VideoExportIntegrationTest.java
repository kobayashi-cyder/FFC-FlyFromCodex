package com.ffc.banc888.fly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class VideoExportIntegrationTest {
    @Test
    public void connectomeVideoRequestExportsThroughAuthenticatedWebViewBridge() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(TestSupport.awaitNativeReady(scenario));
            assertTrue(TestSupport.awaitJs(scenario,
                    "!!(window.AndroidVideo&&window.FFCVideoExport)", 12000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "JSON.parse(__BancVideo.status('wrong-token','')).error==='native bridge denied'", 3000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){window.__mp4Plan=null;setTimeout(function(){window.__mp4Plan="
                            + "FFC_PROXY_AGENT.execute('猫が動く動画を生成して',{threadCode:'VIDEO',context:''})},0);return true})()", 3000));
            assertTrue("Connectome video plan did not start MP4 export", TestSupport.awaitJs(scenario,
                    "!!(window.__mp4Plan&&__mp4Plan.status==='done'&&__mp4Plan.value&&__mp4Plan.value.exported"
                            + "&&__mp4Plan.value.exported.ok&&__mp4Plan.steps.every(function(s){return !!s.connectome}))", 60000));
            assertTrue("WebView MP4 export did not complete", TestSupport.awaitJs(scenario,
                    "FFCVideoExport.status().status==='complete'", 120000));
            assertTrue(TestSupport.awaitJs(scenario,
                    "(function(){var s=FFCVideoExport.status();return s.ok&&s.frames===96&&s.frameCount===96&&s.fps===24"
                            + "&&s.bytes>100&&s.shared===false&&s.fileUri.indexOf('content://')===0})()", 3000));
            String uriString = readJs(scenario, "FFCVideoExport.status().fileUri");
            AtomicReference<MainActivity> reference = new AtomicReference<>();
            scenario.onActivity(reference::set);
            MediaMetadataRetriever decoder = new MediaMetadataRetriever();
            try {
                decoder.setDataSource(reference.get(), Uri.parse(uriString));
                Bitmap first = decoder.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                assertNotNull("Canvas MP4 must actually decode", first);
                assertEquals(768, first.getWidth());
                assertEquals(512, first.getHeight());
                first.recycle();
            } finally {
                decoder.release();
            }
        }
    }

    @Test
    public void sequentialJpegFramesProduceDecodableAvcMp4() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<MainActivity> reference = new AtomicReference<>();
            scenario.onActivity(reference::set);
            MainActivity activity = reference.get();
            VideoExporter exporter = new VideoExporter(activity);
            try {
                // Calls run on this instrumentation worker, never on the UI thread.
                JSONObject began = json(exporter.begin(240, 160, 6, 12, "../decoded-clip.mp4"));
                assertTrue("H.264 initialization: " + began, began.getBoolean("ok"));
                String job = began.getString("jobId");
                assertFalse(began.has("fileUri"));
                assertFalse(json(exporter.share(job)).getBoolean("ok"));
                assertFalse(json(exporter.finish(job)).getBoolean("ok"));
                assertFalse(json(exporter.begin(240, 160, 6, 12, "second.mp4")).getBoolean("ok"));
                assertFalse(json(exporter.append(job, 1, jpeg(240, 160, Color.RED))).getBoolean("ok"));
                for (int frame = 0; frame < 12; frame++) {
                    int red = 255 - frame * 20;
                    int blue = frame * 20;
                    JSONObject appended = json(exporter.append(job, frame,
                            jpeg(240, 160, Color.rgb(red, 20, blue))));
                    assertTrue("frame " + frame + ": " + appended, appended.getBoolean("ok"));
                    assertEquals(frame + 1, appended.getInt("frames"));
                    assertFalse(appended.has("fileUri"));
                }
                JSONObject done = json(exporter.finish(job));
                assertTrue("MP4 finish: " + done, done.getBoolean("ok"));
                assertEquals("complete", done.getString("state"));
                assertEquals("video/mp4", done.getString("mime"));
                assertFalse(done.getBoolean("shared"));
                assertTrue(done.getLong("bytes") > 100);
                assertTrue(done.getString("name").endsWith(".mp4"));
                assertFalse(done.getString("name").contains(".."));
                Uri uri = Uri.parse(done.getString("fileUri"));
                assertEquals("content", uri.getScheme());
                // Verify the exported provider content, not an internal codec buffer.
                MediaExtractor extractor = new MediaExtractor();
                try (ParcelFileDescriptor file = activity.getContentResolver().openFileDescriptor(uri, "r")) {
                    assertNotNull(file);
                    extractor.setDataSource(file.getFileDescriptor());
                    assertEquals(1, extractor.getTrackCount());
                    MediaFormat track = extractor.getTrackFormat(0);
                    assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, track.getString(MediaFormat.KEY_MIME));
                    assertEquals(240, track.getInteger(MediaFormat.KEY_WIDTH));
                    assertEquals(160, track.getInteger(MediaFormat.KEY_HEIGHT));
                    assertTrue(track.getLong(MediaFormat.KEY_DURATION) >= 1600000);
                    assertTrue(track.getLong(MediaFormat.KEY_DURATION) <= 2200000);
                    extractor.selectTrack(0);
                    int samples = 0;
                    long previousPts = -1;
                    while (extractor.getSampleTime() >= 0) {
                        assertTrue(extractor.getSampleTime() > previousPts);
                        previousPts = extractor.getSampleTime();
                        samples++;
                        extractor.advance();
                    }
                    assertEquals(12, samples);
                } finally {
                    extractor.release();
                }
                MediaMetadataRetriever decoder = new MediaMetadataRetriever();
                try {
                    decoder.setDataSource(activity, uri);
                    Bitmap first = decoder.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    assertNotNull("Export must actually decode", first);
                    int color = first.getPixel(first.getWidth() / 2, first.getHeight() / 2);
                    assertTrue("YUV planes must retain red rather than corrupt chroma", Color.red(color) > Color.blue(color) + 100);
                    first.recycle();
                } finally {
                    decoder.release();
                }
                assertEquals(done.getString("fileUri"), json(exporter.finish(job)).getString("fileUri"));
            } finally {
                exporter.close();
            }
        }
    }

    @Test
    public void limitsCancellationAndBadFrameCleanupAreBounded() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<MainActivity> reference = new AtomicReference<>();
            scenario.onActivity(reference::set);
            MainActivity activity = reference.get();
            VideoExporter exporter = new VideoExporter(activity);
            try {
                assertFalse(json(exporter.begin(1280, 1280, 30, 450, "huge.mp4")).getBoolean("ok"));
                assertFalse(json(exporter.begin(240, 160, 31, 12, "fps.mp4")).getBoolean("ok"));
                assertFalse(json(exporter.begin(240, 160, 6, 91, "long.mp4")).getBoolean("ok"));
                JSONObject began = json(exporter.begin(240, 160, 6, 12, "cancelled.mp4"));
                assertTrue(began.toString(), began.getBoolean("ok"));
                String job = began.getString("jobId");
                assertTrue(json(exporter.append(job, 0, jpeg(240, 160, Color.GREEN))).getBoolean("ok"));
                assertTrue(json(exporter.cancel(job)).getBoolean("ok"));
                assertEquals("cancelled", json(exporter.status(job)).getString("state"));
                assertFalse(new File(activity.getCacheDir(), "exports/" + began.getString("name") + ".part").exists());
                assertFalse(json(exporter.share(job)).getBoolean("ok"));
                JSONObject next = json(exporter.begin(240, 160, 6, 12, "bad.mp4"));
                assertTrue(next.toString(), next.getBoolean("ok"));
                String nextJob = next.getString("jobId");
                JSONObject bad = json(exporter.append(nextJob, 0, jpeg(320, 240, Color.BLUE)));
                assertFalse(bad.getBoolean("ok"));
                assertTrue(bad.getString("error").contains("dimensions"));
                assertEquals("error", json(exporter.status(nextJob)).getString("state"));
                assertFalse(new File(activity.getCacheDir(), "exports/" + next.getString("name") + ".part").exists());
                JSONObject finalJob = json(exporter.begin(240, 160, 6, 12, "closed.mp4"));
                assertTrue(finalJob.toString(), finalJob.getBoolean("ok"));
                exporter.close();
                assertFalse(new File(activity.getCacheDir(), "exports/" + finalJob.getString("name") + ".part").exists());
            } finally {
                exporter.close();
            }
        }
    }

    private static JSONObject json(String value) throws Exception {
        return new JSONObject(value);
    }

    private static String readJs(ActivityScenario<MainActivity> scenario, String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            ViewGroup root = activity.findViewById(android.R.id.content);
            WebView view = (WebView) root.getChildAt(0);
            view.evaluateJavascript(expression, value -> { result.set(value); done.countDown(); });
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return (String) new JSONTokener(result.get()).nextValue();
    }

    private static String jpeg(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output));
        bitmap.recycle();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }
}
