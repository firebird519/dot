package com.assistant.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;

public class Utils {
    public static final String DCIM = "DCIM";
    private static final boolean DEBUG = true;

    public static int byteToInt(byte b) {
        int i = b & 0xff;

        return i;
    }

    public static boolean isCharging(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        return isCharging;
    }


    /* Return level of the battery which is between 0 to 100.*/
    public static int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float) scale;

        return (int) (batteryPct * 100);
    }

    public static String[] getRootPath(Context context) {

        try {
            StorageManager sm = (StorageManager) context
                    .getSystemService(Context.STORAGE_SERVICE);

            return (String[]) sm.getClass()
                    .getMethod("getVolumePaths", null).invoke(sm, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getAppStoragePath(Context context) {
        String path = "";
        File file = context.getFilesDir();

        if (file != null) {
            path = file.getAbsolutePath();
        }

        return path;
    }

}