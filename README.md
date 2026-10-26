# Resonance

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

An offline Android media player with local file import, playback queues, chapters, per-file resume
positions and a live waveform. Android 10 or newer is required. English, Traditional Chinese and
Simplified Chinese are included, with light, dark and system themes. The optional LAN shelf streams
or copies your own files from a computer. No account is required.

Import files through Android's file picker. Resonance keeps imported copies in its private storage.
User-selected LAN connections are optional; existing local files can play offline. Codec support
depends on the device.

## Candidate and published release

This source candidate is 0.9.2 (version code 11), unpublished and unsigned. The 0.9.1 download and
its verification below remain historical release references. Current candidate checks are recorded
in QUALITY.md; they do not establish device or release acceptance.

## Install

Download the signed `Resonance-v0.9.1.apk` from
[GitHub Releases](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1). Allow installation
from your browser or file manager when Android asks. The release includes SHA-256 checksums and its
public signing certificate fingerprint. See [RELEASE.md](RELEASE.md) for verification and the
release procedure. The 0.9.1 release record listed the
[F-Droid submission](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310) as pending.
Current F-Droid availability was not checked for this candidate.

## Build

Install a free JDK 17 distribution, Android SDK platform 37 and build tools 36.0.0. Set ANDROID_HOME
to the SDK directory, then run:

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

This command produces an unsigned APK. Published GitHub APKs use the retained developer release key.
F-Droid is requested to verify a reproducible rebuild and use the same signature; this requires its
acceptance and publication checks. For local development, use `./gradlew :app:assembleDebug`; its
application ID has `.catalog` appended. Public release ID: `io.github.codingezio.resonance`. The
public application is separate from any privately distributed development app. It does not
automatically import another application's private data.

Gradle downloads build dependencies from Google Maven and Maven Central. No account, local signing
key, personal configuration or private asset is required.

## Optional LAN shelf

Install Deno, ffprobe and shasum. Copy `server/config.example.json` to `<private-data>/config.json`
and add explicitly selected absolute library paths:

```json
{ "libraries": [{ "name": "Music", "path": "/path/to/music" }], "bind": "0.0.0.0" }
```

Run `deno task serve`, open `http://localhost:12345` on the server, and use the shown LAN connection
details to pair the Android app. Only selected roots are served. Keep the shelf on a trusted local
network: its HTTP transport is not end-to-end encrypted. It is optional for Android file import and
offline playback. Defaults are in `server/config.json`; `RESONANCE_CONFIG` selects another overlay.
`RESONANCE_BIND` and `RESONANCE_PORT` override bind and port. State stays in the selected private
data directory. The shelf does not start until explicitly run, and initially serves no files.

## Private shelf data

`<private-data>` defaults to `~/Library/Application Support/ResonanceShelf` on macOS, XDG data
storage on Linux and Local AppData on Windows. Only the current Mac path/fixtures were verified in
this revision. `RESONANCE_DATA` chooses an absolute directory outside the checkout. The default
overlay is `<private-data>/config.json`; `RESONANCE_CONFIG` selects a different settings file
without moving that profile. `stateDirectory: "."` keeps the access key and catalog beside it;
relative state paths resolve within that profile. Traversal and physical links back into source are
rejected.

For a separate profile, set `RESONANCE_DATA=/path/to/private-shelf-data`, create that directory,
copy `server/config.example.json` there as `config.json`, and select your own libraries. Keep the
private folder out of Git and back it up separately. Changing the profile path does not migrate
data.

An older `Local/shelf` or `Local/portable-shelf` makes ordinary startup fail visibly. Stop your own
shelf, preserve all configuration, copy its `config.json`, `access-key` and `catalog.json` without
overwriting an existing profile, set `stateDirectory` to `"."`, and verify key/catalog hashes and
other settings. Keep the originals privately outside source before restarting. An explicit new
`RESONANCE_DATA` is a deliberate separate profile. Do not point candidate tests at personal data.

## Privacy and licensing

See [PRIVACY.md](PRIVACY.md), [DEPENDENCIES.md](DEPENDENCIES.md) and [LICENSE](LICENSE). The current
automated checks and their limits are in [QUALITY.md](QUALITY.md). Program source and original app
artwork: GPL-3.0-or-later. Fonts and third-party components retain their own licenses and notices.
No media collection or cover art is included. Supply files you are entitled to use.
