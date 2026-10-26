# Resonance

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

ローカルファイル、再生キュー、チャプター、ファイル別再開位置、ライブ波形を備えるオフライン Android
メディアプレーヤーです。Android10+、英語・繁体・簡体 UI と明暗／system
に対応し、アカウントは不要です。

## 取り込みとリリース

Android ファイル選択で取り込み、App
私有ストレージへコピーします。既存ローカルファイルはオフライン再生でき、codec
対応は端末次第です。私有メディア集や第三者カバーは同梱しません。任意の LAN shelf
だけが自分のコンピューターから stream／copy を行います。

公開済みは [v0.9.1](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1) の署名
APK、SHA256SUMS、公開署名指紋です。ブラウザー／file manager
からのインストールを必要時だけ許可します。[RELEASE.md](RELEASE.md)で照合してください。現在のソース候補0.9.2／code11は未公開・未署名で、候補検証は実機やリリースの受入ではありません。F-Droid
は0.9.1記録で申請中、今回の現在提供状況は未確認です。

## ビルド

自由な JDK17、Android SDK37、build tools36.0.0を用意し、ANDROID_HOME
を設定します。`./gradlew -Presonance.profile=portable :app:assembleRelease` は未署名 APK
を作ります。Gradle は Google Maven／Maven Central
から依存を取得し、アカウント・私有設定・署名鍵・メディアは不要です。

GitHub リリースは保持した開発者 release key、F-Droid
の同一署名による再ビルド案は審査・再現性確認が別です。`./gradlew :app:assembleDebug` は .catalog
を追加します。公開 ID は io.github.codingezio.resonance で、私有開発アプリと別です。他 App
の私有データを自動取り込みしません。

## 任意の LAN shelf

Deno、ffprobe、shasum を別途導入し、server/config.example.json を <private-data>/config.json
へコピーして明示的に選ぶライブラリ絶対パスを設定します。`deno task serve` で起動し、server の
localhost:12345 に表示した接続情報で Android
とペアリングします。既定ではソースが空で、自動起動しません。選択した root だけを提供し、HTTP
は暗号化されないため信頼できる LAN に限定します。Android 単独のファイル取り込みやオフライン再生に
shelf は不要です。

server/config.json が既定、RESONANCE_CONFIG が overlay、RESONANCE_BIND／RESONANCE_PORT が bind／port
を上書きし、state は選択した私有データディレクトリに保存します。私有 Mac
版とそのライブラリは公開ソース範囲外です。

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
