# Dependencies and asset licenses

Program source and original app icon/appearance pictograms are licensed under
GPL-3.0-or-later. The original author is codingEzio. Third-party licenses below
remain in force; see the packaged notices under app/src/main/assets/licenses.

| Component | Version | License / source |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | Apache-2.0 / Google Maven |
| Gradle | 9.6.0 | Apache-2.0 / services.gradle.org |
| Kotlin and Compose compiler plugin | 2.4.20 | Apache-2.0 / Maven |
| Compose BOM | 2026.09.00 | Apache-2.0 / Google Maven |
| Material3 | 1.5.0-alpha28 | Apache-2.0 / Google Maven; Expressive APIs |
| Activity Compose | 1.13.0 | Apache-2.0 / Google Maven |
| Media3 ExoPlayer, Session, Inspector | 1.11.1 | Apache-2.0 / Google Maven |
| JUnit | 4.13.2 | EPL-1.0 / test only |
| AndroidX Test runner / extension | 1.7.0 / 1.3.0 | Apache-2.0 / test only |
| JDK | 17 | Free OpenJDK distribution, build only |
| Android SDK / Build Tools | 37 / 36.0.0 | Official Android SDK, build only |

All runtime Maven dependencies use Google Maven or Maven Central. No Play
Services, Firebase, ad SDK, proprietary codec or FFmpeg binary is bundled.
Media decoding and SQLite are Android platform services.

Eight bundled font files are enumerated with SHA-256 in FONT_ASSETS.json:
Geist Pixel, Geist, Geist Mono, Doto, Roboto, Roboto Condensed, Roboto Mono and
DeparturePixelZh Compact. They use SIL OFL-1.1; the modified Compact face includes
Departure Mono, Cubic 11 and Nerd Fonts glyphs with their component notices.
All original notices, attribution, reserved-name statements and pinned source
provenance are retained under app/src/main/assets/licenses. These non-code assets
are not relicensed under the app's GPL. No proprietary Nothing fonts are included.

The optional shelf uses Deno (MIT), ffprobe (FFmpeg license depends on build) and
shasum (Perl distribution), installed separately and never bundled in the APK.
Optional icon preview tooling requires Python and Pillow (HPND); it is not needed
to build the Android app. The editable original icon is an included SVG.
