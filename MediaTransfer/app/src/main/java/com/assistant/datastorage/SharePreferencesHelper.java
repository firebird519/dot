package com.assistant.datastorage;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by liyong on 17-8-25.
 */

public class SharePreferencesHelper {
    private SharedPreferences mSharedPreferences;
    private static final String SP_NAME = "media_transfer";

    public static final String SP_KEY_UNIQUE_ID = "uniqueId";
    public static final String SP_KEY_PORT = "port";
    public static final String SP_KEY_CLIENT_NAME = "clientName";

    public SharePreferencesHelper(Context context) {
        mSharedPreferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void save(String key, String content) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        editor.putString(key, content);
        editor.commit();
    }

    public void save(String key, int content) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        editor.putInt(key, content);
        editor.commit();
    }

    public String getString(String key) {
        return mSharedPreferences.getString(key, "");
    }

    public int getInt(String key) {
        return mSharedPreferences.getInt(key, 0);
    }
}
