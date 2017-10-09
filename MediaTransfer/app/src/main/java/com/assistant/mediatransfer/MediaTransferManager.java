package com.assistant.mediatransfer;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.assistant.connection.ConnectionManager;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.events.ClientInfo;
import com.assistant.utils.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.UUID;


public class MediaTransferManager {
    private static MediaTransferManager sInstance;

    public static MediaTransferManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new MediaTransferManager(context);
        }

        return sInstance;
    }

    private Context mContext;

    // IMPORTANT: careful to change to avoid can not listen or connect to other client.
    private int mPort = ConnectionManager.DEFAULT_PORT;

    private ClientInfo mClientInfo;

    private SharePreferencesHelper mSharePreferencesHelper;

    private MediaTransferManager(Context context) {
        mContext = context;

        mSharePreferencesHelper = SharePreferencesHelper.getInstance(context);
        mPort = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_PORT,
                ConnectionManager.DEFAULT_PORT);

        generateClientInfo();

        ConnectionManager.getInstance(context).setPort(mPort);
    }

    public int getPort() {
        return mPort;
    }
    public void setPort(int port) {
        mPort = port;
        ConnectionManager.getInstance(mContext).setPort(mPort);
    }

    public boolean isNetworkSettingsOn() {
        boolean isOn = false;

        if (mSharePreferencesHelper != null) {
            isOn = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_NETWORK_ON, 1) == 1;
        }

        return isOn;
    }

    public ClientInfo getClientInfo() {
        try {
            return (ClientInfo)mClientInfo.clone();
        } catch (CloneNotSupportedException e) {
            Log.d(this, "getClientInfo CloneNotSupportedException:" + e.getMessage());
        }

        return null;

    }

    private void generateClientInfo() {
        String name = mSharePreferencesHelper.getString(SharePreferencesHelper.SP_KEY_CLIENT_NAME);

        if (TextUtils.isEmpty(name)) {
            name = Build.MODEL;

            if (TextUtils.isEmpty(name)) {
                String currentTime = String.valueOf(System.currentTimeMillis());

                name = "client" + currentTime.substring(currentTime.length() - 5);
            }

            mSharePreferencesHelper.save(SharePreferencesHelper.SP_KEY_CLIENT_NAME, name);
        }

        String uniqueId = mSharePreferencesHelper.getString(SharePreferencesHelper.SP_KEY_UNIQUE_ID);

        if (TextUtils.isEmpty(uniqueId)) {
            uniqueId = UUID.randomUUID().toString();

            mSharePreferencesHelper.save(SharePreferencesHelper.SP_KEY_UNIQUE_ID, uniqueId);
        }

        mClientInfo = new ClientInfo(name, uniqueId);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        try {
            writer.println("  MediaTransferManager:");

            writer.println("    ClientInfo:" + mClientInfo.toString());
            writer.println("    port:" + mPort);
            writer.flush();
        } catch (Exception e) {
            Log.d(this, "Exception happened when dump:" + e.getMessage());
        }
    }
}
