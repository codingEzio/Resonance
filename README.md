# Resonance

English · [繁體中文](README.zh-TW.md) · [简体中文](README.zh-CN.md)

An offline Android media player with local file import, playback queues, chapters,
per-file resume positions and a live waveform. Android 10 or newer is required.
English, Traditional Chinese and Simplified Chinese are included, with light,
dark and system themes. The optional LAN shelf streams or copies your own files
from a computer. No account is required.

Import files through Android's file picker. Resonance keeps imported copies in
its private storage. User-selected LAN connections are optional; existing local
files can play offline. Codec support depends on the device.

## Install

Download the signed `Resonance-v0.9.1.apk` from [GitHub Releases](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1).
Allow installation from your browser or file manager when Android asks.
The release includes SHA-256 checksums and its public signing certificate fingerprint.
See [RELEASE.md](RELEASE.md) for verification and the release procedure.
F-Droid inclusion is [under review](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310);
it is not yet available from the official F-Droid repository.

## Build

Install a free JDK 17 distribution, Android SDK platform 37 and build tools 36.0.0.
Set ANDROID_HOME to the SDK directory, then run:

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

This command produces an unsigned APK. Published GitHub APKs use the retained
developer release key. F-Droid is requested to verify a reproducible rebuild and
use the same signature; this requires its acceptance and publication checks.
For local development, use `./gradlew :app:assembleDebug`; its application ID has
`.catalog` appended. Public release ID: `io.github.codingezio.resonance`.
The public application is separate from any privately distributed development app.
It does not automatically import another application's private data.

Gradle downloads build dependencies from Google Maven and Maven Central. No
account, local signing key, personal configuration or private asset is required.

## Optional LAN shelf

Install Deno, ffprobe and shasum. Copy `server/config.example.json` to
`Local/shelf/config.json` and add explicitly selected absolute library paths:

```json
{"libraries":[{"name":"Music","path":"/path/to/music"}],"bind":"0.0.0.0"}
```

Run `deno task serve`, open `http://localhost:12345` on the server, and use the
shown LAN connection details to pair the Android app. Only selected roots are
served. Keep the shelf on a trusted local network: its HTTP transport is not
end-to-end encrypted. It is optional for Android file import and offline playback.
Defaults are in `server/config.json`; `RESONANCE_CONFIG` selects another overlay.
`RESONANCE_BIND` and `RESONANCE_PORT` override bind and port. State stays in `Local/`.
The shelf does not start until explicitly run, and initially serves no files.

## Privacy and licensing

See [PRIVACY.md](PRIVACY.md), [DEPENDENCIES.md](DEPENDENCIES.md) and [LICENSE](LICENSE).
The current automated checks and their limits are in [QUALITY.md](QUALITY.md).
Program source and original app artwork: GPL-3.0-or-later. Fonts and third-party
components retain their own licenses and notices. No media collection or cover
art is included. Supply files you are entitled to use.
