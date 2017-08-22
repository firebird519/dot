package com.assistant.mediatransfer;

import android.content.Context;
import android.text.TextUtils;

import com.assistant.connection.ConnectionManager;

/**
 * Created by alex on 17-8-21.
 */

public class MediaTransferManager {
    private static MediaTransferManager sInstance;
    public static MediaTransferManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new MediaTransferManager(context);
        }

        return sInstance;
    }


    private Context mContext;
    private ConnectionManager mConnectionManager;

    private int mDefaultPort = 8989;

    MediaTransferManager(Context context) {
        mContext = context;

        mConnectionManager = ConnectionManager.getInstance(context);
    }

    public void startSearchHost(ConnectionManager.SearchListener listener) {
        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(mContext);
        String ip = networkInfoManager.getWifiIpAddressString();

        if (TextUtils.isEmpty(ip) && networkInfoManager.isWifiConnected()) {
            mConnectionManager.searchHost(ip, mDefaultPort, listener);
        } else {
            listener.onSearchCompleted();
        }
    }
}
