package com.ffc.banc888agent;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentCommandEngine {
    private static final Pattern TAP = Pattern.compile("(?:tap|タップ)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*[,， ]+\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SWIPE = Pattern.compile("(?:swipe|スワイプ)\\s*[:：]?\\s*(\\d+)\\s*[,， ]+\\s*(\\d+)\\s*[,， ]+\\s*(\\d+)\\s*[,， ]+\\s*(\\d+)(?:\\s*[,， ]+\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_CLICK = Pattern.compile("[「『\\\"](.+?)[」』\\\"]\\s*(?:を)?\\s*(?:押す|クリック|タップ)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_CLICK = Pattern.compile("(?:click|press)\\s+(.+)", Pattern.CASE_INSENSITIVE);

    public String run(String command) {
        String c = command == null ? "" : command.trim();
        BancAccessibilityService s = BancAccessibilityService.getInstance();
        if (c.isEmpty()) return json(false, "empty_command", null);

        String lower = c.toLowerCase(Locale.ROOT);
        if (lower.equals("権限") || lower.contains("accessibility status")) {
            return json(s != null, s != null ? "enabled" : "disabled", null);
        }
        if (s == null) return json(false, "accessibility_disabled", "Android設定で操作権限を有効化してください");

        if (lower.equals("画面取得") || lower.equals("snapshot") || lower.equals("screen")) {
            return s.snapshotJson();
        }
        if (lower.equals("戻る") || lower.equals("back")) return json(s.globalBack(), "back", null);
        if (lower.equals("ホーム") || lower.equals("home")) return json(s.globalHome(), "home", null);
        if (lower.equals("履歴") || lower.equals("recents")) return json(s.globalRecents(), "recents", null);

        Matcher m = TAP.matcher(c);
        if (m.find()) {
            float x = Float.parseFloat(m.group(1));
            float y = Float.parseFloat(m.group(2));
            return json(s.tap(x, y), "tap", x + "," + y);
        }

        m = SWIPE.matcher(c);
        if (m.find()) {
            float x1 = Float.parseFloat(m.group(1));
            float y1 = Float.parseFloat(m.group(2));
            float x2 = Float.parseFloat(m.group(3));
            float y2 = Float.parseFloat(m.group(4));
            long d = m.group(5) == null ? 450 : Long.parseLong(m.group(5));
            return json(s.swipe(x1, y1, x2, y2, d), "swipe", x1 + "," + y1 + "->" + x2 + "," + y2);
        }

        m = QUOTED_CLICK.matcher(c);
        if (m.find()) {
            String target = m.group(1).trim();
            return json(s.clickText(target), "click_text", target);
        }

        m = EN_CLICK.matcher(c);
        if (m.matches()) {
            String target = m.group(1).trim();
            return json(s.clickText(target), "click_text", target);
        }

        if (c.endsWith("を押す")) {
            String target = c.substring(0, c.length() - 3).trim();
            return json(s.clickText(target), "click_text", target);
        }

        return json(false, "unknown_command", "対応: 画面取得 / 戻る / ホーム / 履歴 / タップ x y / スワイプ x1 y1 x2 y2 [ms] / 「文字」を押す");
    }

    private String json(boolean ok, String action, String detail) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            o.put("action", action);
            if (detail != null) o.put("detail", detail);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":" + ok + "}";
        }
    }
}
