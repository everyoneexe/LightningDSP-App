package lightning.dsp.session.shared

/** Shared key for the optional per-app AudioEffect power setting in Session Mode. */
const val SESSION_DSP_ENABLED_PREFIX = "session_dsp_enabled:"

/**
 * Keeps existing current-user preference keys stable while isolating work-profile copies
 * of the same package. Work-profile DSP is intentionally opt-in; legacy/user installs
 * continue to default on.
 */
object SessionDspControlPreferences {
    private const val USER_ID_RANGE = 100_000

    fun userIdForUid(uid: Int): Int = if (uid < 0) 0 else uid / USER_ID_RANGE

    fun identityKey(packageName: String, userId: Int, currentUserId: Int): String =
        if (userId == currentUserId) packageName else "user:$userId:$packageName"

    fun preferenceKey(packageName: String, userId: Int, currentUserId: Int): String =
        SESSION_DSP_ENABLED_PREFIX + identityKey(packageName, userId, currentUserId)

    fun defaultEnabled(userId: Int, currentUserId: Int): Boolean = userId == currentUserId
}
