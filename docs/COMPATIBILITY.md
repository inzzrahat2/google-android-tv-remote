# Compatibility matrix

Nothing is listed as supported until it has been tested on real hardware.

Legend: ✓ verified · ✗ not supported · ? untested

| Device | OS | Discovery | Pairing | D-pad | Volume | Keyboard | Apps (links) | Voice | Power | Inputs | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| *(none yet)* | | | | | | | | | | | Android TV adapter implemented; awaiting real-device results |

## Phone side
- minSdk 26 (Android 8.0), targetSdk 37 (Android 17).
- Android 17+: the user must grant **Local network access** (`ACCESS_LOCAL_NETWORK`); without it all LAN traffic is blocked.

## To verify per device
- Whether a quick `START_LONG`/`END_LONG` on DPAD_CENTER registers as a click (it should; it mirrors key down/up).
- Long-press OK opening the context menu.
- `TV_INPUT` and `TV_INPUT_HDMI_n` handling.
- Whether volume level is reported (CEC/soundbar setups often don't report it).
