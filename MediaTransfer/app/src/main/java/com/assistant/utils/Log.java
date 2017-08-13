package com.assistant.utils;

/**
 * Created by alex on 17-8-5.
 */

public class Log {
    public static void log(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }

    public static void d(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }

    public static void d(Object object, String msg) {
        android.util.Log.d(object.getClass().getName(), msg);
    }
}
