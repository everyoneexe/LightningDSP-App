package lightning.dsp.volumemanager.surface;
import android.view.SurfaceControlViewHost.SurfacePackage;
oneway interface IVolumePanelSurfaceCallback {
    void surfaceReady(long generation, in SurfacePackage surface, boolean locked);
    void surfaceFailed(long generation);
    void dismiss(long generation);
    void interaction(long generation, boolean active);
}
