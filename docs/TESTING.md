# Testing

```
./gradlew :app:testDebugUnitTest          # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented UI tests (device/emulator)
```

## Unit tests
174 JVM tests in 25 classes. They use fakes (`FakeTransport`, `MockTvProtocol`, in-memory
repositories) and virtual time, so they need no TV.

| Area | Test classes |
|---|---|
| Connection | `ConnectionManager`, `ConnectionLifecycle`, `ReconnectPolicy`, `ConnectionIssues`, `Ipv4Subnet` |
| Android TV protocol | `WireCodec`, `Pairing`, `AndroidTvSession`, `AndroidTvSupport` (through `FakeTransport`) |
| Data | `DataStoreDeviceRepository`, `DataStoreAppShortcutRepository` |
| Keyboard and voice | `KeyboardViewModel`, `KeyboardAutoOpen`, `TextDiff`, `TextSyncer`, `VoiceViewModel`, `VoiceRecorder`, `ClipboardReader` |
| UI logic | `AppsLogic`, `AppsViewModel`, `QuickActions`, `GestureStepper`, `SwipeClassifier`, `DPadHitTest` |
| Widgets | `WidgetKeySender` |

## Instrumented tests
9 tests in 5 classes: `RemoteScreenTest` (4), `SettingsScreenTest` (2), `PairingFlowTest`,
`ConnectionErrorTest` and `KeyboardScreenTest` (1 each). They drive the Compose screens against the
simulated TV through `testing/MockTvHarness` and in-memory repositories. They need a device or
emulator.

## Mock TV
Debug builds register "Simulated TV". It appears under *Nearby TVs*; its pairing code (`A1B2C3`)
is shown in the pairing dialog. It tracks volume, mute and the foreground app, and records received commands.
Tests can also force connection failures (`failNextConnects`), drop the session (`drop()`) or revoke pairing (`revokePairing(id)`).

## Real-device checklist (Definition of Done)
Discover → pair → connect → D-pad / Home / Back / volume / media / keyboard / app link →
switch the TV off at the wall → turn it on → the app reconnects without user action.
Record results in COMPATIBILITY.md.
