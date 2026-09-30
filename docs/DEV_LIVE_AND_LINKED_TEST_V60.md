# BANC888 v6.0 Dev Live + Linked Emulator

Goal: stop reinstalling the APK for every HTML/JS change.

Dev Live:
- install this debug shell once
- tap Live更新 to pull the fixed public integration branch from raw.githubusercontent.com
- assets are written to a dedicated internal-storage bundle
- WebViewAssetLoader.InternalStoragePathHandler serves the selected bundle at the appassets origin
- the old bundle is not selected until every required asset is downloaded and index.html passes a marker check
- APK内蔵 returns immediately to bundled assets
- native Java/manifest/dependency changes still require an APK update

Real linked CI:
- launches MainActivity in an Android emulator
- waits for WebView runtime
- checks FFC_THREADS, FFC_PROXY_AGENT, FFC_CAPABILITIES and conversation output
- checks AndroidVoice, AndroidFiles, AndroidResearch and AndroidDev JS bridges from inside WebView
- verifies same-thread speech output omits routing metadata.