package com.ffc.banc888agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BancAccessibilityService extends AccessibilityService {
    private static volatile BancAccessibilityService instance;

    public static BancAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Event-driven autonomy is intentionally disabled.
        // Actions occur only from explicit local commands.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public boolean tap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        return dispatchGesture(g, null, null);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        long d = Math.max(100, Math.min(durationMs, 3000));
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, d))
                .build();
        return dispatchGesture(g, null, null);
    }

    public boolean globalBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean globalHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean globalRecents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }

    public boolean clickText(String target) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || target == null || target.isBlank()) return false;
        AccessibilityNodeInfo hit = findByText(root, target.trim(), 0);
        if (hit == null) return false;

        AccessibilityNodeInfo node = hit;
        for (int i = 0; i < 8 && node != null; i++) {
            if (node.isClickable() && node.isEnabled()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            node = node.getParent();
        }
        Rect b = new Rect();
        hit.getBoundsInScreen(b);
        return !b.isEmpty() && tap(b.exactCenterX(), b.exactCenterY());
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 30) return null;
        if (!node.isPassword()) {
            CharSequence t = node.getText();
            CharSequence d = node.getContentDescription();
            if ((t != null && t.toString().contains(target))
                    || (d != null && d.toString().contains(target))) {
                return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByText(node.getChild(i), target, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    public String snapshotJson() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "{\"ok\":false,\"error\":\"no_active_window\"}";
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("package", String.valueOf(root.getPackageName()));
            JSONArray nodes = new JSONArray();
            int[] count = new int[]{0};
            appendSnapshot(root, nodes, 0, count);
            out.put("nodes", nodes);
            out.put("count", count[0]);
            return out.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"snapshot_failed\"}";
        }
    }

    private void appendSnapshot(AccessibilityNodeInfo node, JSONArray out, int depth, int[] count) throws Exception {
        if (node == null || depth > 14 || count[0] >= 300) return;
        JSONObject item = new JSONObject();
        item.put("depth", depth);
        item.put("class", String.valueOf(node.getClassName()));
        item.put("clickable", node.isClickable());
        item.put("enabled", node.isEnabled());

        Rect r = new Rect();
        node.getBoundsInScreen(r);
        item.put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));

        if (!node.isPassword()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            if (text != null && text.length() > 0) item.put("text", text.toString());
            if (desc != null && desc.length() > 0) item.put("desc", desc.toString());
        } else {
            item.put("password", true);
        }

        out.put(item);
        count[0]++;
        for (int i = 0; i < node.getChildCount(); i++) {
            appendSnapshot(node.getChild(i), out, depth + 1, count);
            if (count[0] >= 300) break;
        }
    }
}
