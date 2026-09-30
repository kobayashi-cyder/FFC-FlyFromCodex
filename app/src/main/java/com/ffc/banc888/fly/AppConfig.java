package com.ffc.banc888.fly;

import android.net.Uri;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class AppConfig {
    static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    static final String APP_HOST = "appassets.androidplatform.net";
    static final String DEV_REF = "banc888-apk-thread-router";
    static final String REPO = "kobayashi-cyder/FFC-FlyFromCodex";
    static final int SHELL_VERSION = 70;
    static final int WEB_RUNTIME_VERSION = 7;
    static final int BRIDGE_SCHEMA = 4;
    static final int ASSET_SCHEMA = 4;

    static final String[] RUNTIME_SCRIPTS = new String[]{
            "native-bridge.js",
            "webview-runtime.js",
            "thread-router-core.js",
            "capability-vocabulary-core.js",
            "research-physics-core.js",
            "ir-patch-core.js",
            "proxy-agent-core.js",
            "capability-tools.js",
            "research-physics-tools.js",
            "proxy-agent.js",
            "conversation-output-core.js",
            "thread-router.js",
            "dev-live.js"
    };

    static final String[] LIVE_ASSETS = new String[]{
            "index.html",
            "native-bridge.js",
            "webview-runtime.js",
            "thread-router-core.js",
            "capability-vocabulary-core.js",
            "research-physics-core.js",
            "ir-patch-core.js",
            "proxy-agent-core.js",
            "capability-tools.js",
            "research-physics-tools.js",
            "proxy-agent.js",
            "conversation-output-core.js",
            "thread-router.js",
            "dev-live.js"
    };

    static final Set<String> LIVE_ASSET_SET =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(LIVE_ASSETS)));

    private AppConfig() {}

    static boolean isTrustedOrigin(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && APP_HOST.equalsIgnoreCase(uri.getHost());
    }

    static boolean isTrustedInternalUri(Uri uri) {
        if (!isTrustedOrigin(uri)) return false;
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.startsWith("/assets/") || path.startsWith("/live/");
    }

    static String rawBranchAssetUrl(String name) {
        return "https://raw.githubusercontent.com/" + REPO + "/" + DEV_REF
                + "/app/src/main/assets/" + name;
    }

    static String rawCommitAssetUrl(String commit, String name) {
        return "https://raw.githubusercontent.com/" + REPO + "/" + commit
                + "/app/src/main/assets/" + name;
    }
}
