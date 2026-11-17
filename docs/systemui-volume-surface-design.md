# SystemUI volume surface

Status: Implementation in progress. Not device-validated.

The optional stock-panel replacement uses a SystemUI-owned volume window and an
app-owned embedded SurfaceControlViewHost surface (Android 11+). The existing
application overlay cannot appear above the notification shade simply by changing
flags. Compose, preferences, per-user identities and DSP stay in LightningDSP.

The exported surface service authenticates the actual Binder caller against the
installed SystemUI UID, owns one bounded generation/display/surface, and releases
on Binder death or heartbeat timeout. A signature-protected dynamic receiver
accepts explicit prepare/show/hide requests from LightningDSP. No system-window
permission is granted to the app and no keyguard policy is bypassed.

Native volume UI stays available until a usable surface is attached. Root
suppression requires an unexpired attachment receipt matching current keyguard
state (or a more restrictive stream-only presentation). Lost ownership, unsupported APIs, service death and failed attachment fail
open. Show requests never repeat the hardware volume adjustment.

Without a reliable keyguard transition listener (including Android 11/12), the
trusted host uses the stream-only presentation even while unlocked. This is an
explicit privacy-first fallback, not full per-app replacement on those devices.

The locked presentation contains system stream volume controls only, not app
names, work apps, media metadata, profiles, or settings. Screen-off and keyguard
transitions hide an unrestricted surface before another surface is presented.
The feature never unlocks the device or starts private apps.

Host tests do not prove window layering, touch delivery, keyguard privacy, OEM
compatibility or response latency. Device checks must cover shade/keyguard,
unlock/screen-off, repeated keys, drag/timeout, orientation, process death,
native safety warnings and Samsung/Fold fallback. Keep stock fallback until the
actual host/window attachment is acknowledged; a hook PID is not sufficient.
