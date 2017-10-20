package pjsipapp.com.firebird;

import android.app.Application;
import android.util.Log;

import pjsipapp.com.firebird.pjsip.PjSipController;

/**
 * Created by liyong on 17-7-20.
 */

public class PjSipApplication extends Application {
    private static final String TAG = "PjSipApplication";

    private PjSipController mPjsipController;

    @Override
    public void onCreate() {
        super.onCreate();

        initPjLib();
    }

    private void initPjLib() {
        Log.d(TAG, "initPjLib...");
        mPjsipController = PjSipController.getInstance(getApplicationContext());
        mPjsipController.init();
    }
}
