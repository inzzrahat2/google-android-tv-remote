# Architecture

```
UI (Compose screens + ViewModels)
   │  reads StateFlow, calls RemoteController
   ▼
domain/remote/RemoteController          ← the only remote API the UI sees
   ▼
service/connection/ConnectionManager    ← state machine, command queue, reconnection
   ▼
protocol/core/ProtocolRegistry ─┬─ protocol/mock     (debug builds only)
                                ├─ protocol/androidtv (Phase 3: Android TV Remote v2)
                                └─ future adapters
```

## Layers

| Package | Responsibility |
|---|---|
| `domain/model` | Pure Kotlin types: `TvDevice`, `RemoteKey`, `RemoteCapabilities`, `ConnectionState`, `RemoteError`, `PairingState`, `AppSettings`. No Android imports. |
| `domain/repository`, `domain/remote` | Interfaces the rest of the app depends on. |
| `protocol/core` | `RemoteProtocol`, `RemoteSession`, `PairingSession`, `DiscoverySource`. Every protocol adapter implements these. |
| `service/connection` | `ConnectionManager` (implements `RemoteController`), `ReconnectPolicy`, `AutoConnector`. |
| `data` | DataStore-backed repositories (devices, settings). |
| `core` | Platform services: logging, haptics, network monitor, permissions, discovery aggregation. |
| `ui` | Compose screens, one ViewModel per screen, theme, navigation. |

## Key decisions

- **Manual DI (`AppContainer`)** instead of Hilt. The graph has about 10 objects, so annotation processing would cost build time without saving code.
- **DataStore + JSON** instead of Room. A household has a handful of TVs, and there is nothing to query.
- **Capabilities drive the UI.** Each `RemoteSession` publishes `RemoteCapabilities`. Controls for unsupported features are not rendered.
- **Single serial dispatcher for connection state.** `ConnectionManager` runs on `Dispatchers.Default.limitedParallelism(1)`, so connect, disconnect and reconnect transitions cannot interleave. Adapters do their socket I/O on `Dispatchers.IO`.
- **Commands are fire-and-forget and ordered.** `send()` puts the command on a bounded channel (32, drop-oldest). A single consumer writes to the open session. If the socket stalls, it cannot build up a backlog of stale presses.
- **Keys go out on touch-down.** Arrows and volume send `CLICK` on press, then repeat while held. Other keys send `DOWN`/`UP`, which mirrors a real remote so the TV applies its own long-press behaviour.
- **Session reuse.** One long-lived session per TV. A command never opens a connection.

## Connection state machine

```
Idle ──connect──▶ Connecting ──ok──▶ Connected ──session lost──▶ Reconnecting
                     │                                  ▲            │
                     │ not paired / creds rejected       └──── ok ────┘
                     ▼                                               │ max attempts / auto-reconnect off
               PairingRequired                                       ▼
                                                               Disconnected(error)
```

The backoff is exponential: 500 ms, doubling, capped at 30 s, with ±20% jitter and at most 8 attempts. A network-available event or the user tapping "Try again" ends the wait immediately.
