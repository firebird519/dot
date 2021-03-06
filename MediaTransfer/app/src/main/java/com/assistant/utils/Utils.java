package com.assistant.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.widget.Toast;

import com.assistant.MediaTransferApplication;
import com.assistant.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final String TAG = "Utils";

    //TODO: change to false for official release.
    public static final boolean DEBUG = true;
    public static final boolean DEBUG_CONNECT_SELF = true;
    public static final boolean DEBUG_NO_SEARCH = true;

    public static final String DCIM = "DCIM";

    private static final String IP_REXP =
            "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";

    public static int byteToInt(byte b) {
        return b & 0xff;
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
                    .getMethod("getVolumePaths", (Class<?>)null).invoke(sm, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getAppStoragePath(Context context) {
        String path = MediaTransferApplication.getInstance().getTempFileDir();

        if (TextUtils.isEmpty(path)) {
            File file = context.getFilesDir();

            if (file != null) {
                path = file.getAbsolutePath();
            }
        }

        return path;
    }

    public static boolean isIpPattern(String address) {
        if (address.length() < 7 || address.length() > 15 || "".equals(address)) {
            return false;
        }

        Pattern pat = Pattern.compile(IP_REXP);

        Matcher mat = pat.matcher(address);

        return mat.find();
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        if (bytes != null) {
            int index = 0;
            for(byte b: bytes) {
                if (index%20 == 0) {
                    builder.append("\n        ");
                }
                builder.append(String.format("%02X ", b));
                index ++;
            }
        }

        return builder.toString();
    }

    public static PowerManager.WakeLock createBackGroundWakeLock(Context context, String name) {
        PowerManager pm = (PowerManager)context.getSystemService(
                Context.POWER_SERVICE);
        return pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                name);
    }

    public static boolean fileSaveAs(String destPath,
                                     String destFileName, String souceFilePathName) {
        boolean success = false;

        if (!destPath.endsWith("/")) {
            destPath += "/";
        }

        File destFile = new File(destPath);
        if (!(destFile.exists() && destFile.isDirectory())) {
            Log.d(TAG , "fileSaveAs, dest path invalid:" + destPath);
            return success;
        }

        destFile = new File(destPath + destFileName);

        if (destFile.exists()) {
            for (int i = 0; ; i++) {
                destFile = new File(destPath
                        + i + "_" + destFileName);

                if (!destFile.exists()) {
                    break;
                }
            }
        }

        File sourceFile = new File(souceFilePathName);
        if (sourceFile.exists()) {
            try {
                destFile.createNewFile();

                FileInputStream input = new FileInputStream(sourceFile);
                FileOutputStream output = new FileOutputStream(destFile);
                byte[] b = new byte[1024 * 5];
                int len;
                while ((len = input.read(b)) != -1) {
                    output.write(b, 0, len);
                }
                output.flush();
                output.close();
                input.close();

                success = true;
            } catch (Exception e) {
                destFile.delete();
            }
        }

        return success;
    }

}