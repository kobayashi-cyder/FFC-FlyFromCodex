# BANC888 Agent APK

Branch: `feature/banc888-agent-apk`

This Android wrapper embeds BANC888 v1.9 as an offline WebView asset and adds:

- Offline CDL -> structural IR -> single HTML generation.
- Android save bridge: generated HTML/JSON is stored under `Downloads/BANC888/` on Android 10+.
- Explicit-command accessibility agent.
- UI snapshot capped at 300 nodes; password-node text is never exported.
- Tap, swipe, click-by-text, Back, Home and Recents primitives.
- No INTERNET permission and WebView HTTP/HTTPS requests are blocked.
- Event-driven autonomous actions are intentionally disabled; external-app actions happen only from explicit local commands.

## Agent commands

- `画面取得`
- `「OK」を押す`
- `タップ 500 800`
- `スワイプ 500 1500 500 400 450`
- `戻る`
- `ホーム`
- `履歴`

Enable the accessibility service from the **操作権限** button before external-app operations.

## Build

GitHub Actions builds `app-debug.apk` and uploads it as the `BANC888-Agent-debug` artifact.
