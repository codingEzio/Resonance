# Resonance

<p><img src="fastlane/metadata/android/en-US/images/icon.png" width="96" height="96" alt="Resonance"></p>

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

离线 Android 媒体播放器：导入本地文件、播放队列、内嵌章节、逐文件进度恢复与实时波形。 支持 Android
10 以上，提供简体中文、繁体中文、英文，以及浅色／深色／系统主题。
可选用局域网媒体库，从自己的电脑串流或复制文件，无需账号。

通过 Android 文件选择器导入媒体，副本保存在 App 私有存储中；已有本地文件可离线播放。
解码格式取决于设备。不附带媒体收藏或第三方封面。

## 源码与安装

此源码为 0.9.2（版本码 11）。尚未核实此版本有公开的已签名 APK；下方构建生成未签名 APK。
[QUALITY.md](QUALITY.md) 中的候选检查不代表真机或发布验收。
[F-Droid 申请](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)于
2026-11-11 关闭且未合并，近期暂停上架。后续按作者自己的需求开发，并通过 GitHub 发布。

已签名的 [0.9.1 版本](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)仍可下载；校验与签名信息见 [RELEASE.md](RELEASE.md)。

## 构建

安装自由软件版本的 JDK 17、Android SDK platform 37 与 build tools 36.0.0， 设置 ANDROID_HOME
后运行：

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

这个命令生成未签名 APK。未来签名发布需要核实开发者密钥；此前 F-Droid 的同签名
可复现构建申请目前已停止。开发版使用
`./gradlew :app:assembleDebug`，ID 会加上 `.catalog`。 公开版 ID 为
`io.github.codingezio.resonance`，与私有开发版本分开， 不会自动读取另一个 App 的私有数据。构建需下载
Google Maven／Maven Central 依赖。

本地构建可创建被忽略的 `Local/android-build.properties`，用 `-Presonance.localBuildConfig=Local/android-build.properties` 明确选择，并按需设置 `applicationId`。使用签名须同时设置 `storeFile`、`storePasswordEnv`、`keyAlias`、`keyPasswordEnv`；两个 Env 字段是存放密码的环境变量名称。缺文件、不完整、缺密码或未知字段会失败；未选择时使用公开 ID 和未签名设置。密钥和配置文件须保密。

私人 Mac、维护脚本和展示素材可通过被忽略的本地链接提供；本仓库不附带它们，使用前须确认其依赖。

## 可选的局域网媒体库

另装 Deno 2.7 或更新版、ffprobe、shasum。Shelf 锁定私人配置目录以防多个进程同时使用；恢复时保留现有访问密钥。将 `server/config.example.json` 复制为
`<private-data>/config.json`，指定要共享的绝对路径：

```json
{ "libraries": [{ "name": "Music", "path": "/path/to/music" }], "bind": "0.0.0.0" }
```

每个来源可选填 `"role": "singles"`、`"collections"` 或 `"legacy"`。singles 即使位于子文件夹也保留在主列表；collections 和 legacy 将子文件夹显示为播放列表，根目录文件仍在主列表。legacy 播放列表遵循现有 Legacy 设置。未指定角色及旧媒体库数据保持未分类，显示在主列表；文件夹名称不会决定角色。编辑私人设置并重启媒体库，再在 Android 中刷新。现有设置和文件不会自动迁移或覆盖。

运行 `deno task serve`，在服务器打开 `http://localhost:12345`，使用页面显示的
局域网连接信息配对手机。只共享已配置的目录。HTTP 传输未加密，请限可信任的局域网。
媒体库默认没有文件来源，需自行启动；手机本地导入与离线播放不依赖它。 默认值见
`server/config.json`；RESONANCE_CONFIG 选择配置文件，RESONANCE_BIND／ RESONANCE_PORT
覆盖地址／端口，状态保存在所选私人资料目录。

## 私人媒体库资料

`<private-data>` 在 macOS 默认是 `~/Library/Application Support/ResonanceShelf`，Linux 用 XDG
资料目录，Windows 用 Local AppData；本次只验证当前 Mac 路径与隔离情境。`RESONANCE_DATA`
指定代码目录外的绝对路径。默认设置是 `<private-data>/config.json`；`RESONANCE_CONFIG`
只换设置文件，不移动资料。`stateDirectory: "."`
将访问密钥与目录放在同一资料配置中；相对状态路径从这里解析。路径穿越及指回代码的实体链接被拒绝。

独立配置可设置 `RESONANCE_DATA=/path/to/private-shelf-data`，创建目录，把 server/config.example.json
复制为其中的 config.json，再选择自己的媒体根目录。私人资料不进 Git，单独备份；换路径不会自动搬资料。

旧 Local/shelf 或 Local/portable-shelf 仍在时，普通启动明确失败。停止自己的媒体库，完整保留设置，把
config.json、access-key、catalog.json 复制到私人目录，勿覆盖已有配置；将 stateDirectory
设为点号，核对密钥／目录哈希及其他设置，原档留在代码外私人恢复位置后再启动。明确设置新的
RESONANCE_DATA 表示另选配置。候选测试不使用个人资料。

## 隐私与许可

无广告或分析 SDK。媒体、进度、队列、设置与愿望清单保留在本地。
只有自行启用的媒体库连接会传送配对信息、媒体元数据及所请求的文件。 Android
云端备份已停用；卸载会删除 App 私有数据。

程序与原创图标采用 GPL-3.0-or-later；字体与第三方组件保留各自许可。 详见
[LICENSE](LICENSE)、[DEPENDENCIES.md](DEPENDENCIES.md)、[PRIVACY.md](PRIVACY.md)。
目前自动质量检查与限制见 [QUALITY.md](QUALITY.md)。
