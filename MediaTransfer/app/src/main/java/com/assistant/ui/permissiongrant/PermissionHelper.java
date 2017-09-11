package com.assistant.ui.permissiongrant;

import android.Manifest;
import android.app.Activity;
import android.content.Context;


public class PermissionHelper {
    public static final String[] PERMISSIONS = new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    Context mContext;

    PermissionsChecker mPermissionsChecker;
    public PermissionHelper(Context context) {
        mContext = context;

        mPermissionsChecker = new PermissionsChecker(context);
    }

    public boolean isAllPermissionGranted() {
        return !mPermissionsChecker.lacksPermissions(PERMISSIONS);
    }

    public boolean isWriteExternalStoragePermissionGranted() {
        return !mPermissionsChecker.lacksPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,});
    }

    public void startPermissionsActivity(Activity activity, int requestCode) {
        PermissionsActivity.startActivityForResult(
                activity, requestCode, PermissionHelper.PERMISSIONS);
    }
}
