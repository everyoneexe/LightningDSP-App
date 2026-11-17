package lightning.dsp.volumemanager

import lightning.dsp.session.shared.SESSION_DSP_ENABLED_PREFIX
import lightning.dsp.session.shared.SessionDspControlPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDspControlPreferencesTest {
    @Test fun personalProfileKeepsItsExistingPreferenceIdentityAndDefault() {
        assertEquals("com.example.player", SessionDspControlPreferences.identityKey("com.example.player", 0, 0))
        assertEquals(SESSION_DSP_ENABLED_PREFIX + "com.example.player",
            SessionDspControlPreferences.preferenceKey("com.example.player", 0, 0))
        assertTrue(SessionDspControlPreferences.defaultEnabled(0, 0))
    }

    @Test fun samePackageInWorkProfileHasIsolatedIdentityAndStartsOptedOut() {
        assertEquals("user:10:com.example.player",
            SessionDspControlPreferences.identityKey("com.example.player", 10, 0))
        assertEquals(SESSION_DSP_ENABLED_PREFIX + "user:10:com.example.player",
            SessionDspControlPreferences.preferenceKey("com.example.player", 10, 0))
        assertFalse(SessionDspControlPreferences.defaultEnabled(10, 0))
    }

    @Test fun currentSecondaryUserStillUsesItsLegacyLocalPackageKey() {
        assertEquals("com.example.player", SessionDspControlPreferences.identityKey("com.example.player", 10, 10))
        assertTrue(SessionDspControlPreferences.defaultEnabled(10, 10))
    }

    @Test fun androidUidKeepsUserAndAppIdentitySeparated() {
        assertEquals(0, SessionDspControlPreferences.userIdForUid(10123))
        assertEquals(10, SessionDspControlPreferences.userIdForUid(1_010_123))
    }
}
