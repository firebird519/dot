package com.assistant.mediatransfer;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.ConnectionManager;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.mediatransfer.events.ChatMessageEvent;
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.utils.Log;

import java.util.UUID;

public class MediaTransferManager {
    private static MediaTransferManager sInstance;
    public static MediaTransferManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new MediaTransferManager(context);
        }

        return sInstance;
    }

    public interface MediaTransferListener {
        void onClientConnected(int id, ClientInfo info);
        void onChatMsgComing(int clientId, ChatMessageEvent msg);
        void onChatMsgSendingResult(int clientId, int msgId, boolean isSuccess);
    }

    private Context mContext;
    private ConnectionManager mConnectionManager;

    // IMPORTANT: careful to change to avoid can not listen or connect to other client.
    private int mPort = ConnectionManager.DEFAULT_PORT;

    private ClientInfo mClientInfo;

    private NetEventHandler mNetCommandHelper;
    private SharePreferencesHelper mSharePreferencesHelper;

    private MThreadHandler mThreadHandler;

    class MThreadHandler extends Handler {
        public static final int EVENT_GENERATE_CLIENTINFO = 0;

        MThreadHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_GENERATE_CLIENTINFO:
                    generateClientInfo();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    private MediaTransferManager(Context context) {
        mContext = context;

        mSharePreferencesHelper = new SharePreferencesHelper(context);

        mConnectionManager = ConnectionManager.getInstance(context);

        mNetCommandHelper = new NetEventHandler(this, mConnectionManager);

        HandlerThread thread = new HandlerThread("MediaTransferManager");
        thread.start();
        mThreadHandler = new MThreadHandler(thread.getLooper());

        mThreadHandler.sendEmptyMessage(MThreadHandler.EVENT_GENERATE_CLIENTINFO);
    }


    public int getDefaultPort() {
        return mPort;
    }

    public void startSearchHost(ConnectionManager.SearchListener listener) {
        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(mContext);
        String ip = networkInfoManager.getWifiIpAddressString();

        Log.d(this, "startSearchHost, ip:" + ip +
                ", wifi connected:" + networkInfoManager.isWifiConnected());
        if (!TextUtils.isEmpty(ip) && networkInfoManager.isWifiConnected()) {
            mConnectionManager.searchHost(ip, mPort, listener);
        } else {
            listener.onSearchCompleted();
        }
    }

    public void reset() {
        // TODO:
    }

    public ClientInfo getClientInfo() {
        return mClientInfo;
    }

    private void generateClientInfo() {
        String name = mSharePreferencesHelper.getString(SharePreferencesHelper.SP_KEY_CLIENT_NAME);

        if (TextUtils.isEmpty(name)) {
            name = android.os.Build.PRODUCT;

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
}
