package lightning.dsp.volumemanager.surface;
import lightning.dsp.volumemanager.surface.IVolumePanelSurfaceCallback;
interface IVolumePanelSurfaceService {
    oneway void attach(IBinder owner, IBinder hostToken, int displayId, int width, int height, long generation, boolean locked, IVolumePanelSurfaceCallback callback);
    oneway void heartbeat(IBinder owner, long generation, boolean attached, boolean locked);
    oneway void release(IBinder owner, long generation);
}
