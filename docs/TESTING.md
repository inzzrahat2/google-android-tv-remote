# Testing

```
./gradlew :app:testDebugUnitTest          # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented UI tests (device/emulator)
```

## Unit tests (current)
| Test | Covers |
|---|---|
| `ConnectionManagerTest` | connect, ordered command delivery, volume mirroring, auto-reconnect after drop, backoff and give-up, network wake-up, pairing-required, deliberate disconnect, device switching |
| `ReconnectPolicyTest` | exponential growth, cap, jitter bounds |
| `DataStoreDeviceRepositoryTest` | persistence, favorites ordering, reorder rules, forget, rename |
| `DPadHitTestTest` | D-pad touch regions |

All of these use `MockTvProtocol` and virtual time, so they need no TV.

## Mock TV
Debug builds register "Simulated TV". It appears under *Nearby TVs*; its pairing code (`A1B2C3`)
is shown in the pairing dialog. It tracks volume, mute and the foreground app, and records received commands.
Tests can also force connection failures (`failNextConnects`), drop the session (`drop()`) or revoke pairing.

## Real-device checklist (Definition of Done)
Discover → pair → connect → D-pad / Home / Back / volume / media / keyboard / app link →
switch the TV off at the wall → turn it on → the app reconnects without user action.
Record results in COMPATIBILITY.md.
