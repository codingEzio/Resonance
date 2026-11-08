# Roboto combo notices

The Android app includes the Google Fonts families Roboto, Roboto Condensed,
and Roboto Mono as TTF files. Each bundled TTF is byte-identical to its source
at Google Fonts commit `809e4d8b8d7e9364a914909bb777679606c178b8`; file hashes
are recorded in `FONT_ASSETS.json` in the source distribution.

The adjacent `roboto-provenance.json` records a separate browser WOFF2
conversion from these upstream TTFs. Those WOFF2 files are not bundled in this
Android app; the conversion does not describe the included TTF files.

The pinned Google Fonts distribution supplies SIL Open Font License 1.1 notices
for these families. The exact Roboto Condensed TTF matches the pinned upstream
file byte for byte, but its embedded metadata also names Apache License 2.0.
Keep the matching `OFL-Roboto*.txt` and `Apache-2.0.txt` notices when
redistributing the fonts. The families cover
Latin and related scripts, not CJK. Chinese uses the platform CJK fallback in
the Roboto mode.

Pinned Condensed source: https://github.com/google/fonts/tree/809e4d8b8d7e9364a914909bb777679606c178b8/ofl/robotocondensed
