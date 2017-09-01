package com.assistant.mediatransfer;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.ConnectionCreationCallback;
import com.assistant.connection.ConnectionManager;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.utils.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        void onClientAvailable(int id, ClientInfo info);
        void onClientDisconnected(int id, int reason);
        void onMessageReceived(int clientId, Event msg);
        void onMessageSendResult(int clientId, int msgId, boolean isSuccess);
    }

    private Set<MediaTransferListener> mListeners =
            Collections.synchronizedSet(new HashSet<MediaTransferListener>());

    private Context mContext;
    private ConnectionManager mConnectionManager;

    private Set<Integer> mConnectionIds =
            Collections.synchronizedSet(new HashSet<Integer>());

    // IMPORTANT: careful to change to avoid can not listen or connect to other client.
    private int mPort = ConnectionManager.DEFAULT_PORT;

    private ClientInfo mClientInfo;

    private NetEventHandler mNetEventHandler;
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

        mSharePreferencesHelper = SharePreferencesHelper.getInstance(context);
        mConnectionManager = ConnectionManager.getInstance(context);

        mConnectionManager.addListener(new ConnectionManager.ConnectionManagerListener() {
            @Override
            public void onConnectionAdded(int id) {}

            @Override
            public void onConnectionRemoved(int id, int reason) {
                removeConnectId(id);

                notifyClientDisconnected(id, reason);
            }

            @Override
            public void onDataReceived(int id, String data, boolean isFile) {}
        });

        mNetEventHandler = new NetEventHandler(this, mConnectionManager);
        mNetEventHandler.addListener(new ConnectionEventListener() {
            @Override
            public void onClientAvailable(int connId, ClientInfo info) {
                addConnectionId(connId);
                notifyClientAvailable(connId, info);
            }

            @Override
            public void onEventReceived(int connId, Event event) {
                Log.d(this, "onEventReceived:" + event);
                notifyEventReceived(connId, event);
            }

            @Override
            public void onEventStateUpdated(int connId, Event event) {
            }
        });

        HandlerThread thread = new HandlerThread("MediaTransferManager");
        thread.start();
        mThreadHandler = new MThreadHandler(thread.getLooper());

        //mThreadHandler.sendEmptyMessage(MThreadHandler.EVENT_GENERATE_CLIENTINFO);

        generateClientInfo();
        mPort = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_PORT,
                ConnectionManager.DEFAULT_PORT);
    }

    public List<Event> getMessageList(int connId) {
        return mNetEventHandler.getMessageList(connId);
    }

    public int sendEvent(int connId, Event event) {
        if (event != null) {
            mNetEventHandler.sendEvent(connId, event);
        }

        return 0;
    }

    public int sendFile(int connId, String strFilePathName) {
        return 0;
    }

    public int getPort() {
        return mPort;
    }

    public Integer[] getConnectionIds() {
        Integer[] ret;
        synchronized (mConnectionIds) {
            Integer[] keyArray = new Integer[mConnectionIds.size()];
            ret = mConnectionIds.toArray(keyArray);
        }

        return ret;
    }

    private void addConnectionId(int connId) {
        synchronized (mConnectionIds) {
            mConnectionIds.add(connId);
        }
    }

    private void removeConnectId(int connId) {
        synchronized (mConnectionIds) {
            mConnectionIds.remove(connId);
        }
    }

    public void connectTo(String ipAddress, int port, ConnectionCreationCallback listener) {
        mConnectionManager.connectTo(ipAddress, port, listener);
    }

    public void startListen() {
        mConnectionManager.listen(getPort());
    }
    public void startSearchHost(ConnectionManager.SearchListener listener) {
        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(mContext);
        String ip = networkInfoManager.getWifiIpAddressString();

        Log.d(this, "startSearchHost, ip:" + ip +
                ", wifi connected:" + networkInfoManager.isWifiConnected());
        if (!TextUtils.isEmpty(ip) && networkInfoManager.isWifiConnected()) {
            if (!mConnectionManager.isHostSearching()) {
                mConnectionManager.searchHost(ip, mPort, listener);
            }
        } else {
            if (listener != null) {
                listener.onSearchCompleted();
            }
        }
    }

    public void stopAllConnections() {
        mConnectionManager.stopAllConnections();
    }

    public ClientInfo getClientInfo() {
        return mClientInfo;
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

    public void addListener(MediaTransferListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public void removeListener(MediaTransferListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    protected void notifyClientAvailable(int connId, ClientInfo info) {
        for (MediaTransferListener listener : mListeners) {
            listener.onClientAvailable(connId, info);
        }
    }

    protected void notifyClientDisconnected(int connId, int reason) {
        for (MediaTransferListener listener : mListeners) {
            listener.onClientDisconnected(connId, reason);
        }
    }

    protected void notifyEventReceived(int connId, Event event) {
        for (MediaTransferListener listener : mListeners) {
            listener.onMessageReceived(connId, event);
        }
    }

    protected void notifyMessageSendResult(int connId, int msgId, boolean isSuccess) {
        for (MediaTransferListener listener : mListeners) {
            listener.onMessageSendResult(connId, msgId, isSuccess);
        }
    }
}
