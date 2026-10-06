# Quality checks and limits

Source reviewed: v0.9.1, commit `9a3db36192c3b0862d8ab782db4f7386ff384f13`.
F-Droid submission: <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51310>.
Human review, merge and official repository publication are pending.

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
