# Resonance

[English](README.md) · [繁體中文](README.zh-TW.md) · 简体中文

离线 Android 媒体播放器：导入本地文件、播放队列、内嵌章节、逐文件进度恢复与实时波形。
支持 Android 10 以上，提供简体中文、繁体中文、英文，以及浅色／深色／系统主题。
可选用局域网媒体库，从自己的电脑串流或复制文件，无需账号。

通过 Android 文件选择器导入媒体，副本保存在 App 私有存储中；已有本地文件可离线播放。
解码格式取决于设备。不附带媒体收藏或第三方封面。

## 安装

从 [GitHub Releases](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1)
下载正式签名的 `Resonance-v0.9.1.apk`；Android 询问时，允许浏览器或文件管理器安装。
发布页提供 SHA-256 校验码与公开签名证书指纹，验证方式见 [RELEASE.md](RELEASE.md)。
F-Droid 正在[审核](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)，
目前尚未在官方仓库上架。

## 构建

安装自由软件版本的 JDK 17、Android SDK platform 37 与 build tools 36.0.0，
设置 ANDROID_HOME 后运行：

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

这个命令生成未签名 APK。GitHub 正式 APK 使用保留的开发者发布密钥；
F-Droid 已改为申请重新构建比对后沿用相同签名，仍需通过审核与发布验证。开发版使用
`./gradlew :app:assembleDebug`，ID 会加上 `.catalog`。
公开版 ID 为 `io.github.codingezio.resonance`，与私有开发版本分开，
不会自动读取另一个 App 的私有数据。构建需下载 Google Maven／Maven Central 依赖。

## 可选的局域网媒体库

另装 Deno、ffprobe、shasum。将 `server/config.example.json` 复制为
`Local/shelf/config.json`，指定要共享的绝对路径：

```json
{"libraries":[{"name":"Music","path":"/path/to/music"}],"bind":"0.0.0.0"}
```

运行 `deno task serve`，在服务器打开 `http://localhost:12345`，使用页面显示的
局域网连接信息配对手机。只共享已配置的目录。HTTP 传输未加密，请限可信任的局域网。
媒体库默认没有文件来源，需自行启动；手机本地导入与离线播放不依赖它。
默认值见 `server/config.json`；RESONANCE_CONFIG 选择配置文件，RESONANCE_BIND／
RESONANCE_PORT 覆盖地址／端口，状态保存在 Local。

## 隐私与许可

无广告或分析 SDK。媒体、进度、队列、设置与愿望清单保留在本地。
只有自行启用的媒体库连接会传送配对信息、媒体元数据及所请求的文件。
Android 云端备份已停用；卸载会删除 App 私有数据。

程序与原创图标采用 GPL-3.0-or-later；字体与第三方组件保留各自许可。
详见 [LICENSE](LICENSE)、[DEPENDENCIES.md](DEPENDENCIES.md)、[PRIVACY.md](PRIVACY.md)。
目前自动质量检查与限制见 [QUALITY.md](QUALITY.md)。
