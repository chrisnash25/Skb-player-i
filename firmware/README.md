# SKB AI Controller Bridge — experimental

Target: M5StickC Plus2 (ESP32-PICO-V3-02). Separate from SKB Headless Pro.

## Current scope
- BLE HID gamepad descriptor: 16 buttons, 4 signed 8-bit axes, 2 unsigned 8-bit triggers.
- Custom BLE GATT command characteristic: UUID 7a31f0e0-5bd1-4df5-b36e-6c8c1b319002.
- Eight-byte command payload: buttons low/high, LX, LY, RX, RY, LT, RT.
- Input reports emitted approximately every 16 ms while connected.
- Inputs reset to neutral if no valid command arrives for 500 ms or on disconnect.
- **No screen dependency.**

## Important limitations
This is an uncompiled experimental starting point, not verified firmware. It does not yet implement the approved branded web dashboard, Android BLE GATT transport, authentication/authorization for command writes, or AI-to-gamepad state mapping. Pairing, security behavior, BLE GATT coexistence, and Xbox Cloud Gaming compatibility require device tests. Do not rely on this for privileged or safety-critical input. Do not flash over your working headless firmware without a backup.

## Build
PlatformIO: `pio run -d firmware -e m5stickc-plus2-bridge`.
Android must pair as HID and a separate app GATT client must be developed and tested.
