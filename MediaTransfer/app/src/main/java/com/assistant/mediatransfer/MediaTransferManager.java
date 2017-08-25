package com.assistant.mediatransfer;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.utils.Log;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        void onChatMsgComing(int clientId, ChatMessage msg);
        void onChatMsgSendingResult(int clientId, int msgId, boolean isSuccess);
    }

    private Context mContext;
    private ConnectionManager mConnectionManager;

    // IMPORTANT: careful to change to avoid can not listen or connect to other client.
    private int mPort = ConnectionManager.DEFAULT_PORT;

    private ClientInfo mClientInfo;

    private Gson mGson = new Gson();

    private NetCommandHelper mNetCommandHelper = new NetCommandHelper();
    private SharePreferencesHelper mSharePreferencesHelper;

    // key string is unique id of one connection.
    private Map<String, Object> mMsgCollections =
            Collections.synchronizedMap(new HashMap<String, Object>(10));

    // TODO: move to command helper.
    private ConnectionManager.ConnectionManagerListener mConnectionMgrListener =
            new ConnectionManager.ConnectionManagerListenerBase() {
        @Override
        public void onDataReceived(int id, String data, boolean isFile) {
            if (!isFile) {
                NetCommand cmd = mNetCommandHelper.parserNetCommand(data);

                switch (cmd.command) {
                    case NetCommand.COMMAND_CLIENT_INFO:
                        ClientInfo clientInfo = mNetCommandHelper.parserClientInfo(cmd.jsonData);

                        Connection connection = mConnectionManager.getConnection(id);
                        if (connection != null) {
                            connection.setConnData(clientInfo);
                            List<ChatMessage> mMsgArray =
                                    Collections.synchronizedList(new ArrayList<ChatMessage>());

                            mMsgCollections.put(clientInfo.uniqueId, mMsgArray);

                            // when connection connected, client send info to host side first.
                            // host side will send info back after received client info.
                            if (connection.isHost()) {
                                byte[] response =
                                        mNetCommandHelper.toNetCommandJson(mClientInfo);

                                mConnectionManager.sendData(id, response, response.length,
                                        new ConnectionManager.DataSendListener() {
                                    @Override
                                    public void onSendProgress(int percent) {}

                                    @Override
                                    public void onResult(int ret, int failedReason) {

                                    }
                                });
                            }
                        }
                        break;
                    case NetCommand.COMMAND_TEXT:
                        // notify to UI.
                        break;
                    case NetCommand.COMMAND_FILE:
                        break;
                }
            } else {
                // TODO: to be implemented
            }
        }
    };

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
        mConnectionManager.addListener(mConnectionMgrListener);

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
