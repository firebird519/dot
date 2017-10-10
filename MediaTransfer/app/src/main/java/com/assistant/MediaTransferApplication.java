package com.assistant;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import com.assistant.connection.ClientsSearchHandler;
import com.assistant.mediatransfer.ClientManager;
import com.assistant.ui.permissiongrant.PermissionHelper;
import com.assistant.utils.Log;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MediaTransferApplication extends Application {
    private static final String TAG = "MediaTransferApplication";
    private static MediaTransferApplication sApp;

    public static final String TEMP_FILE_DIR = "mediatransfer";

    private static String sTempFileDir = "";

    private PermissionHelper mPermissionHelper;

    private Set<Activity> mResumedActivity =
            Collections.synchronizedSet(new HashSet<Activity>(3));
    private boolean mAppPaused = false;
    private boolean mAppQuit = false;

    private static final int APP_PAUSED_CHECK_TIMESTAMP = 5 * 1000;
    private static final int EVENT_ACTIVITY_PAUSED = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_ACTIVITY_PAUSED:
                    handleActivityPaused();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public static MediaTransferApplication getInstance() {
        return sApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sApp = this;

        mPermissionHelper = new PermissionHelper(getApplicationContext());
        if (mPermissionHelper.isWriteExternalStoragePermissionGranted()) {
            onWriteExtStoragePermissionGranted(getApplicationContext());
        }

        Log.d(this, "onCreate createTime:" + SystemClock.elapsedRealtime());

        MediaTransferService.startService(getApplicationContext(), true);

        // TODO: to add monitor process/job schedule to keep this process keep alive?
    }

    public void onActivityResumed(Activity activity) {
        Log.d(this, "onActivityResumed, activityName:"
                + activity.getClass().getSimpleName());

        if (activity != null
                && !mResumedActivity.contains(activity)) {
            mResumedActivity.add(activity);
        }

        Log.d(this, "onActivityResumed, mAppPaused:" + mAppPaused);

        if (mAppPaused) {
            ClientManager clientManager = ClientManager.getInstance(
                    getApplicationContext());

            clientManager.startSearchHost(null);
        }

        mHandler.removeMessages(EVENT_ACTIVITY_PAUSED);
        mAppPaused = false;
        mAppQuit = false;
    }

    public void onActivityPaused(Activity activity) {
        Log.d(this, "onActivityPaused, activityName:"
                + activity.getClass().getSimpleName());
        mResumedActivity.remove(activity);

        if (!mAppQuit) {
            mHandler.sendEmptyMessageDelayed(EVENT_ACTIVITY_PAUSED, APP_PAUSED_CHECK_TIMESTAMP);
        }
    }

    private void handleActivityPaused() {
        if (mResumedActivity.size() == 0) {
            Log.d(this, "handleActivityPaused, app paused");
            mAppPaused = true;
        }
    }

    public boolean isQuited() {
        return mAppQuit;
    }

    public void quit() {
        quit(true);
    }

    // only designed to be used by MediaTransferService
    public void quit(boolean stopService) {
        ClientManager clientManager = ClientManager.getInstance(
                getApplicationContext());

        mAppQuit = true;

        ClientsSearchHandler.getInstance(getApplicationContext())
                .stopSearch(ClientsSearchHandler.SERVER_SEARCH_CANCELED);

        if (stopService) {
            MediaTransferService.startServiceForQuit(getApplicationContext());
        }

        for(Activity activity : mResumedActivity) {
            activity.finish();
        }

        clientManager.disconnectAllConnections();
    }

    public void onWriteExtStoragePermissionGranted(Context context) {
        String path;
        if (Environment.MEDIA_MOUNTED.equals(Environment.MEDIA_MOUNTED)
                || !Environment.isExternalStorageRemovable()) {//如果外部储存可用
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            path = context.getFilesDir().getPath();
        }

        path = path + "/" + TEMP_FILE_DIR;

        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();//创建父路径

            Log.d(TAG, "Temp file path:" + path
                    + ", existed:" + file.exists());
        }

        sTempFileDir = path;

        // init log save path folder.
        Log.init(path, context);
    }

    public String getTempFileDir() {
        return sTempFileDir;
    }
}
