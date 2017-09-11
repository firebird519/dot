package com.assistant;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;

import com.assistant.ui.permissiongrant.PermissionHelper;
import com.assistant.utils.Log;

import java.io.File;

public class MediaTransferApplication extends Application {
    private static final String TAG = "MediaTransferApplication";

    public static final String TEMP_FILE_DIR = "mediatransfer";

    private static String sTempFileDir = "";

    private PermissionHelper mPermissionHelper;
    @Override
    public void onCreate() {
        super.onCreate();

        mPermissionHelper = new PermissionHelper(getApplicationContext());
        if (mPermissionHelper.isWriteExternalStoragePermissionGranted()) {
            writeExternalStoragePermissionGranted(getApplicationContext());
        }

        Log.d(this, "onCreate createTime:" + SystemClock.elapsedRealtime());

        MediaTransferService.startService(getApplicationContext(), true);

        // TODO: to add monitor process/job schedule to keep this process keep alive?
    }

    public static void writeExternalStoragePermissionGranted(Context context) {
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

    public static String getTempFileDir() {
        return sTempFileDir;
    }
}
