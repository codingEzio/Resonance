# Quality checks and limits

## Shelf data ownership and documentation checks — 2026-10-26

The local Android 0.9.2/code11 candidate remains unpublished and unsigned.
Its reviewed source selection now contains 195 files. The six product README
files cover English, Simplified and Traditional Chinese, Japanese, Korean and
Spanish. Android source, Gradle inputs and bundled Android assets are unchanged
from the checked October 21 source. The unsigned APK and 19 JVM fixture results
below are retained evidence for those identical inputs, not a fresh build or a
new device check.

The optional Deno shelf now owns configuration, pairing key and catalog in an
OS-private data profile outside the checkout. Five existing/high-level synthetic
shelf tests, four export-boundary fixtures, typecheck and lint passed. Configuration
checks cover persistence and reopen, explicit profiles, both legacy local profile
names, relative/traversal paths and physical symlinks back into source.

An independent copy of the selected source passed two loopback-only HTTP startup
cycles with an empty synthetic library: health and authenticated catalog reads,
an explicit refresh, catalog persistence and restart readback. Settings and the
pairing key retained their bytes, no runtime file appeared in the source copy,
and the owned listener stopped after the fixture. This checks the Deno shelf
boundary; it does not establish Android pairing, media streaming, LAN reachability
from another device, native UI, or physical-device performance. No owner media or
production service was used. Current public repository/release metadata was read
without changing the remote; F-Droid review status was not refreshed.

## Local 0.9.2 candidate checks — 2026-10-21

This source candidate is unpublished and unsigned (version code 11). A fresh
history-free export selected 191 reviewed Git-index files and verified every
output hash and mode. Four synthetic export-boundary suites and four existing
Deno shelf core tests passed. All eight font files matched FONT_ASSETS.json.

Using JDK 17, the declared Gradle wrapper and existing Android SDK, an isolated
build with fresh Gradle and JVM-home state passed `:app:assembleRelease` (52 tasks).
The APK reads as `io.github.codingezio.resonance`, 0.9.2/code11, portable profile.
It has no APK signing block or JAR signature. Packaged assets contain 39 retained
license/provenance files and two generated dexopt profiles; every license byte
matches the reviewed export. No presentation catalog or private cover is included.

The current task inventory has no `testReleaseUnitTest`. The existing
`:app:testDebugUnitTest` ran 19 pure JVM fixture tests across seven suites,
with no failures. Its dry-run graph contained no APK signing, APK packaging,
installation or device tasks. These are debug-variant JVM checks, separate from
the unsigned release build. Gradle reports existing deprecations and retains one
native dependency library without stripping its debug symbols.

No app launch, installation, device/UI/performance test, shelf service or real
media access occurred. Existing public screenshots are historical release assets,
not acceptance evidence for this candidate. No signing, publication or F-Droid
reproducibility check was performed for 0.9.2. The build used an exported source
tree without Git metadata; it does not reproduce the earlier signed release.

## Historical published release evidence

The following records describe the 0.9.1 submission at the time recorded; its current external review status was not rechecked for this local preparation. They are not acceptance evidence for the unpublished 0.9.2 candidate.

Source reviewed: v0.9.1, commit `9a3db36192c3b0862d8ab782db4f7386ff384f13`.
F-Droid submission: <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310>.
At the time of that release record, human review, merge and official repository publication were pending.

F-Droid uses automated build, metadata lint/format/schema, update checks,
source scans and APK scans. The [developer-signature pipeline](https://gitlab.com/CodingEzio/fdroiddata/-/pipelines/2916041155)
passed all nine jobs at recipe commit `4be6afa97cd3c3ded5a84c6236f8a67db0437a7b`.
Its build log confirms the downloaded developer APK and signature-copy rebuild
both verify. Its Code Quality view reports findings;
it is not proof that every source line was audited or that runtime behavior,
security and performance have no defects.

## Findings reviewed

The updated report has 18 new entries: two warnings and 16 informational entries.
The extra informational entry records the public release signing identity.
One reported fixed finding concerns another app in the comparison baseline.

| Finding | Meaning and decision |
|---|---|
| Major: Cleartext Traffic Permitted | Real limitation: optional LAN shelf pairing and media use unencrypted HTTP. Keep it on a trusted private network. Turning off cleartext would break that feature; encrypted pairing/transport needs a coordinated Android/server change. |
| Minor: INTERNET permission | Required for the optional user-selected LAN shelf, not evidence of analytics. Local file playback works without a shelf connection. |
| Informational permissions | Playback, downloads, notifications, audio, local-network and network-state access. No blanket claim that permissions prove or disprove tracking. |
| Informational packaging | APK/ABI size, R8 marker, store text/screenshots, signing identity and reproducible APK. No requested code fix. |

Source review confirms no declared advertising or analytics SDK in the release
dependency list. `ShelfAddress` rejects non-local resolved addresses;
`HttpShelfTransport` rejects redirects. The app still supports hostname inputs,
so validation is not a permanent guarantee about a DNS name's later resolution.
Do not treat the private-network restriction as encryption or as a complete
network security audit. See [PRIVACY.md](PRIVACY.md).

## Improvements for distribution

- A stable developer release key and public fingerprint replace the missing
  installable release artifact.
- The release helper verifies the expected certificate and preserves aligned
  APK bytes; checksums and provenance permit independent checks.
- The F-Droid recipe requests verification of the same signed APK, so accepted
  releases can retain the update identity across distribution channels.
- Three-language installation instructions separate GitHub availability from
  pending F-Droid inclusion.

A future transport revision should provide encrypted pairing/media and pin
validated connection addresses. These are product changes, not requirements
raised by a maintainer in this submission. Automated scanners do not replace
Android runtime checks. Emulator results do not establish physical-device
performance.
