package com.ffc.banc888.fly;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ResearchClient {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BANC888-Research");
        t.setDaemon(true);
        return t;
    });

    String searchWikipedia(String query, int limit) {
        return await(() -> searchWikipediaBlocking(query, limit));
    }

    String searchCrossref(String query, int limit) {
        return await(() -> searchCrossrefBlocking(query, limit));
    }

    private String await(Callable<String> work) {
        Future<String> future = executor.submit(work);
        try {
            return future.get(18, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return errorJson(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String searchWikipediaBlocking(String query, int limit) throws Exception {
        int n = Math.max(1, Math.min(5, limit));
        String q = URLEncoder.encode(query == null ? "" : query, "UTF-8");
        String url = "https://ja.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="
                + q + "&gsrlimit=" + n
                + "&prop=extracts|info&exintro=1&explaintext=1&inprop=url&format=json&utf8=1";
        String raw = httpGetJson(url, 80_000);
        JSONObject j = new JSONObject(raw);
        JSONObject queryObj = j.optJSONObject("query");
        JSONObject pages = queryObj == null ? null : queryObj.optJSONObject("pages");
        JSONArray arr = new JSONArray();
        if (pages != null) {
            java.util.Iterator<String> it = pages.keys();
            while (it.hasNext()) {
                JSONObject p = pages.optJSONObject(it.next());
                if (p == null) continue;
                JSONObject x = new JSONObject();
                x.put("title", p.optString("title", ""));
                x.put("url", p.optString("fullurl", ""));
                x.put("extract", p.optString("extract", ""));
                arr.put(x);
            }
        }
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("results", arr);
        out.put("provider", "wikipedia");
        return out.toString();
    }

    private String searchCrossrefBlocking(String query, int limit) throws Exception {
        int n = Math.max(1, Math.min(5, limit));
        String q = URLEncoder.encode(query == null ? "" : query, "UTF-8");
        String url = "https://api.crossref.org/works?rows=" + n
                + "&select=DOI,title,URL,author,published,abstract&query=" + q;
        String raw = httpGetJson(url, 80_000);
        JSONObject j = new JSONObject(raw);
        JSONObject message = j.optJSONObject("message");
        JSONArray items = message == null ? null : message.optJSONArray("items");
        JSONArray arr = new JSONArray();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject p = items.optJSONObject(i);
                if (p == null) continue;
                JSONObject x = new JSONObject();
                JSONArray tt = p.optJSONArray("title");
                x.put("title", tt != null && tt.length() > 0 ? tt.optString(0) : "");
                x.put("url", p.optString("URL", ""));
                x.put("abstract", p.optString("abstract", "").replaceAll("<[^>]+>", " "));
                x.put("published", p.optJSONObject("published") == null ? JSONObject.NULL : p.optJSONObject("published"));
                arr.put(x);
            }
        }
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("results", arr);
        out.put("provider", "crossref");
        return out.toString();
    }

    private String httpGetJson(String rawUrl, int maxChars) throws Exception {
        String current = rawUrl;
        int cap = Math.max(1_000, Math.min(100_000, maxChars));
        for (int redirect = 0; redirect <= 3; redirect++) {
            URL u = new URL(current);
            validateUrl(u);
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setConnectTimeout(8_000);
            con.setReadTimeout(12_000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "BANC888-FlyResearch/2.0");
            con.setRequestProperty("Accept", "application/json");
            int code = con.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = con.getHeaderField("Location");
                con.disconnect();
                if (location == null || location.isEmpty()) throw new IllegalStateException("redirect without Location");
                URL next = new URL(u, location);
                validateUrl(next);
                current = next.toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                con.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                while ((read = r.read(buf)) >= 0 && b.length() < cap) {
                    b.append(buf, 0, Math.min(read, cap - b.length()));
                }
            } finally {
                con.disconnect();
            }
            return b.toString();
        }
        throw new IllegalStateException("too many redirects");
    }

    private void validateUrl(URL u) {
        if (!"https".equalsIgnoreCase(u.getProtocol())) throw new SecurityException("HTTPS only");
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        boolean wiki = host.equals("wikipedia.org") || host.endsWith(".wikipedia.org");
        boolean crossref = host.equals("api.crossref.org");
        if (!wiki && !crossref) throw new SecurityException("research host blocked: " + host);
    }

    private String errorJson(String message) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false);
            out.put("error", message == null ? "research error" : message);
        } catch (Exception ignored) {}
        return out.toString();
    }

    String diagnosticsJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("executor", "single");
            o.put("providers", new JSONArray().put("wikipedia").put("crossref"));
            o.put("redirectPolicy", "manual-allowlist");
            o.put("httpsOnly", true);
        } catch (Exception ignored) {}
        return o.toString();
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
