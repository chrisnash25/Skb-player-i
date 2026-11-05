# SKB AI Player — Option B verification matrix

## Software checks
- CI: compile experimental M5Stick firmware via PlatformIO.
- CI: protocol packet encoding tests.
- Android: verify physical USB controller KeyEvent and MotionEvent handling.
- Android: implement and test BLE GATT client, permission flow, reconnect and stop.

## Device checks (NOT possible in CI)
1. Backup current SKB Headless Pro firmware before flashing the experimental image.
2. Pair M5Stick as BLE HID with Galaxy S21.
3. Verify Android InputDevice identifies controller and its axes/buttons.
4. Verify Android BLE GATT client can write command packets while HID stays connected.
5. Confirm a test game responds to neutral, A/B, triggers, both sticks and release.
6. Confirm watchdog returns to neutral within 500 ms when packets stop.
7. Verify Xbox Cloud Gaming recognizes and uses gamepad.
8. Verify WebView frame capture actually includes protected video; if black, AI autonomy is blocked.

## Security and limitations
- Prototype GATT command service is not yet restricted to an authenticated/authorized client.
- Do not use near other paired devices or rely on this for production.
- Firmware is NOT an Xbox console controller and cannot be assumed to work on Xbox hardware.
- 60Hz HID report repetition does not mean Gemini can make 60 AI decisions per second.
- The approved SKB logo/web dashboard is not implemented yet.
