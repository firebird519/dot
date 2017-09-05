package com.assistant.ui.permissiongrant;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

/**
 * Created by liyong on 17-9-5.
 */

public class PermissionHelper {
    // 所需的全部权限
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

    public boolean isWriteExternalStrogagePermissionGranted() {
        return !mPermissionsChecker.lacksPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,});
    }

    public void startPermissionsActivity(Activity activity, int requestCode) {
        PermissionsActivity.startActivityForResult(
                activity, requestCode, PermissionHelper.PERMISSIONS);
    }
}
