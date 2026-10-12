package com.ffc.banc888.fly;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class RecordingVideoAnalyzer {
    private static final int GRID = 16;
    private static final int STATE_HAMMING = 5;
    private final Context context;

    RecordingVideoAnalyzer(Context context) {
        this.context = context.getApplicationContext();
    }

    JSONObject analyze(Uri uri, int sampleMs, int maxFrames) {
        JSONObject out = new JSONObject();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            long durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 0L);
            int stepMs = Math.max(80, sampleMs);
            int limit = Math.max(2, Math.min(maxFrames <= 0 ? 600 : maxFrames, 2000));
            List<Sample> samples = new ArrayList<>();
            for (long t = 0; t <= durationMs && samples.size() < limit; t += stepMs) {
                Bitmap frame = frameAt(retriever, t * 1000L);
                if (frame == null) continue;
                samples.add(Sample.from(frame, t));
                frame.recycle();
            }

            JSONArray events = new JSONArray();
            List<Long> canonicalHashes = new ArrayList<>();
            List<String> canonicalKeys = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                Sample s = samples.get(i);
                String stateKey = canonicalState(s.hash, canonicalHashes, canonicalKeys);
                JSONObject frameEvent = new JSONObject();
                frameEvent.put("timestamp_ms", s.timeMs);
                frameEvent.put("state_key", stateKey);
                frameEvent.put("kind", "frame");
                frameEvent.put("confidence", 0.92);
                events.put(frameEvent);

                if (i > 0 && i + 1 < samples.size()) {
                    TapCandidate tap = inferTap(samples.get(i - 1), s, samples.get(i + 1));
                    if (tap != null) {
                        JSONObject tapEvent = new JSONObject();
                        tapEvent.put("timestamp_ms", s.timeMs);
                        tapEvent.put("state_key", stateKey);
                        tapEvent.put("kind", "tap");
                        tapEvent.put("x", tap.x);
                        tapEvent.put("y", tap.y);
                        tapEvent.put("confidence", tap.confidence);
                        JSONObject meta = new JSONObject();
                        meta.put("heuristic", "localized_transient_change");
                        meta.put("requires_review", true);
                        tapEvent.put("metadata", meta);
                        events.put(tapEvent);
                    }
                }
            }

            out.put("ok", true);
            out.put("source_uri", uri.toString());
            out.put("duration_ms", durationMs);
            out.put("sample_ms", stepMs);
            out.put("frame_count", samples.size());
            out.put("events", events);
            out.put("tap_inference", "heuristic_only_when_touch_feedback_is_visible");
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
        }
        return out;
    }

    private Bitmap frameAt(MediaMetadataRetriever retriever, long timeUs) {
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                return retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 96, 96);
            }
            Bitmap raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (raw == null) return null;
            Bitmap scaled = Bitmap.createScaledBitmap(raw, 96, 96, true);
            if (scaled != raw) raw.recycle();
            return scaled;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String canonicalState(long hash, List<Long> hashes, List<String> keys) {
        for (int i = 0; i < hashes.size(); i++) {
            if (Long.bitCount(hash ^ hashes.get(i)) <= STATE_HAMMING) return keys.get(i);
        }
        String key = "v_" + String.format("%016x", hash);
        hashes.add(hash);
        keys.add(key);
        return key;
    }

    private static TapCandidate inferTap(Sample prev, Sample cur, Sample next) {
        if (Long.bitCount(prev.hash ^ next.hash) < 10) return null;
        int changed = 0;
        int minX = GRID, minY = GRID, maxX = -1, maxY = -1;
        double sumX = 0, sumY = 0, weight = 0;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int idx = y * GRID + x;
                int d = Math.abs(cur.luma[idx] - prev.luma[idx]);
                if (d < 42) continue;
                changed++;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                sumX += (x + 0.5) * d;
                sumY += (y + 0.5) * d;
                weight += d;
            }
        }
        double ratio = changed / (double)(GRID * GRID);
        if (changed < 2 || ratio < 0.006 || ratio > 0.08 || weight <= 0) return null;
        double boxW = (maxX - minX + 1) / (double)GRID;
        double boxH = (maxY - minY + 1) / (double)GRID;
        if (boxW > 0.35 || boxH > 0.35) return null;
        double x = Math.max(0.0, Math.min(1.0, sumX / weight / GRID));
        double y = Math.max(0.0, Math.min(1.0, sumY / weight / GRID));
        double confidence = Math.min(0.72, 0.52 + (0.08 - ratio));
        return new TapCandidate(x, y, confidence);
    }

    private static long parseLong(String text, long fallback) {
        try { return Long.parseLong(text); } catch (Exception ignored) { return fallback; }
    }

    private static final class Sample {
        final long timeMs;
        final long hash;
        final int[] luma;

        Sample(long timeMs, long hash, int[] luma) {
            this.timeMs = timeMs;
            this.hash = hash;
            this.luma = luma;
        }

        static Sample from(Bitmap bitmap, long timeMs) {
            Bitmap small = Bitmap.createScaledBitmap(bitmap, GRID, GRID, true);
            int[] px = new int[GRID * GRID];
            small.getPixels(px, 0, GRID, 0, 0, GRID, GRID);
            int[] luma = new int[px.length];
            long total = 0;
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int y = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
                luma[i] = y;
                total += y;
            }
            int avg = (int)(total / px.length);
            long hash = 0L;
            for (int i = 0; i < 64; i++) {
                int src = (i / 8) * 2 * GRID + (i % 8) * 2;
                if (luma[src] >= avg) hash |= (1L << i);
            }
            if (small != bitmap) small.recycle();
            return new Sample(timeMs, hash, luma);
        }
    }

    private static final class TapCandidate {
        final double x, y, confidence;
        TapCandidate(double x, double y, double confidence) {
            this.x = x; this.y = y; this.confidence = confidence;
        }
    }
}
