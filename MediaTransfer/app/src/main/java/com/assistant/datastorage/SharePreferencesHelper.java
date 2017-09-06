package com.assistant.datastorage;

import android.content.Context;
import android.content.SharedPreferences;

public class SharePreferencesHelper {
    private SharedPreferences mSharedPreferences;
    private static final String SP_NAME = "media_transfer";

    public static final String SP_KEY_UNIQUE_ID = "clientUniqueId";
    public static final String SP_KEY_PORT = "port";
    public static final String SP_KEY_CLIENT_NAME = "clientName";
    public static final String SP_KEY_NETWORK_ON = "networkOn";

    private static SharePreferencesHelper sSharePreferencesHelper = null;
    public static SharePreferencesHelper getInstance(Context context) {
        if (sSharePreferencesHelper == null) {
            sSharePreferencesHelper = new SharePreferencesHelper(context);
        }

        return sSharePreferencesHelper;
    }

    private SharePreferencesHelper(Context context) {
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

    public int getInt(String key, int defaultValue) {
        return mSharedPreferences.getInt(key, defaultValue);
    }
}
