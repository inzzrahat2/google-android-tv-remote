# Security

## Assumptions
- The TV and phone share a LAN that the user considers trusted enough for a remote control.
- An attacker on the LAN may observe or inject traffic, so all TV traffic is TLS.

## Credentials
- **Client identity:** an RSA key pair generated in the **Android Keystore**. The private key is never exportable and never leaves the phone. Its self-signed certificate is presented to the TV during TLS client authentication.
- **TV identity:** the TV's certificate is pinned (by SHA-256 fingerprint) at pairing time, trust-on-first-use. A later mismatch produces `CERTIFICATE_CHANGED` → the app asks the user to pair again. It never falls back to an unpinned connection silently.
- **Pairing codes** exist only in the dialog's in-memory state and are used once to compute the secret. They are never stored or logged.
- **Forgetting a TV** deletes its pinned fingerprint, its key alias and its saved record.
- `allowBackup=false` and data-extraction rules exclude everything, so credentials cannot be copied to another device through backup.

## Data handling
- No backend, no analytics, no crash reporting.
- Logs (an in-memory ring buffer, plus logcat in debug builds only) never contain pairing codes, keys, typed text, clipboard contents or voice audio. `RemoteCommand.Text.toString()` prints only the text's length.
- Network input is parsed defensively. Malformed frames close the session; they do not crash the app.

## Not done / known risks
- Trust-on-first-use cannot detect an attacker who is present during the *first* pairing. The 6-character code, which mixes in both certificates' public keys, is what defends against that.
