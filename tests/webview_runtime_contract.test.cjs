const fs=require('node:fs');

function read(p){return fs.readFileSync(p,'utf8')}
function must(cond,msg){if(!cond)throw new Error(msg)}

const config=read('app/src/main/java/com/ffc/banc888/fly/AppConfig.java');
const shell=read('app/src/main/java/com/ffc/banc888/fly/WebShellController.java');
const activity=read('app/src/main/java/com/ffc/banc888/fly/MainActivity.java');
const bridge=read('app/src/main/assets/native-bridge.js');
const runtime=read('app/src/main/assets/webview-runtime.js');
const manifest=JSON.parse(read('app/src/main/assets/live-manifest.json'));

must(/SHELL_VERSION\s*=\s*70/.test(config),'shell version must remain 70');
must(/WEB_RUNTIME_VERSION\s*=\s*7/.test(config),'web runtime version must remain 7');
must(/BRIDGE_SCHEMA\s*=\s*4/.test(config),'bridge schema must remain 4');
must(/ASSET_SCHEMA\s*=\s*4/.test(config),'asset schema must remain 4');
must(config.includes('"webview-runtime.js"'),'runtime asset must be installed and live-updatable');

must(shell.includes('addWebMessageListener'),'origin-scoped WebMessage bridge missing');
must(shell.includes('Collections.singleton(AppConfig.APP_ORIGIN)'),'WebMessage origin rule missing');
must(shell.includes('blocked remote subresource'),'remote subresource policy missing');
must(shell.includes('MIXED_CONTENT_NEVER_ALLOW'),'mixed content policy missing');
must(shell.includes('setAllowFileAccess(false)'),'file access must remain disabled');
must(shell.includes('setAllowContentAccess(false)'),'content access must remain disabled');
must(shell.includes('onRenderProcessGone'),'renderer loss recovery missing');
must(shell.includes('WebRecoveryGuard.tryAcquire'),'bounded recovery guard missing');
must(shell.includes('WebViewRenderProcessClient'),'renderer responsiveness monitor missing');
must(shell.includes('NavigationListener'),'native navigation metrics missing');
must(shell.includes('WebResourceErrorCompat'),'compat error callback missing');

must(activity.includes('WebViewCompat.saveState'),'bounded state save missing');
must(activity.includes('128 * 1024'),'saved state cap changed unexpectedly');

must(bridge.includes('window.AndroidRuntime'),'async native runtime API missing');
must(bridge.includes("post('runtime.status')"),'runtime status message missing');
must(runtime.includes('window.FFC_WEBVIEW_RUNTIME'),'WebView health runtime missing');
must(runtime.includes("runtime.heartbeat"),'runtime heartbeat missing');
must(runtime.includes('unhandledrejection'),'promise error monitoring missing');
must(runtime.includes("PerformanceObserver"),'long-task monitoring missing');

must(manifest.bridgeSchema===4&&manifest.assetSchema===4&&manifest.minShellVersion===70,'live manifest schema drift');
must(Object.hasOwn(manifest.files,'webview-runtime.js'),'live manifest missing webview-runtime.js');

console.log('webview-runtime-contract: PASS');
