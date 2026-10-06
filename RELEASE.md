# Release and signature verification

Release APKs are available at <https://github.com/codingEzio/Resonance/releases>.
Use the APK and `SHA256SUMS` from the same release. On macOS/Linux, in the
download directory, run `shasum -a 256 -c SHA256SUMS`. Check the signing identity
with Android SDK `apksigner verify --verbose --print-certs Resonance-v0.9.1.apk`.
The certificate SHA-256 is:

```text
33eb38cc3270c7c96bc71b0ef866f04a9135f4f4a494ee1e1583c613d1816d6c
```

The same public identity is in `release-signing.json`. Checksums detect a changed
download; the certificate identifies the release key. Keep the public release
key for future updates. Never publish its keystore or password. The key is
different from debug keys and private development editions.

## Build and sign

Build from a clean Git checkout of the release tag, not a source ZIP. Android
Gradle Plugin embeds the checkout revision; a source archive produces different
version-control metadata. For 0.9.1, the source is commit
`9a3db36192c3b0862d8ab782db4f7386ff384f13`.

```sh
git checkout v0.9.1
./gradlew -Presonance.profile=portable :app:assembleRelease --no-daemon
```

The signing helper and release documentation were added after the first source
tag. Use the helper from main with the unsigned APK built from the release tag.
Do not change the tag to include these later files.

Install Android SDK build-tools 34.0.0 for signing (builds still use 36.0.0),
Deno and a JDK 17. Use an existing PKCS12 keystore and its password file; both
must be readable only by their owner. The key and keystore passwords must match.
Create the output directory, then run from the checkout containing the helper:

```sh
mkdir -p Local/release
deno run --allow-read --allow-write=Local/release --allow-run scripts/sign-release.ts \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  Local/release/Resonance-v0.9.1.apk \
  /path/to/release.p12 /path/to/password-file /path/to/build-tools/34.0.0
```

The helper checks ZIP alignment, signs, verifies the pinned certificate and
prints the APK checksum. It refuses to overwrite an output. It passes only
password-file paths to tools, never password values. APK signature schemes v2
and v3 cover Android 10+, the supported minimum. v1 is disabled because it is
not required on supported devices and its ZIP flags caused a signature-copy
mismatch with the checked tooling. Existing 16 KiB ZIP alignment is preserved.

Before upload, check the APK package ID/version, install and launch it on an
isolated Android device/emulator, and compare against the F-Droid rebuild with
`apksigcopier compare signed.apk --unsigned fdroid-unsigned.apk`. Publish the
APK only when verification passes, along with `SHA256SUMS`, the public
certificate fingerprint and source/build provenance. Never publish a CI test
signing key or use a CI test-signed APK as a permanent release.

## F-Droid distribution

The inclusion recipe requests developer-signed reproducible distribution using
`Binaries` and `AllowedAPKSigningKeys`. F-Droid must reproduce the APK and verify
the expected key before publication. If it accepts this recipe and publishes,
GitHub and F-Droid APKs can update one another without changing the signing key.
This is requested, not an assertion that the app has already been accepted.
See <https://f-droid.org/en/docs/Reproducible_Builds/>.
