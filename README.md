# SKB AI Player B1 — Experimental browser compatibility prototype

This is **not an autonomous Fortnite player**. It tests whether Android WebView can open Xbox Cloud Gaming and exposes a read-only JavaScript Gamepad API diagnostic. It does not capture gameplay frames, inject inputs, or include an AI decision engine.

## Build
Push the contents to a GitHub repository's main branch, open Actions > Build Android APK > Run workflow, and download the SKB-B1-debug-apk artifact if the build succeeds. GitHub Actions is the compilation verification step; this source package has not been compiled or tested on physical hardware.

## Technical caveats
Xbox Cloud Gaming may reject Android WebView or fail DRM, authentication, streaming, or controller compatibility. navigator.getGamepads() reads existing physical controllers; it cannot create a trusted virtual gamepad. The web page is third-party and is not controlled by this app. No attempt is made to bypass restrictions.
