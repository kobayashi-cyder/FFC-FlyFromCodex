package com.ffc.banc888.fly;

import org.json.JSONArray;
import org.json.JSONObject;

final class AutomatonExecutionEngine {
    private JSONObject spec;
    private String currentState;
    private boolean allowUnreviewed;

    String load(String json, boolean allowUnreviewed) {
        JSONObject out = new JSONObject();
        try {
            JSONObject parsed = new JSONObject(json == null ? "{}" : json);
            if (parsed.optInt("schema_version", 0) != 1) throw new IllegalArgumentException("unsupported schema");
            String initial = parsed.optString("initial_state", "");
            JSONObject states = parsed.optJSONObject("states");
            if (initial.isEmpty() || states == null || !states.has(initial)) throw new IllegalArgumentException("invalid automaton");
            this.spec = parsed;
            this.currentState = initial;
            this.allowUnreviewed = allowUnreviewed;
            out.put("ok", true).put("state", currentState).put("accessibility_ready", AutomatonAccessibilityService.isReady());
        } catch (Exception e) {
            try { out.put("ok", false).put("error", e.getMessage() == null ? e.toString() : e.getMessage()); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    String reset() {
        if (spec == null) return error("no automaton loaded");
        currentState = spec.optString("initial_state", currentState);
        return okState(false, null);
    }

    String step(String observedState) {
        if (spec == null) return error("no automaton loaded");
        try {
            JSONObject states = spec.getJSONObject("states");
            if (observedState != null && !observedState.isEmpty() && states.has(observedState)) currentState = observedState;
            JSONObject state = states.getJSONObject(currentState);
            JSONArray transitions = state.optJSONArray("transitions");
            if (state.optBoolean("terminal", false) || transitions == null || transitions.length() == 0) {
                return okState(false, "terminal");
            }

            JSONObject best = null;
            double bestConfidence = -1;
            for (int i = 0; i < transitions.length(); i++) {
                JSONObject t = transitions.optJSONObject(i);
                if (t == null) continue;
                double c = t.optDouble("confidence", 0.0);
                if (c < 0.50) continue;
                if (!allowUnreviewed && t.optBoolean("requires_review", false)) continue;
                if (c > bestConfidence) { best = t; bestConfidence = c; }
            }
            if (best == null) return okState(false, "no_safe_transition");

            JSONObject action = best.optJSONObject("action");
            if (action == null) return error("transition has no action");
            boolean executed = AutomatonAccessibilityService.execute(action);
            if (!executed) return error(AutomatonAccessibilityService.isReady() ? "action execution failed" : "accessibility service not enabled");
            currentState = best.optString("to_state", currentState);
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("executed", true);
            out.put("state", currentState);
            out.put("confidence", bestConfidence);
            out.put("action", action);
            out.put("max_delay_ms", best.optLong("max_delay_ms", 5000));
            return out.toString();
        } catch (Exception e) {
            return error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    String status() {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("loaded", spec != null);
            o.put("state", currentState == null ? JSONObject.NULL : currentState);
            o.put("allow_unreviewed", allowUnreviewed);
            o.put("accessibility_ready", AutomatonAccessibilityService.isReady());
        } catch (Exception ignored) {}
        return o.toString();
    }

    private String okState(boolean executed, String reason) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true).put("executed", executed).put("state", currentState);
            if (reason != null) o.put("reason", reason);
        } catch (Exception ignored) {}
        return o.toString();
    }

    private static String error(String message) {
        JSONObject o = new JSONObject();
        try { o.put("ok", false).put("error", message); } catch (Exception ignored) {}
        return o.toString();
    }
}
