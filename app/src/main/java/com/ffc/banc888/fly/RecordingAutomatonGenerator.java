package com.ffc.banc888.fly;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

final class RecordingAutomatonGenerator {
    private static final Set<String> ACTIONS = new HashSet<>();
    static {
        ACTIONS.add("tap"); ACTIONS.add("swipe"); ACTIONS.add("wait");
        ACTIONS.add("back"); ACTIONS.add("launch"); ACTIONS.add("noop");
    }

    JSONObject generate(JSONObject analysis, String name) {
        JSONObject out = new JSONObject();
        try {
            if (!analysis.optBoolean("ok", false)) return analysis;
            JSONArray events = analysis.optJSONArray("events");
            if (events == null || events.length() == 0) {
                out.put("ok", false).put("error", "no recording events");
                return out;
            }

            JSONObject states = new JSONObject();
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                String key = e.optString("state_key", "unknown");
                if (!states.has(key)) states.put(key, newState(key));
            }

            JSONObject first = firstFrame(events);
            if (first == null) {
                out.put("ok", false).put("error", "no visual frame events");
                return out;
            }
            String initial = first.optString("state_key", "unknown");
            String current = initial;
            long lastStateTime = first.optLong("timestamp_ms", 0L);
            JSONObject pendingAction = null;
            int transitionCount = 0;

            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null || e == first) continue;
                String kind = e.optString("kind", "frame");
                if (ACTIONS.contains(kind) && !"noop".equals(kind)) {
                    pendingAction = e;
                    continue;
                }
                if (!"frame".equals(kind)) continue;
                String next = e.optString("state_key", current);
                if (next.equals(current)) continue;

                long elapsed = Math.max(0L, e.optLong("timestamp_ms", lastStateTime) - lastStateTime);
                JSONObject action = actionFrom(pendingAction);
                double actionConfidence = action.optDouble("confidence", 0.4);
                double confidence = Math.min(e.optDouble("confidence", 0.9), actionConfidence);
                JSONObject transition = new JSONObject();
                transition.put("to_state", next);
                transition.put("action", action);
                transition.put("min_delay_ms", 0);
                transition.put("max_delay_ms", Math.min(15000L, Math.max(500L, elapsed + 1000L)));
                transition.put("confidence", confidence);
                transition.put("requires_review", confidence < 0.65 || "noop".equals(action.optString("kind")));
                states.getJSONObject(current).getJSONArray("transitions").put(transition);
                transitionCount++;

                current = next;
                lastStateTime = e.optLong("timestamp_ms", lastStateTime);
                pendingAction = null;
            }

            for (String key : keys(states)) {
                JSONObject state = states.getJSONObject(key);
                state.put("terminal", state.getJSONArray("transitions").length() == 0);
            }

            out.put("schema_version", 1);
            out.put("name", name == null || name.trim().isEmpty() ? "recording_automaton" : name.trim());
            out.put("source", "screen_recording");
            out.put("initial_state", initial);
            out.put("states", states);
            JSONObject meta = new JSONObject();
            meta.put("event_count", events.length());
            meta.put("transition_count", transitionCount);
            meta.put("coordinates", "normalized_0_to_1");
            meta.put("recording_duration_ms", analysis.optLong("duration_ms", 0L));
            meta.put("tap_inference", analysis.optString("tap_inference", "none"));
            out.put("metadata", meta);
        } catch (Exception e) {
            try {
                out = new JSONObject();
                out.put("ok", false).put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static JSONObject firstFrame(JSONArray events) {
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && "frame".equals(e.optString("kind", "frame"))) return e;
        }
        return null;
    }

    private static JSONObject newState(String key) throws Exception {
        JSONObject state = new JSONObject();
        state.put("key", key);
        state.put("terminal", false);
        state.put("transitions", new JSONArray());
        return state;
    }

    private static JSONObject actionFrom(JSONObject e) throws Exception {
        JSONObject action = new JSONObject();
        if (e == null) {
            action.put("kind", "noop");
            action.put("args", new JSONObject());
            action.put("confidence", 0.40);
            action.put("source", "recording-inferred");
            return action;
        }
        String kind = e.optString("kind", "noop");
        JSONObject args = new JSONObject();
        if ("tap".equals(kind)) {
            args.put("x", e.optDouble("x", 0.5));
            args.put("y", e.optDouble("y", 0.5));
        } else if ("swipe".equals(kind)) {
            args.put("x1", e.optDouble("x", 0.5));
            args.put("y1", e.optDouble("y", 0.5));
            args.put("x2", e.optDouble("x2", 0.5));
            args.put("y2", e.optDouble("y2", 0.5));
            args.put("duration_ms", e.optInt("duration_ms", 250));
        } else if ("wait".equals(kind)) {
            args.put("duration_ms", e.optInt("duration_ms", 500));
        } else if (e.has("label")) {
            args.put("label", e.optString("label", ""));
        }
        action.put("kind", ACTIONS.contains(kind) ? kind : "noop");
        action.put("args", args);
        action.put("confidence", e.optDouble("confidence", 0.5));
        action.put("source", "recording");
        return action;
    }

    private static Set<String> keys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        java.util.Iterator<String> it = object.keys();
        while (it.hasNext()) keys.add(it.next());
        return keys;
    }
}
