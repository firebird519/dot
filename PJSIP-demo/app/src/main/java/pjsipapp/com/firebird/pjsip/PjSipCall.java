package pjsipapp.com.firebird.pjsip;

import android.util.Log;

import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.CallMediaInfoVector;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.OnCallMediaStateParam;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.VideoPreview;
import org.pjsip.pjsua2.VideoWindow;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsua2;
import org.pjsip.pjsua2.pjsua_call_media_status;

/**
 * Created by liyong on 17-7-20.
 */

public class PjSipCall extends Call {
    private static final String TAG = "PjSipCall";

    public VideoWindow vidWin;
    public VideoPreview vidPrev;

    public PjSipCall(PjSipAccount acc, int call_id) {
        super(acc, call_id);
        vidWin = null;
    }

    @Override
    public void onCallState(OnCallStateParam prm) {
        // TODO: notify call state
        //org.pjsip.pjsua2.app.MyApp.observer.notifyCallState(this);
        try {
            CallInfo ci = getInfo();
            if (ci.getState() ==
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                this.delete();
            }
        } catch (Exception e) {
            return;
        }
    }

    @Override
    public void onCallMediaState(OnCallMediaStateParam prm) {
        CallInfo ci;
        try {
            ci = getInfo();
        } catch (Exception e) {
            return;
        }

        CallMediaInfoVector cmiv = ci.getMedia();

        for (int i = 0; i < cmiv.size(); i++) {
            CallMediaInfo cmi = cmiv.get(i);
            if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    (cmi.getStatus() ==
                            pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                            cmi.getStatus() ==
                                    pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)) {
                // unfortunately, on Java too, the returned Media cannot be
                // downcasted to AudioMedia
                Media m = getMedia(i);
                AudioMedia am = AudioMedia.typecastFromMedia(m);

                // connect ports
                try {
                    PjSipController pjSipController = PjSipController.getInstance(null);
                    if (pjSipController != null) {
                        pjSipController.getEndPoint().audDevManager().getCaptureDevMedia().
                                startTransmit(am);
                        am.startTransmit(pjSipController.getEndPoint().audDevManager().
                                getPlaybackDevMedia());
                    } else {
                        Log.e(TAG, "PjSipController is not init!!");
                    }
                } catch (Exception e) {
                    continue;
                }
            } else if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_VIDEO &&
                    cmi.getStatus() ==
                            pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE &&
                    cmi.getVideoIncomingWindowId() != pjsua2.INVALID_ID) {
                vidWin = new VideoWindow(cmi.getVideoIncomingWindowId());
                vidPrev = new VideoPreview(cmi.getVideoCapDev());
            }
        }

        // TODO: to add call media state notify
        //org.pjsip.pjsua2.app.MyApp.observer.notifyCallMediaState(this);
    }
}
