# Resonance

[English](README.md) · 繁體中文 · [简体中文](README.zh-CN.md)

離線 Android 媒體播放器：匯入本機檔案、播放佇列、內嵌章節、逐檔進度還原與即時波形。
支援 Android 10 以上，提供繁體中文、簡體中文、英文，以及淺色／深色／系統主題。
可選用區域網路媒體架，從自己的電腦串流或複製檔案，不需帳號。

透過 Android 檔案挑選器匯入媒體，副本儲存在 App 私人空間；已有本機檔案可離線播放。
解碼格式依裝置而定。不隨附媒體收藏或第三方封面。

## 安裝

從 [GitHub Releases](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)
下載正式簽署的 `Resonance-v0.9.1.apk`；Android 詢問時，允許瀏覽器或檔案管理員安裝。
發布頁提供 SHA-256 校驗碼與公開簽章憑證指紋，驗證方式見 [RELEASE.md](RELEASE.md)。
F-Droid 正在[送審](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)，
目前尚未在官方儲存庫上架。

## 建置

安裝自由軟體版本的 JDK 17、Android SDK platform 37 與 build tools 36.0.0，
設定 ANDROID_HOME 後執行：

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

這個命令產生未簽章 APK。GitHub 正式 APK 使用保留的開發者發布金鑰；
F-Droid 已改為申請重新建置比對後沿用相同簽章，仍需通過審核與發布驗證。開發版使用
`./gradlew :app:assembleDebug`，ID 會加上 `.catalog`。
公開版 ID 為 `io.github.codingezio.resonance`，與私人開發版本分開，
不會自動讀取另一個 App 的私人資料。建置需下載 Google Maven／Maven Central 依賴。

## 可選的區域網路媒體架

另裝 Deno、ffprobe、shasum。將 `server/config.example.json` 複製為
`Local/shelf/config.json`，指定要分享的絕對路徑：

```json
{"libraries":[{"name":"Music","path":"/path/to/music"}],"bind":"0.0.0.0"}
```

執行 `deno task serve`，在伺服器開啟 `http://localhost:12345`，使用頁面顯示的
區域網路連線資料配對手機。只分享設定的目錄。HTTP 傳輸未加密，請限可信任的區域網路。
媒體架預設沒有檔案來源，需自行啟動；手機本機匯入與離線播放不依賴它。
預設值見 `server/config.json`；RESONANCE_CONFIG 選擇設定檔，RESONANCE_BIND／
RESONANCE_PORT 覆寫位址／連接埠，狀態儲存在 Local。

## 隱私與授權

無廣告或分析 SDK。媒體、進度、佇列、設定與願望清單保留在本機。
只有自行啟用的媒體架連線會傳送配對資訊、媒體中繼資料及所要求的檔案。
Android 雲端備份已停用；解除安裝會刪除 App 私人資料。

程式與原創圖示採 GPL-3.0-or-later；字型與第三方元件保留各自授權。
詳見 [LICENSE](LICENSE)、[DEPENDENCIES.md](DEPENDENCIES.md)、[PRIVACY.md](PRIVACY.md)。
目前自動品質檢查與限制見 [QUALITY.md](QUALITY.md)。
