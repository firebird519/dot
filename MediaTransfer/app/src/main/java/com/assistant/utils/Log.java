package com.assistant.utils;

/**
 * Created by alex on 17-8-5.
 */

public class Log {
    public static void log(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }

    public static void logd(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }

    public static void logd(Object object, String msg) {
        android.util.Log.d(object.getClass().getName(), msg);
    }
}
