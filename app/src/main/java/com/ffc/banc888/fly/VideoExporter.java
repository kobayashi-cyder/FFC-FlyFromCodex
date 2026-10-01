package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One acknowledged frame at a time; callers dispatch encoding off the UI thread. */
final class VideoExporter {
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int MAX_PIXELS = 1280 * 720;
    private static final int MAX_JPEG_BYTES = 2 * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final long IDLE_TIMEOUT_MS = 20000;
    private static final long JOB_TIMEOUT_MS = 120000;
    private final Activity activity;
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BANC888-MP4-expiry");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> deadlineCheck;
    private Job active;
    private volatile boolean closed;

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        int width, height, fps, expectedFrames, frames, colorFormat, track = -1;
        int[] pixels;
        String state = "encoding", codecName, error;
        File temporary, target;
        MediaCodec codec;
        MediaMuxer muxer;
        boolean muxerStarted, outputEos;
        long started = SystemClock.elapsedRealtime(), touched = started;
        final MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
    }

    VideoExporter(Activity activity) {
        this.activity = activity;
    }

    synchronized String begin(int width, int height, int fps, int frameCount, String filename) {
        if (closed) return error("video exporter is closed", null);
        expire();
        if (active != null) return error("video export busy; finish or cancel the active job", active);
        if (width < 16 || height < 16 || width > 1280 || height > 1280
                || (width & 1) != 0 || (height & 1) != 0 || (long) width * height > MAX_PIXELS
                || fps < 1 || fps > 30 || frameCount < 1 || frameCount > 450
                || frameCount > fps * 15) {
            return error("video limits: even dimensions, at most 921600 pixels / 1280 per side, 1..30 fps, 1..15 seconds", null);
        }
        Job job = new Job();
        job.width = width;
        job.height = height;
        job.fps = fps;
        job.expectedFrames = frameCount;
        try {
            File dir = new File(activity.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create exports directory");
            pruneFiles(dir);
            job.target = new File(dir, "BANC888_video_" + job.id.substring(0, 8) + "_" + safeName(filename));
            job.temporary = new File(dir, job.target.getName() + ".part");
            configureEncoder(job);
            job.muxer = new MediaMuxer(job.temporary.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            job.pixels = new int[width * height];
            jobs.put(job.id, job);
            active = job;
            deadlineCheck = watchdog.scheduleAtFixedRate(() -> {
                synchronized (VideoExporter.this) { expire(); }
            }, 5, 5, TimeUnit.SECONDS);
            trimJobs();
            return response(job, true).toString();
        } catch (Exception e) {
            fail(job, "H.264 encoder unavailable or initialization failed: " + message(e));
            return error(job.error, job);
        }
    }

    synchronized String append(String jobId, int frameIndex, String base64Jpeg) {
        expire();
        Job job = jobs.get(jobId);
        if (job == null || job != active || !"encoding".equals(job.state)) return error("no active video job", job);
        if (frameIndex != job.frames || frameIndex >= job.expectedFrames) return error("video frame order mismatch", job);
        if (base64Jpeg == null || base64Jpeg.length() > ((MAX_JPEG_BYTES + 2) / 3) * 4 + 64) {
            fail(job, "JPEG frame exceeds 2 MiB limit");
            return error(job.error, job);
        }
        Bitmap bitmap = null;
        try {
            String payload = base64Jpeg;
            if (payload.startsWith("data:image/jpeg;base64,")) payload = payload.substring(23);
            byte[] jpeg = Base64.decode(payload, Base64.DEFAULT);
            if (jpeg.length > MAX_JPEG_BYTES) throw new IllegalArgumentException("JPEG frame exceeds 2 MiB limit");
            if (jpeg.length < 4 || (jpeg[0] & 255) != 255 || (jpeg[1] & 255) != 216) {
                throw new IllegalArgumentException("frame is not a JPEG");
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
            if (bounds.outWidth != job.width || bounds.outHeight != job.height) {
                throw new IllegalArgumentException("JPEG dimensions do not match the video job");
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
            if (bitmap == null) throw new IllegalArgumentException("cannot decode JPEG frame");
            bitmap.getPixels(job.pixels, 0, job.width, 0, 0, job.width, job.height);
            bitmap.recycle();
            bitmap = null;
            int input = awaitInput(job, 5000);
            fillInput(job, input);
            job.codec.queueInputBuffer(input, 0, job.width * job.height * 3 / 2,
                    (long) job.frames * 1000000 / job.fps, 0);
            job.frames++;
            job.touched = SystemClock.elapsedRealtime();
            drain(job, false);
            if (job.temporary.length() > MAX_FILE_BYTES) throw new IllegalStateException("encoded video exceeds 16 MiB limit");
            return response(job, true).toString();
        } catch (Exception e) {
            fail(job, message(e));
            return error(job.error, job);
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    synchronized String finish(String jobId) {
        expire();
        Job job = jobs.get(jobId);
        if (job != null && "complete".equals(job.state)) return response(job, true).toString();
        if (job == null || job != active) return error("no active video job", job);
        if (job.frames != job.expectedFrames) return error("video has incomplete frames", job);
        try {
            int input = awaitInput(job, 5000);
            job.codec.queueInputBuffer(input, 0, 0, (long) job.frames * 1000000 / job.fps,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain(job, true);
            if (!job.muxerStarted || !job.outputEos) throw new IllegalStateException("encoder did not produce a complete AVC stream");
            job.muxer.stop();
            job.muxerStarted = false;
            release(job);
            if (!job.temporary.isFile() || job.temporary.length() < 32 || job.temporary.length() > MAX_FILE_BYTES) {
                throw new IllegalStateException("encoded MP4 size invalid");
            }
            if (!job.temporary.renameTo(job.target)) throw new IllegalStateException("cannot finalize MP4 export");
            job.state = "complete";
            active = null;
            stopDeadlineCheck();
            return response(job, true).toString();
        } catch (Exception e) {
            fail(job, message(e));
            return error(job.error, job);
        }
    }

    synchronized String status(String jobId) {
        expire();
        Job job = (jobId == null || jobId.isEmpty()) ? active : jobs.get(jobId);
        if (job == null) return error("video job not found", null);
        return response(job, !"error".equals(job.state)).toString();
    }

    synchronized String cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return error("video job not found", null);
        if ("complete".equals(job.state)) return error("completed MP4 is already finalized", job);
        release(job);
        if (job.temporary != null) job.temporary.delete();
        job.state = "cancelled";
        if (active == job) {
            active = null;
            stopDeadlineCheck();
        }
        return response(job, true).toString();
    }

    synchronized String share(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !"complete".equals(job.state) || !job.target.isFile()) {
            return error("completed MP4 export not found", job);
        }
        final Uri uri = fileUri(job);
        activity.runOnUiThread(() -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("video/mp4");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newRawUri(job.target.getName(), uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(send, "BANC888 MP4 を共有"));
            } catch (RuntimeException error) {
                android.util.Log.w("BANC888-MP4", "Cannot open sharing chooser", error);
            }
        });
        JSONObject result = response(job, true);
        put(result, "shared", true);
        return result.toString();
    }

    void close() {
        if (closed) return;
        closed = true;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Activity destruction must not wait for codec.stop or an in-flight append.
            watchdog.execute(this::closeInternal);
        } else {
            closeInternal();
        }
    }

    private synchronized void closeInternal() {
        if (active != null) cancel(active.id);
        watchdog.shutdownNow();
        jobs.clear();
    }

    private void configureEncoder(Job job) throws Exception {
        Exception last = null;
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            boolean avc = false;
            for (String type : info.getSupportedTypes()) if (MIME.equalsIgnoreCase(type)) avc = true;
            if (!avc) continue;
            MediaCodec candidate = null;
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(MIME);
                if (!caps.getVideoCapabilities().areSizeAndRateSupported(job.width, job.height, job.fps)) continue;
                int color = 0;
                for (int preferred : new int[]{MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar}) {
                    for (int supported : caps.colorFormats) if (supported == preferred) color = preferred;
                    if (color != 0) break;
                }
                if (color == 0) continue;
                MediaFormat format = MediaFormat.createVideoFormat(MIME, job.width, job.height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
                format.setInteger(MediaFormat.KEY_BIT_RATE,
                        Math.max(256000, Math.min(6000000, job.width * job.height * job.fps / 8)));
                format.setInteger(MediaFormat.KEY_FRAME_RATE, job.fps);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                candidate = MediaCodec.createByCodecName(info.getName());
                candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                candidate.start();
                job.codec = candidate;
                job.codecName = info.getName();
                job.colorFormat = color;
                return;
            } catch (Exception e) {
                last = e;
                if (candidate != null) try { candidate.release(); } catch (Exception ignored) {}
            }
        }
        throw new IllegalStateException("no supported H.264 YUV420 encoder" + (last == null ? "" : ": " + message(last)));
    }

    private int awaitInput(Job job, long timeoutMs) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            int input = job.codec.dequeueInputBuffer(10000);
            if (input >= 0) return input;
            drain(job, false);
        }
        throw new IllegalStateException("H.264 encoder input timed out");
    }

    private void fillInput(Job job, int index) throws Exception {
        Image image = job.codec.getInputImage(index);
        if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            if (image.getFormat() != android.graphics.ImageFormat.YUV_420_888 || planes.length != 3) {
                throw new IllegalStateException("encoder input image is not YUV420");
            }
            int left = image.getCropRect().left;
            int top = image.getCropRect().top;
            for (int y = 0; y < job.height; y++) {
                for (int x = 0; x < job.width; x++) {
                    int rgb = job.pixels[y * job.width + x];
                    putPlane(planes[0], x + left, y + top, luminance(rgb));
                }
            }
            for (int y = 0; y < job.height; y += 2) {
                for (int x = 0; x < job.width; x += 2) {
                    int rgb = averageRgb(job, x, y);
                    putPlane(planes[1], (x + left) / 2, (y + top) / 2, chromaU(rgb));
                    putPlane(planes[2], (x + left) / 2, (y + top) / 2, chromaV(rgb));
                }
            }
            return;
        }
        if (job.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
            throw new IllegalStateException("H.264 encoder does not expose flexible YUV input planes");
        }
        ByteBuffer buffer = job.codec.getInputBuffer(index);
        if (buffer == null || buffer.capacity() < job.width * job.height * 3 / 2) {
            throw new IllegalStateException("H.264 encoder input buffer too small");
        }
        buffer.clear();
        int pixels = job.width * job.height;
        for (int i = 0; i < pixels; i++) buffer.put((byte) luminance(job.pixels[i]));
        for (int y = 0; y < job.height; y += 2) {
            for (int x = 0; x < job.width; x += 2) {
                int rgb = averageRgb(job, x, y);
                if (job.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    buffer.put((byte) chromaU(rgb));
                    buffer.put((byte) chromaV(rgb));
                } else {
                    int offset = (y / 2) * (job.width / 2) + x / 2;
                    buffer.put(pixels + offset, (byte) chromaU(rgb));
                    buffer.put(pixels + pixels / 4 + offset, (byte) chromaV(rgb));
                }
            }
        }
    }

    private static void putPlane(Image.Plane plane, int x, int y, int value) {
        ByteBuffer buffer = plane.getBuffer();
        buffer.put(buffer.position() + y * plane.getRowStride() + x * plane.getPixelStride(), (byte) value);
    }

    private static int averageRgb(Job job, int x, int y) {
        int r = 0, g = 0, b = 0;
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            int rgb = job.pixels[(y + dy) * job.width + x + dx];
            r += (rgb >> 16) & 255;
            g += (rgb >> 8) & 255;
            b += rgb & 255;
        }
        return ((r / 4) << 16) | ((g / 4) << 8) | b / 4;
    }

    private static int luminance(int rgb) {
        return ((66 * ((rgb >> 16) & 255) + 129 * ((rgb >> 8) & 255) + 25 * (rgb & 255) + 128) >> 8) + 16;
    }

    private static int chromaU(int rgb) {
        return ((-38 * ((rgb >> 16) & 255) - 74 * ((rgb >> 8) & 255) + 112 * (rgb & 255) + 128) >> 8) + 128;
    }

    private static int chromaV(int rgb) {
        return ((112 * ((rgb >> 16) & 255) - 94 * ((rgb >> 8) & 255) - 18 * (rgb & 255) + 128) >> 8) + 128;
    }

    private void drain(Job job, boolean toEos) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + (toEos ? 10000 : 1000);
        do {
            int index = job.codec.dequeueOutputBuffer(job.outputInfo, toEos ? 10000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!toEos) return;
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (job.muxerStarted) throw new IllegalStateException("encoder output format changed twice");
                job.track = job.muxer.addTrack(job.codec.getOutputFormat());
                job.muxer.start();
                job.muxerStarted = true;
                continue;
            }
            if (index < 0) continue;
            try {
                ByteBuffer output = job.codec.getOutputBuffer(index);
                if ((job.outputInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) job.outputInfo.size = 0;
                if (job.outputInfo.size > 0) {
                    if (!job.muxerStarted || output == null) throw new IllegalStateException("AVC sample arrived before its format");
                    output.position(job.outputInfo.offset);
                    output.limit(job.outputInfo.offset + job.outputInfo.size);
                    job.muxer.writeSampleData(job.track, output, job.outputInfo);
                }
                if ((job.outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    job.outputEos = true;
                    return;
                }
            } finally {
                job.codec.releaseOutputBuffer(index, false);
            }
        } while (SystemClock.elapsedRealtime() < deadline);
        if (toEos) throw new IllegalStateException("H.264 encoder output timed out");
    }

    private void expire() {
        long now = SystemClock.elapsedRealtime();
        if (active != null && (now - active.touched > IDLE_TIMEOUT_MS || now - active.started > JOB_TIMEOUT_MS)) {
            fail(active, "video export timed out");
        }
    }

    private void fail(Job job, String reason) {
        release(job);
        if (job.temporary != null) job.temporary.delete();
        job.state = "error";
        job.error = reason;
        if (active == job) {
            active = null;
            stopDeadlineCheck();
        }
    }

    private void stopDeadlineCheck() {
        if (deadlineCheck != null) deadlineCheck.cancel(false);
        deadlineCheck = null;
    }

    private void release(Job job) {
        if (job.codec != null) {
            try { job.codec.stop(); } catch (Exception ignored) {}
            try { job.codec.release(); } catch (Exception ignored) {}
            job.codec = null;
        }
        if (job.muxer != null) {
            try { job.muxer.release(); } catch (Exception ignored) {}
            job.muxer = null;
        }
        job.pixels = null;
    }

    private void trimJobs() {
        Iterator<Map.Entry<String, Job>> iterator = jobs.entrySet().iterator();
        while (jobs.size() > 5 && iterator.hasNext()) {
            Job old = iterator.next().getValue();
            if (old == active) continue;
            if (old.target != null) old.target.delete();
            if (old.temporary != null) old.temporary.delete();
            iterator.remove();
        }
    }

    private void pruneFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("BANC888_video_")
                && (name.endsWith(".mp4") || name.endsWith(".mp4.part")));
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int retained = 0;
        for (File file : files) {
            if (file.getName().endsWith(".part") || retained++ >= 4) file.delete();
        }
    }

    private JSONObject response(Job job, boolean ok) {
        JSONObject result = new JSONObject();
        put(result, "ok", ok);
        put(result, "jobId", job.id);
        put(result, "state", job.state);
        put(result, "frames", job.frames);
        put(result, "frameCount", job.expectedFrames);
        put(result, "width", job.width);
        put(result, "height", job.height);
        put(result, "fps", job.fps);
        put(result, "durationMs", Math.round(job.expectedFrames * 1000.0 / Math.max(1, job.fps)));
        put(result, "mime", "video/mp4");
        put(result, "codec", job.codecName);
        if (job.target != null) put(result, "name", job.target.getName());
        put(result, "shared", false);
        if (job.error != null) put(result, "error", job.error);
        if ("complete".equals(job.state) && job.target != null && job.target.isFile()) {
            put(result, "bytes", job.target.length());
            put(result, "fileUri", fileUri(job).toString());
        }
        return result;
    }

    private String error(String message, Job job) {
        JSONObject result = job == null ? new JSONObject() : response(job, false);
        put(result, "ok", false);
        put(result, "error", message);
        return result.toString();
    }

    private Uri fileUri(Job job) {
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", job.target);
    }

    private static String safeName(String filename) {
        String name = filename == null ? "clip" : filename.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replace("..", "_");
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp4")) name = name.substring(0, name.length() - 4);
        if (name.length() > 64) name = name.substring(0, 64);
        if (name.isEmpty()) name = "clip";
        return name + ".mp4";
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Exception ignored) {}
    }
}
