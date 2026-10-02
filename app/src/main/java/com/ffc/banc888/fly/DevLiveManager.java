package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DevLiveManager {
    private static final String PREFS = "banc_dev";
    private static final String KEY_LIVE = "live";
    private static final String KEY_CURRENT = "current_bundle";
    private static final String KEY_PREVIOUS = "previous_bundle";

    private final Activity activity;
    private final SharedPreferences prefs;
    private final File root;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BANC888-DevLive");
        t.setDaemon(true);
        return t;
    });

    private final Set<String> verifiedBundles = new HashSet<>();
    private volatile boolean live;
    private volatile String currentBundle;
    private volatile String previousBundle;
    private volatile String lastError = "";

    DevLiveManager(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.root = new File(activity.getFilesDir(), "dev_live_public");
        if (!root.exists()) root.mkdirs();
        this.currentBundle = prefs.getString(KEY_CURRENT, "");
        this.previousBundle = prefs.getString(KEY_PREVIOUS, "");
        // APK upgrades always start from their bundled, mutually compatible UI.
        // Conversation data lives in WebView storage and remains untouched.
        boolean sameApk = prefs.getInt("installed_apk_version", -1) == BuildConfig.VERSION_CODE;
        this.live = sameApk && prefs.getBoolean(KEY_LIVE, false) && isReady(currentBundle);
        prefs.edit().putInt("installed_apk_version", BuildConfig.VERSION_CODE)
                .putBoolean(KEY_LIVE, live).apply();
    }

    File root() {
        return root;
    }

    boolean isLive() {
        return live && isReady(currentBundle);
    }

    File currentBundleDir() {
        if (!isLive()) return null;
        File d = new File(root, currentBundle);
        return d.isDirectory() ? d : null;
    }

    InputStream openRuntimeAsset(String name) throws Exception {
        File d = currentBundleDir();
        File f = d == null ? null : new File(d, name);
        if (f != null) return new FileInputStream(f);
        return activity.getAssets().open(name);
    }

    String currentPageUrl() {
        if (isLive()) {
            return AppConfig.APP_ORIGIN + "/live/" + currentBundle + "/index.html?t=" + System.currentTimeMillis();
        }
        return AppConfig.APP_ORIGIN + "/assets/index.html";
    }

    boolean canLive() {
        return (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    void syncAndReload(Runnable reload) {
        if (!canLive()) {
            lastError = "Live update is debug-only";
            activity.runOnUiThread(reload);
            return;
        }
        executor.execute(() -> {
            String candidate = "";
            try {
                JSONObject manifest = fetchManifest();
                validateManifest(manifest);
                String commit = manifest.getString("commit");
                JSONObject files = manifest.getJSONObject("files");
                candidate = "b_" + commit.substring(0, Math.min(12, commit.length())) + "_" + System.currentTimeMillis();
                File dir = new File(root, candidate);
                if (!dir.mkdirs()) throw new IllegalStateException("cannot create candidate bundle");

                for (String name : AppConfig.LIVE_ASSETS) {
                    String expected = files.getString(name).toLowerCase(Locale.ROOT);
                    File out = new File(dir, name);
                    long max = "index.html".equals(name) ? 2_500_000L : 800_000L;
                    download(AppConfig.rawCommitAssetUrl(commit, name), out, max);
                    String actual = sha256(out);
                    if (!expected.equals(actual)) {
                        throw new SecurityException("SHA-256 mismatch: " + name);
                    }
                }

                String index = readPrefix(new File(dir, "index.html"), 16_384);
                if (!index.contains("BANC888")) throw new IllegalStateException("index validation failed");

                try (FileOutputStream out = new FileOutputStream(new File(dir, "ready.marker"))) {
                    out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                }

                String old = currentBundle;
                previousBundle = isReady(old) ? old : previousBundle;
                currentBundle = candidate;
                live = true;
                lastError = "";
                prefs.edit()
                        .putBoolean(KEY_LIVE, true)
                        .putString(KEY_CURRENT, currentBundle)
                        .putString(KEY_PREVIOUS, previousBundle == null ? "" : previousBundle)
                        .apply();
                cleanupBundles();
                activity.runOnUiThread(reload);
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                if (!candidate.isEmpty()) deleteTree(new File(root, candidate));
                activity.runOnUiThread(reload);
            }
        });
    }

    boolean rollback() {
        if (!isReady(previousBundle)) {
            lastError = "no rollback bundle";
            return false;
        }
        String old = currentBundle;
        currentBundle = previousBundle;
        previousBundle = isReady(old) ? old : "";
        live = true;
        lastError = "";
        prefs.edit()
                .putBoolean(KEY_LIVE, true)
                .putString(KEY_CURRENT, currentBundle)
                .putString(KEY_PREVIOUS, previousBundle)
                .apply();
        return true;
    }

    void useBundled() {
        live = false;
        lastError = "";
        prefs.edit().putBoolean(KEY_LIVE, false).apply();
    }

    private JSONObject fetchManifest() throws Exception {
        File temp = new File(root, "manifest-" + System.currentTimeMillis() + ".json");
        try {
            download(AppConfig.rawBranchAssetUrl("live-manifest.json"), temp, 200_000L);
            return new JSONObject(readPrefix(temp, 200_000));
        } finally {
            temp.delete();
        }
    }

    private void validateManifest(JSONObject manifest) throws Exception {
        if (manifest.optInt("schema", -1) != 1) throw new SecurityException("unsupported live manifest schema");
        if (manifest.optInt("bridgeSchema", -1) != AppConfig.BRIDGE_SCHEMA) {
            throw new SecurityException("bridge schema mismatch");
        }
        if (manifest.optInt("assetSchema", -1) != AppConfig.ASSET_SCHEMA) {
            throw new SecurityException("asset schema mismatch");
        }
        if (manifest.optInt("minShellVersion", Integer.MAX_VALUE) > AppConfig.SHELL_VERSION) {
            throw new SecurityException("native shell upgrade required");
        }
        String commit = manifest.optString("commit", "");
        if (!commit.matches("[0-9a-fA-F]{40}")) throw new SecurityException("invalid commit pin");

        JSONObject files = manifest.optJSONObject("files");
        if (files == null) throw new SecurityException("manifest files missing");
        Set<String> actual = new HashSet<>();
        Iterator<String> it = files.keys();
        while (it.hasNext()) actual.add(it.next());
        if (!actual.equals(AppConfig.LIVE_ASSET_SET)) throw new SecurityException("manifest asset set mismatch");
        for (String name : AppConfig.LIVE_ASSETS) {
            String hash = files.optString(name, "");
            if (!hash.matches("[0-9a-fA-F]{64}")) throw new SecurityException("invalid SHA-256: " + name);
        }
    }

    private void download(String rawUrl, File out, long maxBytes) throws Exception {
        URL u = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(u.getProtocol()) || !"raw.githubusercontent.com".equalsIgnoreCase(u.getHost())) {
            throw new SecurityException("live source blocked");
        }
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(15_000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "BANC888-DevLive/2.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("HTTP " + code + " for " + u.getPath());
        }
        long total = 0;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) throw new IllegalStateException("live asset too large");
                os.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        if (total < 8) throw new IllegalStateException("empty live asset");
    }

    private String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return b.toString();
    }

    private String readPrefix(File file, int maxChars) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0 && b.length() < maxChars) {
                b.append(buf, 0, Math.min(n, maxChars - b.length()));
            }
        }
        return b.toString();
    }

    private synchronized boolean isReady(String bundle) {
        if (bundle == null || !bundle.matches("b_[0-9a-fA-F]{12}_[0-9]+")) return false;
        if (verifiedBundles.contains(bundle)) return true;
        File d = new File(root, bundle);
        try {
            JSONObject manifest = new JSONObject(readPrefix(new File(d, "ready.marker"), 200_000));
            validateManifest(manifest);
            if (!bundle.substring(2, 14).equalsIgnoreCase(manifest.getString("commit").substring(0, 12))) return false;
            JSONObject files = manifest.getJSONObject("files");
            for (String name : AppConfig.LIVE_ASSETS) {
                File asset = new File(d, name);
                if (!asset.isFile() || !files.getString(name).equalsIgnoreCase(sha256(asset))) return false;
            }
            verifiedBundles.add(bundle);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupBundles() {
        File[] dirs = root.listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            if (d.getName().equals(currentBundle) || d.getName().equals(previousBundle)) continue;
            deleteTree(d);
        }
    }

    private void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }

    String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("canLive", canLive());
            o.put("live", isLive());
            o.put("bundle", currentBundle == null ? "" : currentBundle);
            o.put("previousBundle", previousBundle == null ? "" : previousBundle);
            o.put("ref", AppConfig.DEV_REF);
            o.put("shellVersion", AppConfig.SHELL_VERSION);
            o.put("bridgeSchema", AppConfig.BRIDGE_SCHEMA);
            o.put("assetSchema", AppConfig.ASSET_SCHEMA);
            o.put("rollbackAvailable", isReady(previousBundle));
            o.put("lastError", lastError);
        } catch (Exception ignored) {}
        return o.toString();
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
