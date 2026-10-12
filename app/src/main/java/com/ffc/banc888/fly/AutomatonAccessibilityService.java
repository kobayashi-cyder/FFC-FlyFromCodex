package com.ffc.banc888.fly;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

public class AutomatonAccessibilityService extends AccessibilityService {
    private static WeakReference<AutomatonAccessibilityService> current = new WeakReference<>(null);

    static boolean isReady() {
        return current.get() != null;
    }

    static boolean execute(JSONObject action) {
        AutomatonAccessibilityService service = current.get();
        if (service == null || action == null) return false;
        String kind = action.optString("kind", "noop");
        JSONObject args = action.optJSONObject("args");
        if (args == null) args = new JSONObject();
        switch (kind) {
            case "tap":
                return service.tap(args.optDouble("x", 0.5), args.optDouble("y", 0.5));
            case "swipe":
                return service.swipe(
                        args.optDouble("x1", 0.5), args.optDouble("y1", 0.5),
                        args.optDouble("x2", 0.5), args.optDouble("y2", 0.5),
                        args.optInt("duration_ms", 250));
            case "back":
                return service.performGlobalAction(GLOBAL_ACTION_BACK);
            case "noop":
            case "wait":
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        current = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        AutomatonAccessibilityService service = current.get();
        if (service == this) current.clear();
        super.onDestroy();
    }

    private boolean tap(double nx, double ny) {
        if (Build.VERSION.SDK_INT < 24) return false;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        float x = (float)(Math.max(0.0, Math.min(1.0, nx)) * Math.max(1, width - 1));
        float y = (float)(Math.max(0.0, Math.min(1.0, ny)) * Math.max(1, height - 1));
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 80))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean swipe(double nx1, double ny1, double nx2, double ny2, int durationMs) {
        if (Build.VERSION.SDK_INT < 24) return false;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        float x1 = (float)(Math.max(0.0, Math.min(1.0, nx1)) * Math.max(1, width - 1));
        float y1 = (float)(Math.max(0.0, Math.min(1.0, ny1)) * Math.max(1, height - 1));
        float x2 = (float)(Math.max(0.0, Math.min(1.0, nx2)) * Math.max(1, width - 1));
        float y2 = (float)(Math.max(0.0, Math.min(1.0, ny2)) * Math.max(1, height - 1));
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(80, durationMs)))
                .build();
        return dispatchGesture(gesture, null, null);
    }
}
