package lightning.dsp.volumemanager.ui

/** Work apps are visible by default, but can be hidden without affecting personal copies. */
internal object WorkProfileVisibilityPolicy {
    const val DEFAULT_SHOW_WORK_APPS = true

    fun includes(workProfile: Boolean, showWorkProfileApps: Boolean): Boolean =
        !workProfile || showWorkProfileApps
}
