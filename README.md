# Resonance

<p><img src="fastlane/metadata/android/en-US/images/icon.png" width="96" height="96" alt="Resonance"></p>

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) ·
[日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

An offline Android media player with local file import, playback queues, chapters, per-file resume
positions and a live waveform. Android 10 or newer is required. English, Traditional Chinese and
Simplified Chinese are included, with light, dark and system themes. The optional LAN shelf streams
or copies your own files from a computer. No account is required.

Import files through Android's file picker. Resonance keeps imported copies in its private storage.
User-selected LAN connections are optional; existing local files can play offline. Codec support
depends on the device.

For a configured personal shelf, run `scripts/usb-sync` on the Mac with one authorized Android
phone connected. Confirm the connection on the phone and keep the command running until downloads
finish. The script forwards only a loopback port over USB and removes its own forwarding on exit.

## Source and installation

This source is 0.9.2 (version code 11). No public signed APK has been verified
for this version; the build below produces an unsigned APK. Candidate checks in
[QUALITY.md](QUALITY.md) do not establish device or release acceptance. The
[F-Droid submission](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310)
closed without merging on 2026-11-11. F-Droid distribution is paused; future
development is driven by the owner's needs and published through GitHub.

The signed [0.9.1 release](https://github.com/codingEzio/Resonance/releases/tag/v0.9.1) remains available with checksums and signing information; see [RELEASE.md](RELEASE.md).

## Build

Install a free JDK 17 distribution, Android SDK platform 37 and build tools 36.0.0. Set ANDROID_HOME
to the SDK directory, then run:

```sh
./gradlew -Presonance.profile=portable :app:assembleRelease
```

This command produces an unsigned APK. A future signed release requires a verified developer key.
The earlier F-Droid reproducible-build proposal is no longer active. For local development,
use `./gradlew :app:assembleDebug`; its
application ID has `.catalog` appended. Public release ID: `io.github.codingezio.resonance`. The
public application is separate from any privately distributed development app. It does not
automatically import another application's private data.

Gradle downloads build dependencies from Google Maven and Maven Central. No account, local signing
key, personal configuration or private asset is required.

For a local build override, create an ignored `Local/android-build.properties` and select it with `-Presonance.localBuildConfig=Local/android-build.properties`. Set `applicationId` if needed. Optional signing requires all four properties: `storeFile`, `storePasswordEnv`, `keyAlias`, and `keyPasswordEnv`; the last two environment-name properties name variables containing the passwords. A selected missing file, incomplete signing, missing password or unknown property fails the build. Without an override, the public ID and unsigned release remain the defaults. Keep keys and this file private.

The optional personal `bundled` profile also requires separately licensed font inputs under
`app/src/main/assets/private-fonts/`: `timeless.ttf`, `emoji3d.ttf`, and their accompanying
`timeless-license.pdf` and `emoji3d-license.txt`. A `retired-shelf-groups.txt` file lists any
explicitly retired shelf group names, one per line; it may be empty. On first launch each named
group is forgotten from the remote catalog while local copies and other groups are preserved.
These inputs are ignored by Git; acquire and
verify them separately before building. They are embedded in the personal APK, which must be
distributed only as permitted by the included licenses. The portable profile does not require them.

Optional local extensions may be linked at `Mac/`, the ignored `scripts/mac*` and maintenance-script paths, and ignored presentation asset paths. They are not supplied by this checkout; confirm their own dependencies before use.

## Optional LAN shelf

Install Deno 2.7 or newer, ffprobe and shasum. The shelf locks its private profile to prevent simultaneous owners; preserve the existing access key if recovery is needed. Copy `server/config.example.json` to `<private-data>/config.json`
and add explicitly selected absolute library paths:

```json
{ "libraries": [{ "name": "Music", "path": "/path/to/music" }], "bind": "0.0.0.0" }
```

Each library can optionally set `"role": "singles"`, `"collections"`, or `"legacy"`. Singles stay in the main list even in subfolders; collection and legacy roots group non-root folders as playlists. A root file stays in the main list. Legacy playlists follow the existing Legacy setting. Omitted roles and older shelf memberships remain unclassified in the main list; folder names never assign a role. Edit your private config and restart the shelf, then refresh the Android library. Existing settings and files are not migrated or overwritten.

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
