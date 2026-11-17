# LightningDSP app 1.7.2

This update improves the SystemUI volume panel and work-profile audio controls.

### Changes

- Replace the stock volume panel through the SystemUI volume-window path while keeping the stock controls available until the LightningDSP surface is ready.
- Keep the replacement panel centered and expandable, allow touches outside its visible bounds to reach the screen, and preserve its style and app rows across lock-screen and wake transitions.
- Show work-profile apps separately in Volume Manager, with independent per-app volume and DSP controls.
- Include work-profile apps in Session and Fusion by default when Android exposes a work profile. The setting is available in **Volume Manager → Settings → App controls**; a saved user choice is preserved.
- Keep Fusion routing within the complete UID inventory available to the app and avoid disabling the relay when work-profile inventory cannot be read.

### Compatibility notes

Work-profile audio depends on Android exposing the profile and its app inventory to the supported root path. Global Mode does not provide per-app exclusions. SystemUI replacement still requires the LightningDSP SystemUI hook enabled in Vector/LSPosed; the stock panel remains the fallback when the hook or app surface is unavailable.

This release was verified with unit tests and compilation. Device and firmware behavior can vary, so the changes still require broader real-device testing.
