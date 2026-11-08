# Resonance

<p><img src="fastlane/metadata/android/en-US/images/icon.png" width="96" height="96" alt="Resonance"></p>

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

離線 Android 媒體播放器：匯入本機檔案、播放佇列、內嵌章節、逐檔進度還原與即時波形。 支援 Android 10
以上，提供繁體中文、簡體中文、英文，以及淺色／深色／系統主題。
可選用區域網路媒體架，從自己的電腦串流或複製檔案，不需帳號。

透過 Android 檔案挑選器匯入媒體，副本儲存在 App 私人空間；已有本機檔案可離線播放。
解碼格式依裝置而定。不隨附媒體收藏或第三方封面。

## 原始碼與安裝

此原始碼為 0.9.2（版本碼 11）。尚未確認此版本有公開的已簽章 APK；下方建置產生未簽章 APK。
[QUALITY.md](QUALITY.md) 的候選檢查不代表實機或發布驗收。
[F-Droid 申請](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)截至
2026-11-07 仍開啟，實際發布情況尚未確認。

已簽章的 [0.9.1 版本](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)仍可下載；校驗與簽章資訊見 [RELEASE.md](RELEASE.md)。

## 建置

安裝自由軟體版本的 JDK 17、Android SDK platform 37 與 build tools 36.0.0， 設定 ANDROID_HOME
後執行：

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

這個命令產生未簽章 APK。未來簽章發布需要確認開發者金鑰；F-Droid
已改為申請重新建置比對後沿用相同簽章，仍需通過審核與發布驗證。開發版使用
`./gradlew :app:assembleDebug`，ID 會加上 `.catalog`。 公開版 ID 為
`io.github.codingezio.resonance`，與私人開發版本分開， 不會自動讀取另一個 App 的私人資料。建置需下載
Google Maven／Maven Central 依賴。

本機建置可建立被忽略的 `Local/android-build.properties`，以 `-Presonance.localBuildConfig=Local/android-build.properties` 明確選取，並視需要設定 `applicationId`。選用簽章時須同時設定 `storeFile`、`storePasswordEnv`、`keyAlias`、`keyPasswordEnv`；兩個 Env 欄位是存放密碼的環境變數名稱。缺檔、不完整、缺密碼或未知欄位會失敗；未選取時使用公開 ID 與未簽章設定。金鑰及設定檔須保密。

私人 Mac、維護腳本與展示素材可透過被忽略的本機連結提供；本儲存庫不附帶它們。使用前須確認其依賴。

## 可選的區域網路媒體架

另裝 Deno 2.7 或更新版、ffprobe、shasum。Shelf 鎖定私人設定檔以防多個程序同時使用；復原時保留既有存取金鑰。將 `server/config.example.json` 複製為
`<private-data>/config.json`，指定要分享的絕對路徑：

```json
{ "libraries": [{ "name": "Music", "path": "/path/to/music" }], "bind": "0.0.0.0" }
```

每個來源可選填 `"role": "singles"`、`"collections"` 或 `"legacy"`。singles 即使在子資料夾也留在主清單；collections 與 legacy 將子資料夾顯示為播放清單，根目錄檔案仍在主清單。legacy 播放清單遵循既有的 Legacy 設定。未指定角色及舊媒體架資料保持未分類，顯示在主清單；資料夾名稱不決定角色。編輯私人設定並重新啟動媒體架，再於 Android 重新整理。既有設定和檔案不會自動搬移或覆寫。

執行 `deno task serve`，在伺服器開啟 `http://localhost:12345`，使用頁面顯示的
區域網路連線資料配對手機。只分享設定的目錄。HTTP 傳輸未加密，請限可信任的區域網路。
媒體架預設沒有檔案來源，需自行啟動；手機本機匯入與離線播放不依賴它。 預設值見
`server/config.json`；RESONANCE_CONFIG 選擇設定檔，RESONANCE_BIND／ RESONANCE_PORT
覆寫位址／連接埠，狀態儲存在所選私人資料目錄。

## 私人媒體庫資料

`<private-data>` 在 macOS 預設是 `~/Library/Application Support/ResonanceShelf`，Linux 用 XDG
資料目錄，Windows 用 Local AppData；本次只驗證目前 Mac 路徑與隔離情境。`RESONANCE_DATA`
指定程式碼目錄外的絕對路徑。預設設定是 `<private-data>/config.json`；`RESONANCE_CONFIG`
只換設定檔，不移動資料。`stateDirectory: "."`
將存取金鑰與目錄放在同一資料設定中；相對狀態路徑從這裡解析。路徑穿越與指回程式碼的實體連結皆拒絕。

獨立設定可設 `RESONANCE_DATA=/path/to/private-shelf-data`，建立目錄，把 server/config.example.json
複製為其中的 config.json，再選擇自己的媒體根目錄。私人資料不進 Git，單獨備份；換路徑不會自動搬資料。

舊 Local/shelf 或 Local/portable-shelf 仍在時，一般啟動明確失敗。停止自己的媒體庫，完整保留設定，把
config.json、access-key、catalog.json 複製到私人目錄，勿覆寫已有設定；將 stateDirectory
設為點號，核對金鑰／目錄雜湊與其他設定，原檔留在程式碼外私人恢復位置後再啟動。明確設定新的
RESONANCE_DATA 表示另選設定。候選測試不使用個人資料。

## 隱私與授權

無廣告或分析 SDK。媒體、進度、佇列、設定與願望清單保留在本機。
只有自行啟用的媒體架連線會傳送配對資訊、媒體中繼資料及所要求的檔案。 Android
雲端備份已停用；解除安裝會刪除 App 私人資料。

程式與原創圖示採 GPL-3.0-or-later；字型與第三方元件保留各自授權。 詳見
[LICENSE](LICENSE)、[DEPENDENCIES.md](DEPENDENCIES.md)、[PRIVACY.md](PRIVACY.md)。
目前自動品質檢查與限制見 [QUALITY.md](QUALITY.md)。
