# Protocols

## Status

| Protocol | Adapter | Status |
|---|---|---|
| Simulated TV (`mock`) | `protocol/mock` | Done. Debug builds only. No network. |
| Android TV Remote Service v2 (`androidtv`) | `protocol/androidtv` | Planned for Phase 3 |

## Android TV Remote Service v2

This is the protocol the Google TV mobile app uses. On the TV side it is served by the
*Android TV Remote Service* (`com.google.android.tv.remote.service`), which is preinstalled
on most Android TV and Google TV devices. It needs no ADB and no developer mode.

**Documentation status (be honest about this):**
- The **pairing** layer ("Polo") is published in AOSP:
  `platform/external/google-tv-pairing-protocol` (`polo.proto`).
- The **remote** message set (`remotemessage.proto`) is **not officially documented**.
  It is community-documented, and several independent open-source implementations
  interoperate with real devices (for example `tronikos/androidtvremote2`, which Home Assistant uses).
  Fields marked `unknownN` there are genuinely not understood.
  Message definitions are taken from that work, and behaviour must be verified on real hardware
  (see COMPATIBILITY.md).

### Discovery
mDNS service type `_androidtvremote2._tcp`, found via `NsdManager`. Port 6466 is advertised there.

### Transport
Both phases use TLS with a **client certificate**. Messages are protobuf, each prefixed with its
length as a varint.

### Pairing (port 6467)
1. `PairingRequest{service_name="atvremote", client_name}` → `PairingRequestAck`
2. `Options{input_encodings=[HEXADECIMAL, 6], preferred_role=INPUT}` → `Options`
3. `Configuration{encoding=HEXADECIMAL/6, client_role=INPUT}` → `ConfigurationAck`. The TV now shows a 6-character hex code.
4. The client computes `SHA-256(client_modulus ‖ client_exponent ‖ server_modulus ‖ server_exponent ‖ code[2..6])`.
   The hash's first byte must equal `code[0..2]`, which lets a mistyped code be detected locally.
   The client sends `Secret{hash}`, and the TV answers `SecretAck`.

Each message has the outer envelope `{protocol_version=2, status=200}`. Any other status is a failure.

### Remote session (port 6466)
- The TV sends `RemoteConfigure{code1=feature bitmask, device_info}`. The client replies with the intersection of features it wants plus its own `device_info`.
- The TV sends `RemoteSetActive`, and the client replies with `RemoteSetActive{active=features}`.
- The TV sends `RemoteStart{started}`, which means it is ready (and `started` tells whether the screen is on).
- The TV sends `RemotePingRequest{val1}` about every 5 s. The client must answer `RemotePingResponse{val1}`; the TV drops the connection after about 3 missed pings.
- Commands:
  - `RemoteKeyInject{key_code, direction}`: `SHORT` = click; `START_LONG`/`END_LONG` = key down/up.
  - `RemoteImeBatchEdit` sets text in the focused field, using counters echoed from the TV's last batch-edit message.
  - `RemoteAppLinkLaunchRequest{app_link}` opens a URL or deep link.
  - Voice: send `KEYCODE_SEARCH`, wait for `RemoteVoiceBegin{session_id}`, echo it back, then stream `RemoteVoicePayload` (PCM 16-bit mono 8 kHz, 8–20 KiB chunks) and finish with `RemoteVoiceEnd`.
- State from the TV: `RemoteSetVolumeLevel` (level, max, muted) and `RemoteImeKeyInject.app_info.app_package` (the foreground app).

Feature bits: PING=1, KEY=2, IME=4, VOICE=8, POWER=32, VOLUME=64, APP_LINK=512.

### Mapping to `RemoteCapabilities`

| Capability | Source |
|---|---|
| dpad, navigation, systemKeys, mediaControls, channels | KEY bit |
| keyboard | IME bit |
| volume, mute | KEY bit (VOLUME_UP/DOWN/MUTE keycodes) |
| volumeLevel | VOLUME bit / a received `RemoteSetVolumeLevel` |
| power | POWER bit |
| appLaunch | APP_LINK bit |
| voice | VOICE bit, confirmed by a `RemoteVoiceBegin` arriving |
| inputSwitching | KEY bit; whether the TV honours `TV_INPUT`/HDMI keycodes varies, so this needs per-device verification |

### Known limits of this protocol
- **No list of installed apps.** The app launcher can only open app links or deep links. It cannot enumerate apps.
- **No pointer or mouse events.** "Touchpad" mode is a gesture surface that maps swipes and taps to D-pad keys.
- **No confirmed power state** beyond `RemoteStart.started`. The UI never claims the TV turned off.
- **Input switching** works only on TVs whose firmware handles the `TV_INPUT*` keycodes.

## Adding a protocol
Implement `RemoteProtocol` (with discovery, pairing and session), give it a permanent `id`,
and register it in `AppContainer.protocols`. Nothing in `ui/` or `service/` changes.
