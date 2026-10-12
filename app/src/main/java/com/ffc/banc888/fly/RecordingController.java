package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

final class RecordingController {
    static final int REQ_RECORDING = 889;

    private final Activity activity;
    private final RecordingVideoAnalyzer analyzer;
    private final RecordingAutomatonGenerator generator;
    private Uri selectedUri;
    private JSONObject lastAnalysis;
    private JSONObject lastAutomaton;

    RecordingController(Activity activity) {
        this.activity = activity;
        this.analyzer = new RecordingVideoAnalyzer(activity);
        this.generator = new RecordingAutomatonGenerator();
    }

    boolean pickRecording() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQ_RECORDING);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_RECORDING || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        selectedUri = uri;
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { activity.getContentResolver().takePersistableUriPermission(uri, flags); } catch (Throwable ignored) {}
        lastAnalysis = null;
        lastAutomaton = null;
    }

    String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("selected", selectedUri != null);
            o.put("uri", selectedUri == null ? JSONObject.NULL : selectedUri.toString());
            o.put("has_analysis", lastAnalysis != null && lastAnalysis.optBoolean("ok", false));
            o.put("has_automaton", lastAutomaton != null && lastAutomaton.has("states"));
            o.put("accessibility_ready", AutomatonAccessibilityService.isReady());
        } catch (Exception ignored) {}
        return o.toString();
    }

    String analyzeSelected(int sampleMs, int maxFrames) {
        if (selectedUri == null) return error("no recording selected");
        lastAnalysis = analyzer.analyze(selectedUri, sampleMs, maxFrames);
        return lastAnalysis.toString();
    }

    String generateSelected(String name, int sampleMs, int maxFrames) {
        if (selectedUri == null) return error("no recording selected");
        if (lastAnalysis == null || !lastAnalysis.optBoolean("ok", false)) {
            lastAnalysis = analyzer.analyze(selectedUri, sampleMs, maxFrames);
        }
        if (!lastAnalysis.optBoolean("ok", false)) return lastAnalysis.toString();
        lastAutomaton = generator.generate(lastAnalysis, name);
        return lastAutomaton.toString();
    }

    private static String error(String message) {
        JSONObject o = new JSONObject();
        try { o.put("ok", false).put("error", message); } catch (Exception ignored) {}
        return o.toString();
    }
}
