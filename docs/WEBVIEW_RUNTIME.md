# WebView Runtime v7

BANC888 now treats Android WebView as a recoverable application runtime rather than a passive HTML container.

## Native shell

`WebShellController` now provides:

- HTTPS-only local content via `WebViewAssetLoader`
- file/content access disabled
- mixed content disabled
- popup windows and automatic JavaScript windows disabled
- geolocation and form persistence disabled
- remote HTTP(S) subresources blocked inside the WebView
- external main-frame links delegated to the system browser/handler
- WebView console messages mirrored to Logcat
- page-load progress and runtime health telemetry
- renderer-process crash / kill recovery
- main-frame load-error recovery
- bounded recovery using `WebRecoveryGuard`
- WebView state restoration across Activity recreation

AndroidX WebKit is pinned to 1.16.0.

## Bridge model

The existing synchronous bridges remain available for the current voice/files/research/dev APIs, but they remain protected by:

1. trusted main-frame origin
2. per-page rotated native session token

New runtime coordination uses `WebViewCompat.addWebMessageListener` and the JavaScript object `BancNative`.

The allowed origin rule is only:

```
https://appassets.androidplatform.net
```

`native-bridge.js` exposes an asynchronous wrapper:

```js
AndroidRuntime.status()
AndroidRuntime.ping()
AndroidRuntime.reload()
AndroidRuntime.rollback()
AndroidRuntime.useBundled()
AndroidRuntime.post(type, data)
```

## Web runtime health

`webview-runtime.js` records:

- ready state
- native heartbeat
- JavaScript errors
- unhandled promise rejections
- long-task count
- online/offline state
- page visibility
- DOM/load/paint timing
- bridge availability

The small `WV` badge in the header opens diagnostics and recovery controls.

## Recovery policy

A renderer loss or main-frame load failure requests an Activity-level rebuild.

Automatic recovery is capped at 3 attempts within 30 seconds. A successful `runtime.ready` message resets the budget. This prevents reload loops while still recovering from transient WebView failures.

## Live bundles

Live assets use schema 4 and include `webview-runtime.js`.

The branch manifest pins a concrete commit and SHA-256 for every asset before `DevLiveManager` will activate it. The previous live bundle remains available for rollback.
