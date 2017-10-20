package com.demo.utils;

/**
 * Created by liyong on 17-7-14.
 */

public class Log {
    private static final boolean DEBUG = false;
    public static void d(String tag, String text) {
        if (DEBUG) {
            android.util.Log.d(tag, text);
        }
    }
}
