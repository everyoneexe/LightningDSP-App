# Test the modern Xposed API

The normal root build still uses the legacy Xposed API. To build the same
root app with libxposed API 102, use:

```sh
./gradlew -Plightningdsp.modernXposed=true \
  :app:testRootFullReleaseUnitTest :app:assembleRootFullRelease
```

Use a framework that implements API 102, such as Vector 2.2. Keep the legacy
build on Android 8.x, where Vector does not dispatch the modern app callbacks.
The test APK
needs the same owner signature as the installed root app for an in-place
update. Use only one build at a time. Keep the same device, framework version,
and scope when comparing the legacy and modern builds. Hot reload is not
enabled; use the framework's normal process lifecycle before testing the hooks.

The build uses the same root package, settings, DSP and native compatibility
library. It switches the Java and native module entries for all three hook
paths: SystemUI volume panel, system audio focus, and audio app compatibility.
SystemUI and System Framework are suggested scopes, not forced scopes. Audio
app hooks still need the affected app in scope.

Check the stock panel with the replacement disabled, then check the
LightningDSP panel with it enabled, including screenshots, lock/unlock and
outside touches. Check the audio-focus toggle and voice-call exclusion in
System Framework, and session reuse or audio compatibility in a supported
scoped app. The modern API does not fix a missing vendor callback by itself.
