package com.assistant.mediatransfer;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionCreationCallback;
import com.assistant.connection.ConnectionManager;
import com.assistant.connection.EventSendRequest;
import com.assistant.connection.EventSendResponse;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.events.EventFactory;
import com.assistant.events.FileEvent;
import com.assistant.utils.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    // TODO: key string use unique id of one connection.
    private Map<Integer, Object> mMsgCollections =
            Collections.synchronizedMap(new HashMap<Integer, Object>(10));

    // IMPORTANT: careful to change to avoid can not listen or connect to other client.
    private int mPort = ConnectionManager.DEFAULT_PORT;

    private ClientInfo mClientInfo;

    private SharePreferencesHelper mSharePreferencesHelper;

    private ThreadHandler mThreadHandler;

    class ThreadHandler extends Handler {
        public static final int EVENT_GENERATE_CLIENTINFO = 0;
        public static final int EVENT_CONNECTION_ADDED = 1;
        public static final int EVENT_CONNECTION_REMOVED = 2;
        public static final int EVENT_CONNECTION_EVENT_RECEIVED = 3;
        public static final int EVENT_LISTEN = 4;
        public static final int EVENT_SEARCH_HOST = 5;

        ThreadHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_GENERATE_CLIENTINFO:
                    generateClientInfo();
                    break;
                case EVENT_CONNECTION_ADDED:
                    handleConnectionAdded(msg.arg1);
                    break;
                case EVENT_CONNECTION_REMOVED:
                    handleConnectionClosed(msg.arg1, msg.arg2);
                    break;
                case EVENT_CONNECTION_EVENT_RECEIVED:
                    handleEventReceived(msg.arg1, (Event) msg.obj);
                    break;
                case EVENT_LISTEN:
                    handleListenEvent(msg.arg1);
                    break;
                case EVENT_SEARCH_HOST:
                    handleSearchHostEvent((ConnectionManager.SearchListener)msg.obj);
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
            public void onConnectionAdded(int id) {
                mThreadHandler
                        .obtainMessage(ThreadHandler.EVENT_CONNECTION_ADDED, id, 0)
                        .sendToTarget();
            }

            @Override
            public void onConnectionRemoved(int id, int reason) {
                mThreadHandler
                        .obtainMessage(ThreadHandler.EVENT_CONNECTION_REMOVED, id, reason)
                        .sendToTarget();
            }

            @Override
            public void onEventReceived(int id, Event event) {
                mThreadHandler
                        .obtainMessage(
                                ThreadHandler.EVENT_CONNECTION_EVENT_RECEIVED, id, 0, event)
                        .sendToTarget();
            }
        });

        HandlerThread thread = new HandlerThread("MediaTransferManager");
        thread.start();
        mThreadHandler = new ThreadHandler(thread.getLooper());

        //mThreadHandler.sendEmptyMessage(ThreadHandler.EVENT_GENERATE_CLIENTINFO);

        generateClientInfo();
        mPort = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_PORT,
                ConnectionManager.DEFAULT_PORT);
    }

    public List<Event> getMessageList(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        if (connection != null) {
            ClientInfo clientInfo = (ClientInfo) connection.getConnData();

            Log.d(this, "getMessageList, get msg list for connId:" + connId);
            if (clientInfo != null) {
                return (List<Event>)mMsgCollections.get(connId);
            } else {
                Log.d(this, "getMessageList, clientInfo not received for id:" + connId);
            }
        } else {
            Log.d(this, "getMessageList, connection not avaible for id:" + connId);
        }

        return null;
    }

    public int sendEvent(int connId, Event event, EventSendResponse response) {
        if (event != null) {
            EventSendRequest request = new EventSendRequest(event, response);
            recordEvent(connId, event);

            Log.d(this, "sendEvent, connId:" + event.connId + ", event:" + event.getEventClassName());
            mConnectionManager.sendEvent(request);
        }

        return 0;
    }

    public void sendEvent(EventSendRequest request) {
        mConnectionManager.sendEvent(request);
    }

    public int sendFile(int connId, String filePathName,  EventSendResponse response) {
        FileEvent event = EventFactory.generateFileEvent(connId, filePathName);

        if (event != null) {
            EventSendRequest request = new EventSendRequest(event, response);
            recordEvent(connId, event);

            mConnectionManager.sendEvent(request);

            return 0;
        }

        Log.d(this, "sendFile, generateFileEvent failed!");

        return -1;
    }

    public int sendFile(int connId, String strFilePathName) {
        return 0;
    }

    public int getPort() {
        return mPort;
    }

    public boolean isClientAvailable(int connId) {
        synchronized (mConnectionIds) {
            return mConnectionIds.contains(connId);
        }
    }

    public List<Integer> getConnectionIds() {
        List<Integer> connIdList;
        synchronized (mConnectionIds) {
            connIdList =
                    Collections.synchronizedList(new ArrayList<Integer>(mConnectionIds.size()));
            connIdList.addAll(mConnectionIds);
        }

        return connIdList;
    }

    private void handleConnectionAdded(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        Log.d(this, "handleConnectionAdded, connId:" + connId);
        if (connection != null && !connection.isHost()) {
            sendClientInfoEvent(connId);
        }
    }

    private void sendClientInfoEvent(int connId) {
        Log.d(this, "sendClientInfoEvent, connId:" + connId);
        ClientInfo clientInfoEvent = getClientInfo();

        clientInfoEvent.setConnId(connId);
        clientInfoEvent.setEventCreateTime(System.currentTimeMillis());

        sendEvent(connId, clientInfoEvent, new EventSendResponse() {
            @Override
            public void onSendProgress(long eventId, int percent) {}

            @Override
            public void onResult(int connId, long eventId, int result, int failedReason) {
                if (result == EventSendResponse.RESULT_FAILED) {
                    Connection connection = mConnectionManager.getConnection(connId);

                    if (connection != null) {
                        Log.d(this,
                                "ClientInfo Event send failed for connection:" + connId);
                        connection.close(Connection.CONNECTION_REASON_CODE_IO_EXCEPTION);
                    }
                }
            }
        });
    }

    private void handleConnectionClosed(int connId, int closeReason) {
        removeConnectId(connId);

        notifyClientDisconnected(connId, closeReason);
    }

    private void handleEventReceived(int connId, Event event) {
        Log.d(this, "handleEventReceived, connId:" + connId + ", event:" + event.toString());
        switch (event.getEventType()) {
            case Event.EVENT_TYPE_CLIENTNAME:
                handleClientInfoEvent(connId, (ClientInfo)event);
                break;
            case Event.EVENT_TYPE_FILE:
            case Event.EVENT_TYPE_CHAT:
                recordEvent(connId, event);
                notifyEventReceived(connId, event);
                break;

            default:
                break;
        }
    }

    private void handleClientInfoEvent(int connId, ClientInfo clientInfo) {
        Connection connection = mConnectionManager.getConnection(connId);
        if (connection != null) {
            Log.d(this, "handleClientInfoEvent, received ClientInfo, connId:" + connection.getId()
                    + ", client size:" + mConnectionIds.size());
            connection.setConnData(clientInfo);

            if (mMsgCollections.get(connId) == null) {
                List<Event> mMsgArray =
                        Collections.synchronizedList(new ArrayList<Event>());

                Log.d(this, "handleClientInfoEvent, create msg list for uid:" + clientInfo.clientUniqueId);
                mMsgCollections.put(connId, mMsgArray);
            }
            addConnectionId(connId);

            // when connection connected, client send info to host side first.
            // host side will send info back after received client info.
            if (connection.isHost()) {
                Log.d(this, "handleClientInfoEvent, response ClientInfo");
                sendClientInfoEvent(connId);
            }

            notifyClientAvailable(connId, clientInfo);
        }
    }

    void recordEvent(int connId, Event event){
        if (event instanceof ChatMessageEvent
                || event instanceof FileEvent) {
            Log.d(this, "recordEvent, record event for connId:" + connId);
            List<Event> msgList = getMessageList(connId);

            if (msgList != null) {
                msgList.add(event);
            } else {
                Log.d(this, "recordEvent, ChatMessageEvent message list not found!");
            }
        } else {
            Log.d(this, "recordEvent, event not recorded:" + event.getEventClassName());
        }
    }

    private void addConnectionId(int connId) {
        synchronized (mConnectionIds) {
            Log.d(this, "addConnectionId:" + connId);
            if (!mConnectionIds.contains(Integer.valueOf(connId))) {
                mConnectionIds.add(Integer.valueOf(connId));
            }
        }

        logConnectionIds();
    }

    private void removeConnectId(int connId) {
        synchronized (mConnectionIds) {
            mConnectionIds.remove(Integer.valueOf(connId));
        }

        logConnectionIds();
    }

    private void logConnectionIds() {
        StringBuilder builder = new StringBuilder();

        synchronized (mConnectionIds) {
            if (mConnectionIds.size() > 0) {
                for (Integer id : mConnectionIds) {
                    builder.append(String.valueOf(id) + " ");
                }
            } else {
                builder.append("No connection ids!");
            }
        }

        Log.d(this, "mConnectionIds:" + builder.toString());
    }

    public boolean isNetworkSettingsOn() {
        boolean isOn = false;

        if (mSharePreferencesHelper != null) {
            isOn = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_NETWORK_ON, 1) == 1;
        }

        return isOn;
    }

    public void connectTo(String ipAddress, int port, ConnectionCreationCallback listener) {
        mConnectionManager.connectTo(ipAddress, port, listener);
    }

    public void startListen() {
        mThreadHandler.obtainMessage(ThreadHandler.EVENT_LISTEN,getPort(),0).sendToTarget();
    }

    public void startSearchHost(ConnectionManager.SearchListener listener) {
        mThreadHandler.obtainMessage(ThreadHandler.EVENT_SEARCH_HOST,listener).sendToTarget();
    }

    private void handleListenEvent(int port) {
        mConnectionManager.listen(port);
    }

    private void handleSearchHostEvent(ConnectionManager.SearchListener listener) {
        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(mContext);
        String ip = networkInfoManager.getWifiIpAddressString();

        Log.d(this, "startSearchHost, ip:" + ip
                + ", wifi connected:" + networkInfoManager.isWifiConnected()
                + ", listener:" + listener);
        if (!TextUtils.isEmpty(ip) && networkInfoManager.isWifiConnected() && isNetworkSettingsOn()) {
            mConnectionManager.searchHost(ip, mPort, listener);
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
