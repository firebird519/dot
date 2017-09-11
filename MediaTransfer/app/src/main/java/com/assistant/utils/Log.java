package com.assistant.utils;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Log {
    private static String TAG = "Log";

    public static final boolean LOG_TO_FILE_FLAG = Utils.DEBUG_CONNECTION;

    private static final String DEBUG = "D";
    private static final String ERROR = "E";
    private static String sLogPath;

    private static String sLastLogFileName = "";

    private static SimpleDateFormat sDateFormat =
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    private static Date sDate = new Date();

    public static void init(String dir, Context context) {
        if (TextUtils.isEmpty(dir)) {
            dir = Log.getFilePath(context);
        }
        setLogPath(dir + "/assistant_logs");
    }
    /**
     * This function should be called once when this application start. Otherwise logs will not
     * write to file.
     *
     * @param path
     */
    private static void setLogPath(String path) {
        android.util.Log.d(TAG, "log path:" + path);
        if (TextUtils.isEmpty(sLogPath)) {
            sLogPath = path; // getFilePath(context) + "/Logs";//获得文件储存路径,在后面加"/Logs"建立子文件夹
        }
    }

    public static void log(String tag, String msg) {
        android.util.Log.d(tag, msg);
        writeToFile(DEBUG, tag, msg);
    }

    public static void d(String tag, String msg) {
        android.util.Log.d(tag, msg);
        writeToFile(DEBUG, tag, msg);
    }

    public static void d(Object object, String msg) {
        android.util.Log.d(object.getClass().getName(), msg);
        writeToFile(DEBUG, object.getClass().getName(), msg);
    }

    public static void e(String tag, String msg) {
        android.util.Log.e(tag, msg);
        writeToFile(ERROR, tag, msg);
    }

    public static void e(Object object, String msg) {
        android.util.Log.e(object.getClass().getName(), msg);
        writeToFile(ERROR, object.getClass().getName(), msg);
    }

    /**
     * 获得文件存储路径
     *
     * @return
     */
    public static String getFilePath(Context context) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.MEDIA_MOUNTED)
                || !Environment.isExternalStorageRemovable()) {//如果外部储存可用
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            return context.getFilesDir().getPath();
        }
    }

    private static void removeOldLogFiles(File dirFile) {
        if (dirFile != null && dirFile.isDirectory()) {
            SimpleDateFormat dateFormat =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String date = dateFormat.format(new Date());

            for(File file: dirFile.listFiles()) {
                if (!file.getName().contains(date)) {
                    if (file.delete()) {
                        android.util.Log.e(TAG, "deleted:" + file.getName());
                    } else {
                        android.util.Log.e(TAG, "deleted failed:" + file.getName());
                    }
                }
            }
        }
    }

    private static String getLogFileName() {
        if (TextUtils.isEmpty(sLastLogFileName)) {
            if (TextUtils.isEmpty(sLogPath)) {
                android.util.Log.e(TAG, "sLogPath == null ，sLogPath need init!");
                return "";
            }

            //如果父路径不存在
            File file = new File(sLogPath);
            if (!file.exists()) {
                file.mkdirs();//创建父路径

                android.util.Log.e(TAG, "sLogPath:" + sLogPath
                        + ", existed:" + file.exists());
            } else {
                removeOldLogFiles(file);
            }

            sLastLogFileName = sLogPath + "/log_" + sDateFormat.format(new Date()) + ".log";

            file = new File(sLastLogFileName);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                }catch (IOException e) {
                    android.util.Log.e(TAG, "sLastLogFileName:" + sLastLogFileName
                            + ", IOException:" + e.getMessage());
                }
            }
        }

        return sLastLogFileName;
    }

    /**
     * 将log信息写入文件中
     *
     * @param tag
     * @param msg
     */
    private static void writeToFile(String type, String tag, String msg) {
        if (!LOG_TO_FILE_FLAG) {
            return;
        }

        String fileName = getLogFileName();
        if (TextUtils.isEmpty(fileName)) {
            android.util.Log.d(TAG, "log filename is null");
            return;
        }

        //android.util.Log.d(TAG, "fileName:" + fileName);

        String log = sDateFormat.format(sDate) + " " + type + " " + tag + ": " + msg + "\n";

        FileOutputStream fos = null;
        BufferedWriter bw = null;
        try {
            fos = new FileOutputStream(fileName, true);
            bw = new BufferedWriter(new OutputStreamWriter(fos));
            bw.write(log);
            //android.util.Log.d(TAG, "log:" + log);

            bw.close();
            bw = null;

            if (fos != null) {
                fos.close();
                fos = null;
            }

        } catch (FileNotFoundException e) {
            android.util.Log.d(TAG, "fileName:" + fileName
                    + ", file not found:" + e.getMessage());
        } catch (IOException e) {
            android.util.Log.d(TAG, "fileName:" + fileName
                    + ", IOException:" + e.getMessage());
        } finally {
            try {
                if (bw != null) {
                    bw.close();//关闭缓冲流
                }

                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
