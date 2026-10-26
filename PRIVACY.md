# Privacy

Resonance needs no account and includes no advertising or analytics SDK.
Imported media, playback state, queue, settings and wishes are stored locally.
The Android file picker grants access to files you select. Imported copies remain
in the app's private storage. Uninstalling the app removes its private data;
Android cloud backup is disabled.

Network access is used when you explicitly connect to an optional LAN shelf.
That connection exchanges a pairing credential, media metadata and requested
media bytes with the computer you select. HTTP LAN transport is not encrypted;
use a trusted private network. No hosted media or recognition service is required.
The optional shelf stores configuration, its pairing key and catalog in an
OS-private data profile outside the source checkout and serves only configured
roots. `RESONANCE_DATA` selects a different absolute data profile;
`RESONANCE_CONFIG` selects the settings file without changing the data owner.
Existing checkout-local shelf data requires an explicit migration before normal
startup. Keep the profile, pairing key and catalogs out of Git and shared reports.

Notifications and foreground services support playback and downloads. The app
requests local-network permission where required by Android. It does not fetch
remote album artwork or upload media to a developer-operated service.

Reports submitted to a public issue tracker are public. Remove personal media
names, addresses and pairing credentials before sharing diagnostic material.
