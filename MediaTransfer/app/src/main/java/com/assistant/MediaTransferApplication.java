package com.assistant;

import android.app.Application;
import android.os.SystemClock;

import com.assistant.ui.permissiongrant.PermissionHelper;
import com.assistant.utils.Log;

public class MediaTransferApplication extends Application {
    PermissionHelper mPermissionHelper;
    @Override
    public void onCreate() {
        super.onCreate();

        mPermissionHelper = new PermissionHelper(getApplicationContext());
        if (mPermissionHelper.isWriteExternalStrogagePermissionGranted()) {
            Log.init(getApplicationContext());
        }

        Log.d(this, "onCreate time:" + SystemClock.elapsedRealtime());

        MediaTransferService.startService(getApplicationContext(), true);

        // TODO: to add monitor process/job schedule to keep this process keep alive?
    }
}
