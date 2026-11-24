# Android TV Remote

A fast, private remote control for Android TV and Google TV, for Android phones and tablets.

It talks to the TV directly over your Wi-Fi using the same remote protocol as Google's own
TV remote. There is no account, no cloud service, no ads and no analytics.

> **Status:** feature-complete and unit-tested, but not yet verified on real TV hardware.
> Reports from real devices are very welcome. See [Compatibility](#compatibility).

## Features

- **Find your TV automatically** on the local network (mDNS), or add it by IP address
- **Secure pairing** with the code shown on the TV; reconnects automatically afterwards
- **D-pad, Home, Back, Recents, Menu** with haptics, key repeat on hold and long-press support
- **Volume and mute** with the TV's current level shown when the TV reports it
- **Media controls**: play/pause, previous/next, rewind/fast-forward
- **Keyboard**: type on the TV with your phone's keyboard; edits are sent efficiently
- **Paste to TV** (opt-in; the clipboard is read only when you tap it)
- **Voice search** (hold to talk) on TVs that support it
- **Touchpad mode** and **swipe navigation**
- **App shortcuts**: launch apps on the TV through app links, with favorites and search
- **Quick actions bar**, **input source** keys, power (with confirmation)
- **Multiple TVs**: favorites, rename, reorder, quick switcher on the remote screen
- **Home-screen widgets** and a **Quick Settings tile**
- **Light, dark and AMOLED themes**, Material You colors, landscape and tablet layouts
- **Customizable remote**: hide buttons you never use; standard, minimal and full layouts
- Only controls the connected TV actually supports are shown

## Requirements

- Android 8.0 (API 26) or newer on the phone
- An Android TV or Google TV device with the **Android TV Remote Service**
  (`com.google.android.tv.remote.service`), which is preinstalled on most devices
- Phone and TV on the same network

On Android 17 and newer the app asks for the **Local network access** permission; without it
Android blocks all traffic to the TV.

## Build and install

Requirements: JDK 17+ and the Android SDK with platform 37. The Gradle wrapper downloads
everything else.

```sh
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release builds are minified and signed only when a `keystore.properties` file is present;
see [docs/BUILD.md](docs/BUILD.md).

## Using it

1. Open the app and tap **Find my TV**.
2. Pick your TV under **Nearby TVs** and tap **Pair**.
3. Type the 6-character code the TV shows.
4. The remote opens and connects. Next time it connects on its own.

If your TV doesn't show up, use **Add TV by IP address** (the IP is in the TV's network
settings).

Debug builds also include a **Simulated TV** for trying the app without hardware
(pairing code `A1B2C3`).

## How it works

```
Compose UI ──▶ RemoteController ──▶ ConnectionManager ──▶ Protocol registry
                                     (state machine,         ├─ Android TV Remote v2
                                      reconnection,          └─ Simulated TV (debug)
                                      command queue)
```

- **Protocol:** Android TV Remote Service v2. Pairing (port 6467) uses the "Polo" protocol
  published in AOSP. The remote session (port 6466) is TLS with a client certificate and
  length-prefixed protobuf messages. The pairing protocol is documented by Google; the remote
  message set is not officially documented and follows the community's
  reverse-engineered description. See [docs/PROTOCOLS.md](docs/PROTOCOLS.md).
- **UI never depends on a protocol.** Each session reports its capabilities, and the UI only
  shows what is supported. Other protocols can be added behind the same interface.
- **One long-lived connection** per TV; key presses never open a new connection. Keys are sent
  on touch-down for low latency.
- **Reconnection** uses exponential backoff, restarts promptly when Wi-Fi comes back, finds a
  TV whose IP address changed, and disconnects 30 s after the app goes to the background.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Privacy and security

- Everything stays on your local network. No servers, accounts, analytics or crash reporting.
- The phone's pairing key lives in the Android Keystore and cannot be exported.
- The TV's certificate is pinned at pairing time; if it changes, the app asks you to pair again
  instead of connecting silently.
- Pairing codes, typed text, clipboard contents and voice audio are never stored or logged.

See [docs/SECURITY.md](docs/SECURITY.md).

## Limitations

These come from the TV protocol, not the app:

- The app cannot list the apps installed on the TV. App shortcuts are links you save
  (suggestions are included); they open only if the TV has an app that handles them.
- Touchpad mode moves the TV's focus with arrow keys; there is no mouse pointer.
- Power state can't be confirmed, so the app never claims the TV turned off.
- Input switching works only on TVs whose firmware handles the input keys.

## Compatibility

No device has been verified yet. If you try the app on a TV, please open an issue with the
device model, Android TV version and what worked; results go into
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Development

```sh
./gradlew :app:testDebugUnitTest           # JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # UI tests, needs a device or emulator
```

The unit tests cover the protocol codec, pairing, sessions, the connection state machine,
persistence and UI logic; the UI tests run against the simulated TV. See
[docs/TESTING.md](docs/TESTING.md).

Project layout (`app/src/main/java/com/techport/tvremote/`):

| Package | Contents |
|---|---|
| `domain` | Models and interfaces, no Android dependencies |
| `protocol` | Protocol interface, Android TV adapter, simulated TV |
| `service` | Connection manager, lifecycle handling, Quick Settings tile |
| `data` | DataStore persistence for TVs, settings and app shortcuts |
| `core` | Discovery, network monitor, permissions, haptics, clipboard, voice, logging |
| `ui` | Compose screens and components |
| `widget` | Home-screen widgets |

Built with Kotlin, Jetpack Compose, Material 3, coroutines and DataStore. Dependencies are kept
to AndroidX and kotlinx; there is no networking or protobuf library.
