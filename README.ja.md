# Resonance

<p><img src="fastlane/metadata/android/en-US/images/icon.png" width="96" height="96" alt="Resonance"></p>

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

ローカルファイル、再生キュー、チャプター、ファイル別再開位置、ライブ波形を備えるオフライン Android
メディアプレーヤーです。Android10+、英語・繁体・簡体 UI と明暗／system
に対応し、アカウントは不要です。

## 取り込みとインストール

Android のファイル選択でアプリの私有領域にコピーし、ローカルファイルをオフライン再生できます。
対応 codec は端末により異なり、個人メディアや第三者のカバーは含みません。
LAN shelf は任意です。このソースは 0.9.2／code11 で、公開署名済み APK は未確認です。
以下のビルドは未署名 APK を生成します。[QUALITY.md](QUALITY.md) の検証は実機受入ではありません。
[F-Droid 申請](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)は
2026-11-11 にマージされず終了し、当面は申請を休止します。今後は作者自身の用途に応じて開発し、GitHub で公開します。

署名済みの [0.9.1 リリース](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)は引き続き利用できます。検証方法は [RELEASE.md](RELEASE.md) を参照してください。

## ビルド

自由な JDK17、Android SDK37、build tools36.0.0を用意し、ANDROID_HOME
を設定します。`./gradlew -Presonance.profile=portable :app:assembleRelease` は未署名 APK
を作ります。Gradle は Google Maven／Maven Central
から依存を取得し、アカウント・私有設定・署名鍵・メディアは不要です。

将来の署名リリースには開発者鍵の確認が必要です。以前の F-Droid 向け同一署名再ビルド申請は現在停止中です。
`./gradlew :app:assembleDebug` は .catalog
を追加します。公開 ID は io.github.codingezio.resonance で、私有開発アプリと別です。他 App
の私有データを自動取り込みしません。

## 任意の LAN shelf

Deno 2.7 以降、ffprobe、shasum を別途導入します。Shelf は複数プロセスの同時使用を防ぐためプロファイルをロックします。復旧時は既存のアクセスキーを保持してください。

server/config.example.json を <private-data>/config.json
へコピーして明示的に選ぶライブラリ絶対パスを設定します。`deno task serve` で起動し、server の
localhost:12345 に表示した接続情報で Android
とペアリングします。既定ではソースが空で、自動起動しません。選択した root だけを提供し、HTTP
は暗号化されないため信頼できる LAN に限定します。Android 単独のファイル取り込みやオフライン再生に
shelf は不要です。

server/config.json が既定、RESONANCE_CONFIG が overlay、RESONANCE_BIND／RESONANCE_PORT が bind／port
を上書きし、state は選択した私有データディレクトリに保存します。私有 Mac
版とそのライブラリは公開ソース範囲外です。

各ライブラリには `"role": "singles"`、`"collections"`、`"legacy"` を任意で指定できます。singles はサブフォルダー内でもメイン一覧に表示され、collections と legacy はサブフォルダーをプレイリストとして表示します。ルート直下のファイルはメイン一覧に残ります。legacy は既存の Legacy 設定に従います。未指定の役割や旧データは未分類のままメイン一覧に表示され、フォルダー名から役割を推測しません。プライベート設定を編集してシェルフを再起動し、Android で更新してください。既存の設定やファイルは移動・上書きされません。

## 私有データプロファイル

`<private-data>` の既定は macOS の `~/Library/Application Support/ResonanceShelf`、Linux の
XDG、Windows の Local AppData です。この改訂は現在の Mac と隔離 fixture
のみ確認しました。RESONANCE_DATA は checkout 外の絶対ディレクトリ、既定設定はその
config.json。RESONANCE_CONFIG は設定だけを変更します。stateDirectory
の点号は同じプロファイル、相対値もその内部基準です。source への実体リンクや親移動を拒否します。

別プロファイルは `RESONANCE_DATA=/path/to/private-shelf-data` を設定し、作成したディレクトリへ
server/config.example.json を config.json としてコピーして自分の root を選びます。Git
へ含めず別途バックアップします。パス変更はデータ移行ではありません。

旧 Local/shelf／Local/portable-shelf があれば通常起動は失敗します。自分の shelf
を停止し、設定を保持、config.json／access-key／catalog.json
を既存データへ上書きせずコピー、stateDirectory を点号にして鍵／カタログ hash
と他設定を確認します。元資料を source 外に私有保存してから再開します。明示的な新 RESONANCE_DATA
は別プロファイル選択です。候補テストには個人データを使いません。

## プライバシーと権利

媒体、再生位置、キュー、設定、wishlist はローカルです。広告／analytics SDK はなく、任意に接続した
shelf だけが pairing、媒体 metadata と要求したファイルを転送します。Android cloud backup
は無効、アンインストールは App 私有データを削除します。

コードとオリジナル App artwork は
[GPL-3.0-or-later](LICENSE)、字体と部品は元条件を保持します。[PRIVACY.md](PRIVACY.md)、[DEPENDENCIES.md](DEPENDENCIES.md)、[QUALITY.md](QUALITY.md)を参照してください。利用権のある媒体を用意します。既存スクリーンショット／0.9.1検証は過去の版で、0.9.2の端末、性能、署名、F-Droid
検証ではありません。ここでインストール、shelf起動、端末や媒体アクセスは行いません。
