const fs=require('node:fs');

function read(p){return fs.readFileSync(p,'utf8')}
function must(cond,msg){if(!cond)throw new Error(msg)}

const config=read('app/src/main/java/com/ffc/banc888/fly/AppConfig.java');
const shell=read('app/src/main/java/com/ffc/banc888/fly/WebShellController.java');
const activity=read('app/src/main/java/com/ffc/banc888/fly/MainActivity.java');
const session=read('app/src/main/java/com/ffc/banc888/fly/NativeSession.java');
const registry=read('app/src/main/java/com/ffc/banc888/fly/BridgeRegistry.java');
const speech=read('app/src/main/java/com/ffc/banc888/fly/SpeechController.java');
const index=read('app/src/main/assets/index.html');
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
must(shell.includes('__BANC_NATIVE_EPOCH'),'native session epoch injection missing');
must(shell.includes('runtime.capabilities'),'native capability handshake missing');
must(shell.includes('__nativeDispatch'),'Java-to-WebView event dispatch missing');
must(shell.includes('stale-native-session'),'stale WebView session rejection missing');
must(session.includes('long epoch()'),'native session epoch accessor missing');
must(registry.includes('callUiBoolean'),'native speech bridge must synchronously return the Android main-thread result');
must(registry.includes('speech.startListening(language)'),'native speech start bridge missing');
must(registry.includes('speech.stopListening()'),'native speech stop bridge missing');
must(registry.includes('speech.speak(text, language, rate, pitch)'),'native TTS bridge missing');
must(registry.includes('probeMicrophone'),'raw microphone diagnostic bridge missing');
must(speech.includes('pendingSpeechText'),'TTS readiness queue missing');
must(speech.includes('ttsPending'),'TTS pending diagnostics missing');
must(speech.includes('findFallbackVoice'),'TTS fallback voice selection missing');
must(speech.includes('handleTtsFailure'),'TTS retry/error handler missing');
must(speech.includes('ttsLanguageStatus'),'TTS language diagnostics missing');
must(speech.includes('ttsDefaultVoice'),'TTS default-voice diagnostics missing');
must(speech.includes('tts.speak(')&&speech.includes('TextToSpeech.ERROR'),'TTS immediate enqueue failure detection missing');
must(speech.includes('AudioRecord'),'raw microphone capture probe missing');
must(speech.includes('readSamples'),'microphone PCM read diagnostics missing');
must(speech.includes('recognizerReadyAtMs'),'recognizer readiness diagnostics missing');
must(speech.includes('fallbackToSystemRecognizer'),'on-device to system recognizer fallback missing');
must(speech.includes('ready-timeout'),'recognizer readiness watchdog missing');
must(speech.includes('speech-activity-timeout'),'5-second speech activity watchdog missing');
must(speech.includes('5000L'),'speech activity watchdog must remain five seconds');
must(speech.includes('recognitionActivityAtMs'),'speech activity diagnostics missing');
must(index.includes('PCM取得OK'),'UI must distinguish real PCM capture from permission-only state');
must(index.includes('micProbe'),'UI microphone probe diagnostics missing');
must(index.includes('id="flyVoiceLoop" type="checkbox" checked'),'hands-free voice loop must default on');

must(activity.includes('WebViewCompat.saveState'),'bounded state save missing');
must(activity.includes('128 * 1024'),'saved state cap changed unexpectedly');

must(bridge.includes('window.AndroidRuntime'),'async native runtime API missing');
must(bridge.includes("post('runtime.status')"),'runtime status message missing');
must(bridge.includes("post('runtime.capabilities')"),'runtime capability request missing');
must(bridge.includes('__nativeDispatch'),'native push receiver missing');
must(bridge.includes('epoch'),'bridge session epoch missing');
must(bridge.includes('probeMicrophone'),'WebView microphone probe wrapper missing');
must(runtime.includes('window.FFC_WEBVIEW_RUNTIME'),'WebView health runtime missing');
must(runtime.includes('uiSettingsBody'),'WebView diagnostics must live inside settings drawer');
must(runtime.includes('WebView / Java連携'),'integrated Java/WebView panel missing');
must(runtime.includes("runtime.heartbeat"),'runtime heartbeat missing');
must(runtime.includes('unhandledrejection'),'promise error monitoring missing');
must(runtime.includes("PerformanceObserver"),'long-task monitoring missing');

must(manifest.bridgeSchema===4&&manifest.assetSchema===4&&manifest.minShellVersion===70,'live manifest schema drift');
must(Object.hasOwn(manifest.files,'webview-runtime.js'),'live manifest missing webview-runtime.js');

console.log('webview-runtime-contract: PASS');
