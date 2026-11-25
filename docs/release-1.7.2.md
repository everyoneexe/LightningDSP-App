# LightningDSP app 1.7.2-beta-1

This pre-release brings the work-profile and volume-panel work forward, improves audio recovery, and includes an optional build for testing the modern libxposed API. The usual APK still uses the legacy Xposed entry point to preserve compatibility with existing setups.

### Changes

#### Work profiles and app controls
- Show work-profile apps distinctly in Volume Manager, with a briefcase badge and independent per-app volume and DSP controls.
- Enable work-profile DSP by default only when Android exposes an eligible work profile. Keep existing saved choices, and let users change the setting in **Volume Manager → Settings → App controls**.
- Route eligible work-profile audio in Session and Fusion while preserving full Android UIDs, personal/work separation, and the relay when package inventory is restricted.
- Keep app rows stable when DSP power changes, including while the panel is visible on the lock screen.

#### SystemUI volume panel
- Use the SystemUI volume-window path for the optional replacement, while retaining stock controls until the trusted LightningDSP surface is ready.
- Improve the panel's initialization after Vector injects late and after SystemUI services become available.
- Keep the panel centered and sized to its content; expand its media controls without turning the panel into a scrolling viewport.
- Preserve app rows and media grouping through lock-screen, wake and unlock transitions, and allow touches outside the visible panel to reach the underlying screen.
- Keep the stock path safe during startup and for Samsung devices unless the replacement is explicitly ready and enabled.

#### Audio and hook reliability
- Recover Session Mode when AudioFlinger rejects the first effect attachment, without accepting stale work.
- Keep app-compatibility hooks away from SystemUI and the system framework, where app-specific audio hooks do not belong.
- Keep Fusion responsive without reducing its playback headroom; improve work-profile routing without disabling the relay when Android's package inventory is incomplete.
- Add repeatable OpenSL route checks for diagnosis. These checks do not change the APK's audio behavior.

#### Optional libxposed API 102 candidate
- The release also provides a separately named root universal APK built against libxposed API 102 for testing with a compatible modern hook framework.
- The normal APK remains on the legacy Xposed API path. The two APKs are alternatives with the same package and signing certificate; do not install or distribute them as separate apps.
- The API 102 candidate has not been validated across Vector/LSPosed versions or on a full device matrix. Use the legacy APK if the modern framework does not expose the module or its hooks.

### Versions and compatibility

Root app **1.7.2/code 146**, package `com.lightning.dsp`, signed with the existing owner certificate. The universal APK includes arm64-v8a, armeabi-v7a, x86 and x86_64 app libraries. This does not make the module's ARM64 audio driver universal.

Work-profile routing depends on Android exposing the profile and an eligible package inventory through the supported root path. The SystemUI replacement requires its app scope and hook enabled in a compatible Vector/LSPosed setup; stock controls remain the fallback when the replacement is unavailable. Global Mode still cannot exclude one app from a shared output mix.

### Known issues before stable

- Some Bluetooth headsets can still produce intermittent distortion with the SBC codec in Fusion Mode. LDAC has been stable in the reports so far; the cause and a reliable fix are still under investigation.
- The hooked SystemUI volume panel remains unreliable on some Samsung One UI devices. It has been working on the Pixel 10 Pro XL used for project testing, but that does not establish Samsung or device-wide compatibility.
- In temp-root environments, an audio APEX that becomes available after the module's late-load check may still be missed. This timing case needs validation before stable 6.7.2.

Unit tests and both Xposed build variants are checked in CI. The modern API candidate still needs real-device testing; framework and firmware behavior can vary.
