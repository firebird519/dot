package com.assistant.connection;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.utils.Log;
import com.assistant.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.assistant.connection.HostSearchHandler.SERVER_SEARCH_CANCELED;

/**
 *
 * Attention:
 * 1. current don't support big json content which bigger than 64k.
 * 2. for file transfer, if file size is smaller than 64k, then notify file data as ByteString.
 *    Otherwise saved file data to one temporary file and notify temporary file path name.
 *
 */

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";

    public static int DATA_HEADER_LEN_v1 = 34;
    public static int DATA_HEADER_V1 = 1;

    public static final int DEFAULT_PORT = 8671;

    public static int MAX_RECONNECT_COUNT = 3;
    public static final int RECONNECT_DELAY_TIMESTAMP = 2000; // 5s

    private Context mContext;

    public interface ConnectionManagerListener {
        void onConnectionAdded(int id);
        void onConnectionRemoved(int id, int reason);

        void onEventReceived(int id, Event event);
    }

    public static class ConnectRequest {
        String ipAddress;
        int port;
        ConnectionCreationCallback listener;
        int retryCount;
        int connId;
        int disconnectReason;

        ConnectRequest(int id, String ip, int connPort, int reason,
                       int retryTimes, ConnectionCreationCallback cb) {
            connId = id;
            ipAddress = ip;
            port = connPort;
            retryCount = retryTimes;
            listener = cb;
            disconnectReason = reason;
        }

        public String toString() {
            return "[ConnectRequest] - connId:" + connId
                    + " ip:" + ipAddress
                    + ", port:" + port
                    + ", disconnect reason:" + disconnectReason
                    + ", retryTimes:" + retryCount
                    + ", listener:" + listener;
        }
    }

    public static class ConnectionManagerListenerBase implements ConnectionManagerListener {
        @Override
        public void onConnectionAdded(int id) {
            Log.d(this, "ConnectionManagerListenerBase.onConnectionAdded");
        }

        @Override
        public void onConnectionRemoved(int id, int reason) {
            Log.d(this, "ConnectionManagerListenerBase.onConnectionRemoved");
        }

        @Override
        public void onEventReceived(int id, Event event) {
            Log.d(this, "ConnectionManagerListenerBase.onEventReceived");
        }
    }

    private class ConnectionListenerImpl implements Connection.ConnectionListener {
        @Override
        public void onConnected(Connection conn) {
            mThreadHandler.obtainMessage(EVENT_CONNECTION_ADDED, conn).sendToTarget();

            Log.d(TAG, "connection connected for ip:" + conn.getIp());
        }

        @Override
        public void onConnectFailed(Connection conn, int reasonCode) {
            Log.d(TAG, "connection connected failed for ip:" + conn.getIp()
                    + ", reason:" + reasonCode);
            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_CONNECT_FAILED,
                            reasonCode, conn.getReconnectRequest() != null ? 1 : 0, conn)
                    .sendToTarget();
        }

        @Override
        public void onClosed(Connection conn, int reasonCode) {
            Log.d(TAG, "connection closed for ip:" + conn.getIp() + ", id:" + conn.getId()
                    + ", reason:" + reasonCode);

            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_CLOSED,
                            reasonCode, conn.getReconnectRequest() != null ? 1 : 0, conn)
                    .sendToTarget();

            conn.removeListener(this);
        }
    }

    private List<ConnectionManagerListener> mListeners =
            new CopyOnWriteArrayList<ConnectionManagerListener>();

    private final Map<Integer, Connection> mConnections =
            Collections.synchronizedMap(new HashMap<Integer, Connection>());

    private List<HostConnection> mHostConnList =
            Collections.synchronizedList(new ArrayList<HostConnection>());

    private List<ConnectRequest> mReconnectRequestList =
            Collections.synchronizedList(new ArrayList<ConnectRequest>());

    private static int sConnIdBase = -1;

    private ConnMgrThreadHandler mThreadHandler;
    private ConnectionDataTracker mDataTracker;

    private boolean mStopped = false;

    private boolean mReconnectFlag = true;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_CLOSED = 1;
    private static final int EVENT_CONNECTION_CONNECT_FAILED = 2;
    private static final int EVENT_CONNECTION_CREATION_TIMEOUT = 3;
    private static final int EVENT_HOSTCONNECTION_CLOSED = 4;
    private static final int EVENT_CONNECTION_CONNECT = 5;
    private static final int EVENT_NOTIFY_CONNECTION_ADDED = 6;
    private static final int EVENT_NOTIFY_CONNECTION_REMOVED = 7;

    private static final int CONNECTION_CREATION_TIMEOUT_TIMESTAMP = 70*1000; //70s
    class ConnMgrThreadHandler extends Handler {
        public ConnMgrThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CONNECTION_ADDED:
                    handleConnectionAdded((Connection) msg.obj);
                    break;
                case EVENT_CONNECTION_CONNECT_FAILED:
                    handleConnectionRemoved((Connection)msg.obj,
                            msg.arg1, msg.arg2 == 1);
                    break;
                case EVENT_NOTIFY_CONNECTION_ADDED:
                    // TODO: add reconnect flag...
                    notifyConnectionAdded(msg.arg1);
                    break;
                case EVENT_NOTIFY_CONNECTION_REMOVED:
                    notifyConnectionRemoved(msg.arg1, msg.arg2);
                    break;
                case EVENT_CONNECTION_CLOSED:
                    handleConnectionRemoved((Connection)msg.obj,
                            msg.arg1, msg.arg2 == 1);
                    break;
                case EVENT_CONNECTION_CREATION_TIMEOUT:
                    handleConnectionCreationTimeoutEvent((Connection) msg.obj);
                    break;
                case EVENT_HOSTCONNECTION_CLOSED:
                    handleHostConnectionClosed((HostConnection) msg.obj, msg.arg1);
                    break;
                case EVENT_CONNECTION_CONNECT:
                    handleConnectRequest((ConnectRequest) msg.obj);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    private static ConnectionManager sInstance;
    public static ConnectionManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ConnectionManager(context);
        }

        return sInstance;
    }

    private ConnectionManager(Context context) {
        mContext = context;

        HandlerThread thread = new HandlerThread("connectionManager");
        thread.start();
        mThreadHandler = new ConnMgrThreadHandler(thread.getLooper());

        HandlerThread trackerHandler = new HandlerThread("ConnectionDataTracker");
        trackerHandler.start();

        mDataTracker = new ConnectionDataTracker(mContext,
                this,
                trackerHandler.getLooper());

        mDataTracker.addListener(new ConnectionDataTracker.DataTrackerListener() {
            @Override
            public void onEventReceived(int connId, Event event) {
                notifyEventReceived(connId, event);
            }
        });
    }

    public void handleConnectionAdded(final Connection connection) {
        Log.d(TAG, "handleConnectionAdded, ip:" + connection.getIp());
        logConnectionList();

        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
                String ipAddress = connection.getIp();
                removeConnectRequest(ipAddress);

                if (isIpConnected(ipAddress) && !(Utils.DEBUG_CONNECTION && mConnections.size() <= 2)) {
                    Log.d(this, "handleConnectionAdded: ipAddress already connected!");
                    connection.close(Connection.CONNECTION_REASON_CODE_CONNECT_ALREADY_CONNECTED);
                    return;
                }

                boolean reConnected = false;

                // this connection maybe reconnected and connection id already existd
                if (connection.getId() < 0) {
                    connection.setId(generateConnectionId());
                } else {
                    reConnected = true;
                }

                connection.setWakeLock(Utils.createBackGroundWakeLock(mContext,
                                String.valueOf(connection.getId()) + ":send"),
                        Utils.createBackGroundWakeLock(mContext,
                                String.valueOf(connection.getId()) + ":receive"));

                mConnections.put(connection.getId(), connection);
                mDataTracker.onConnectionAdded(connection);
                connection.startHeartBeat();

                connection.addListner(new ConnectionListenerImpl());

                mThreadHandler
                        .obtainMessage(EVENT_NOTIFY_CONNECTION_ADDED, connection.getId(),
                                reConnected ? 1 : 0)
                        .sendToTarget();

                Log.d(TAG, "handleConnectionAdded, ip added:" + connection.getIp());

                logConnectionList();
            } else {
                Log.d(TAG, "handleConnectionAdded, connection already existed:" + connection.getIp());
            }
        }
    }

    private void handleConnectionRemoved(Connection connection, int reason, boolean reconnectFailed) {
        Log.d(TAG, "handleConnectionRemoved, id:" + connection.getId() + ", ip:"
                + connection.getIp() + ", reason:" + reason);
        logConnectReqeustList();

        logConnectionList();

        synchronized (mConnections) {
            if (mConnections.containsValue(connection)) {
                mConnections.remove(connection.getId());

                if (isReconnectAllowed(connection, reason)) {
                    Log.d(TAG, "handleConnectionRemoved, allowed to reconnect.");
                    tryReconnectTo(connection,
                            reason);
                } else {
                    removeConnectRequest(connection.getIp());

                    mDataTracker.onConnectionRemoved(connection);
                    mThreadHandler
                            .obtainMessage(EVENT_NOTIFY_CONNECTION_REMOVED, connection.getId(), reason)
                            .sendToTarget();
                }
            } else if (reconnectFailed) {
                removeConnectRequest(connection.getIp());

                // connection reconnect failed. notify ui and do connection removed for tracker.
                mDataTracker.onConnectionRemoved(connection);
                mThreadHandler
                        .obtainMessage(EVENT_NOTIFY_CONNECTION_REMOVED, connection.getId(), reason)
                        .sendToTarget();
            } else {
                Log.d(TAG, "handleConnectionRemoved, connection already removed.");
            }
        }
    }

    public Connection getConnection(int id) {
        synchronized (mConnections) {
            return mConnections.get(id);
        }
    }

    public ClientInfo getClientInfo(int connId) {
        Connection connection = getConnection(connId);

        if (connection != null) {
            return (ClientInfo) connection.getConnData();
        }

        return null;
    }

    public int getConnectionsCount() {
        return mConnections.size();
    }

    public Integer[] getConnectionIds() {
        Integer[] ret;
        synchronized (mConnections) {
            Set<Integer> keySet = mConnections.keySet();

            Integer[] keyArray = new Integer[keySet.size()];
            ret = keySet.toArray(keyArray);
        }

        return ret;
    }

    public boolean isIpConnected(String ip) {
        synchronized (mConnections) {
            for (Connection conn : mConnections.values()) {
                if (TextUtils.equals(conn.getIp(), ip)) {
                    Log.d(TAG, "isIpConnected:" + ip);
                    return true;
                }
            }
        }

        return false;
    }

    private Integer generateConnectionId() {
        synchronized (mConnections) {
            sConnIdBase += 1;
        }

        Log.d(TAG, "generateConnectionId:" + sConnIdBase);

        return sConnIdBase;
    }

    public void stopAllConnections() {
        Log.d(TAG, "disconnectAllConnections");
        mStopped = true;

        HostSearchHandler.getInstance(mContext).stopSearch(SERVER_SEARCH_CANCELED);

        synchronized (mHostConnList) {
            for(HostConnection hostConn: mHostConnList) {
                hostConn.close();
            }
        }

        synchronized (mConnections) {
            for(Connection conn : mConnections.values()) {
                conn.close();
            }
        }
    }

    public void addListener(ConnectionManagerListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ConnectionManagerListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyConnectionAdded(int connId) {
        Log.d(TAG, "notifyConnectionAdded:" + connId);
        for(ConnectionManagerListener listener : mListeners) {
            listener.onConnectionAdded(connId);
        }
    }

    private void notifyConnectionRemoved(int connId, int reason) {
        for(ConnectionManagerListener listener : mListeners) {
            listener.onConnectionRemoved(connId, reason);
        }
    }

    private void notifyEventReceived(int connId, Event event) {
        for(ConnectionManagerListener listener : mListeners) {
            listener.onEventReceived(connId, event);
        }
    }

    public void sendEvent(EventSendRequest request) {
        mDataTracker.sendEvent(request);
    }

    private void tryReconnectTo(Connection connection, int disconnectReason) {
        logConnectReqeustList();

        if (connection == null) {
            Log.d(TAG, "tryReconnectTo, connection should be not null here...");
            return;
        }

        ConnectRequest request = connection.getReconnectRequest();

        if (request == null && hasPendingConnectRequest(connection.getIp())) {
            Log.d(TAG, "tryReconnectTo, reconnect request existed, id:" + connection.getId()
                    + ", ip:" + connection.getIp());
            return;
        }

        if (request.retryCount == 0) {
            request.disconnectReason = disconnectReason;
        }

        Log.d(TAG, "tryReconnectTo, " + request.toString());

        request.retryCount ++;

        Message msg = mThreadHandler.obtainMessage(EVENT_CONNECTION_CONNECT, request);

        mThreadHandler.sendMessageDelayed(msg, RECONNECT_DELAY_TIMESTAMP);
    }

    private boolean isReconnectAllowed(Connection connection, int reason) {
        if (mReconnectFlag
                && !mStopped
                && connection != null
                && !TextUtils.isEmpty(connection.getIp())
                && connection.getReconnectCount() < MAX_RECONNECT_COUNT
                && !connection.isHost()
                && (reason == Connection.CONNECTION_REASON_CODE_IO_EXCEPTION)) {
            return true;
        }

        return false;
    }

    private void handleConnectRequest(ConnectRequest request) {
        Log.d(TAG, "handleConnectRequest, request:" + request);
        if (mStopped) {
            Log.d(TAG, "handleConnectRequest, mStopped is true");
            removeConnectRequest(request.ipAddress);

            notifyConnectionCreationResult(null,
                    request.listener,
                    true,
                    Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_CANCELED);
            return;
        }
        connectToInternal(request.ipAddress,
                request.port,
                request.listener,
                request);
    }

    public void connectTo(final String ipAddress, int port,
                          final ConnectionCreationCallback listener) {

        logConnectReqeustList();

        if (hasPendingConnectRequest(ipAddress)) {
            notifyConnectionCreationResult(null,
                    listener,
                    false,
                    Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_EXISTED);
            return;
        }
        ConnectRequest request = generateReconnectRequest(-1,
                ipAddress,
                port,
                0,
                listener);

        Log.d(TAG, "connectTo, " + request.toString());

        Message msg = mThreadHandler.obtainMessage(EVENT_CONNECTION_CONNECT, request);
        msg.sendToTarget();
        //connectToInternal(ipAddress, port, listener, null);
    }

    /*
     * @param connId: used for reconnect to specify id of new created connection
     */
    private void connectToInternal(final String ipAddress, int port,
                                   final ConnectionCreationCallback listener,
                                   final ConnectRequest request) {
        // cancel stop flag when one connectToInternal requst come
        mStopped = false;
        logConnectionList();

        if (isIpConnected(ipAddress) && !(Utils.DEBUG_CONNECTION && mConnections.size() < 2)) {
            if (request != null) {
                removeConnectRequest(ipAddress);
            }

            notifyConnectionCreationResult(null,
                    listener,
                    true,
                    Connection.CONNECTION_REASON_CODE_CONNECT_ALREADY_CONNECTED);
            return;
        }

        if (request == null && hasPendingConnectRequest(ipAddress)) {
            Log.d(TAG, "connectToInternal, one reconnect request existed for id:" + ipAddress);

            notifyConnectionCreationResult(null,
                    listener,
                    false,
                    Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_EXISTED);
            return;
        }

        Log.d(TAG, "connectToInternal ip:" + ipAddress + ", request:" + request);

        Connection connection =
                ConnectionFactory.createConnection(ipAddress,
                        port,
                        request,
                        new Connection.ConnectionListener() {
                    @Override
                    public void onConnected(Connection conn) {
                        Log.d(TAG, "connectToInternal, onConnected for " + conn.getIp()
                                + ", ipAddress:" + ipAddress + ", mStopped:" + mStopped);
                        onConnectionCreationSuccess(conn, listener);
                        conn.removeListener(this);
                    }

                    @Override
                    public void onConnectFailed(Connection conn, int reasonCode) {
                        Log.d(TAG, "connectToInternal, onConnectFailed for " + conn.getIp()
                                + ", ipAddress:" + ipAddress);
                        onConnectionCreationFailed(conn, reasonCode, listener);
                        conn.removeListener(this);
                    }

                    @Override
                    public void onClosed(Connection conn, int reasonCode) {
                        Log.d(TAG, "connectToInternal, onClosed for " + conn.getIp()
                                + ", ipAddress:" + ipAddress);
                        onConnectionCreationFailed(conn, reasonCode, listener);
                        conn.removeListener(this);
                    }
                });

        if (connection != null) {
            connection.setConnectRequest(request);
        } else {
            Log.e(TAG, "new connection failed...");
        }

        if (listener != null) {
            Message msg = mThreadHandler.obtainMessage(EVENT_CONNECTION_CREATION_TIMEOUT, connection);
            mThreadHandler.sendMessageDelayed(msg, CONNECTION_CREATION_TIMEOUT_TIMESTAMP);
        }
    }

    private ConnectRequest generateReconnectRequest(int connId,
                                                    String ipAddress,
                                                    int port,
                                                    int disconnectReason,
                                                    ConnectionCreationCallback listener) {
        ConnectRequest request;
        synchronized (mReconnectRequestList) {
            request = new ConnectRequest(connId, ipAddress,
                    port, disconnectReason, 0, listener);

            mReconnectRequestList.add(request);
        }

        Log.d(TAG, "generateReconnectRequest, request:" + (request != null ? request : null));

        return request;
    }

    private ConnectRequest getReconnectRequest(int connId) {
        Log.d(TAG, "getReconnectRequest, connId:" + connId);
        Connection connection = getConnection(connId);
        String ipAddress = connection != null ? connection.getIp() : "";

        synchronized (mReconnectRequestList) {
            if (mReconnectRequestList.size() > 0 && !TextUtils.isEmpty(ipAddress)) {
                for (ConnectRequest request : mReconnectRequestList) {
                    if (TextUtils.equals(ipAddress, request.ipAddress)) {
                        Log.d(TAG, "getReconnectRequest, request:" + request);
                        return request;
                    }
                }
            }
        }

        return null;
    }

    private ConnectRequest getReconnectRequest(String ipAddress) {
        Log.d(TAG, "getReconnectRequest, ipAddress:" + ipAddress);

        synchronized (mReconnectRequestList) {
            if (mReconnectRequestList.size() > 0 && !TextUtils.isEmpty(ipAddress)) {
                for (ConnectRequest request : mReconnectRequestList) {
                    if (TextUtils.equals(ipAddress, request.ipAddress)) {
                        Log.d(TAG, "getReconnectRequest, request:" + request);
                        return request;
                    }
                }
            }
        }

        return null;
    }

    public boolean hasPendingConnectRequest(String ipAddress) {
        Log.d(TAG, "hasPendingConnectRequest, ipAddress:" + ipAddress);
        synchronized (mReconnectRequestList) {
            if (mReconnectRequestList.size() > 0) {
                logConnectReqeustList();

                for (ConnectRequest request : mReconnectRequestList) {
                    Log.d(TAG, "hasPendingConnectRequest, request ip:" + request.ipAddress);
                    if (TextUtils.equals(ipAddress, request.ipAddress)) {
                        Log.d(TAG, "hasPendingConnectRequest, true for ip:" + ipAddress);
                        return true;
                    }
                }
            }
        }

        Log.d(TAG, "hasPendingConnectRequest, false for reconnecting:" + ipAddress);

        return false;
    }

    public boolean hasPendingConnectRequest(int connId) {
        logConnectReqeustList();

        ConnectRequest request = getReconnectRequest(connId);

        Log.d(TAG, "hasPendingConnectRequest:" + (request != null));
        return request != null;
    }

    private void removeConnectRequest(String ipAddress) {
        logConnectReqeustList();

        Log.d(TAG, "removeConnectRequest, ipAddress:" + ipAddress);
        ConnectRequest request = getReconnectRequest(ipAddress);

        if (request != null) {
            synchronized (mReconnectRequestList) {
                Log.d(TAG, "removeConnectRequest, request:" + request);
                mReconnectRequestList.remove(request);
            }
        }
    }

    private void logConnectReqeustList() {
        Log.d(TAG, "logConnectRequestList, size:"  + mReconnectRequestList.size());
        synchronized (mReconnectRequestList) {
            if (mReconnectRequestList.size() > 0) {
                for (ConnectRequest request : mReconnectRequestList) {
                    Log.d(TAG, "    "  + request.toString());
                }
            }
        }
    }

    private void onConnectionCreationSuccess(Connection conn,
                                            ConnectionCreationCallback listener) {
        // if all connections is stopped, then we notify failed here.
        // connection already closed case not handled here. it put to connection.addlistener.
        removeConnectionCreationTimeoutEvent(conn);
        if (!mStopped) {
            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_ADDED, conn)
                    .sendToTarget();

            notifyConnectionCreationResult(conn, listener, true, 0);
        } else {
            notifyConnectionCreationResult(conn,
                    listener,
                    false,
                    Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_CANCELED);
        }
    }

    /*
     * Connection creation failed not only means connection new failed. Socket of connection
     * connected failed is also this case.
     */
    private void onConnectionCreationFailed(Connection conn,
                                            int reason,
                                            ConnectionCreationCallback listener) {
        removeConnectionCreationTimeoutEvent(conn);

        if (isReconnectAllowed(conn, reason)) {
            Log.d(TAG, "onConnectionCreationFailed, allowed to reconnect to " + conn.getIp());
            tryReconnectTo(conn,
                    reason);
        } else {
            removeConnectRequest(conn.getIp());
            // listener is null and reconnect request not null means
            // current connection creation is one reconnect failed case.
            // notify disconnect needed.
            if (conn.getReconnectRequest() != null && listener == null) {
                mThreadHandler
                        .obtainMessage(EVENT_CONNECTION_CLOSED,
                                conn.getReconnectRequest().disconnectReason,
                                1,
                                conn)
                        .sendToTarget();
            } else {
                notifyConnectionCreationResult(conn, listener, false, reason);
            }
        }
    }

    private void notifyConnectionCreationResult(Connection connection,
                                                ConnectionCreationCallback listener,
                                                boolean ret,
                                                int reason) {
        Log.d(TAG, "notifyConnectionCreationResult response:" + listener + ", ret:" + ret);
        if (listener == null) {
            return;
        }

        listener.onResult(ret, reason);
    }

    // this time out event should be removed once connection has feedback (connect success
    // or failed.)
    private void removeConnectionCreationTimeoutEvent(Connection connection) {
        if (connection != null) {
            mThreadHandler.removeMessages(EVENT_CONNECTION_CREATION_TIMEOUT, connection);
            Log.d(TAG, "removeConnectionCreationTimeoutEvent, connection:" + connection);
        }
    }

    private void handleConnectionCreationTimeoutEvent(Connection connection) {
        Log.d(TAG, "handleConnectionCreationTimeoutEvent! state:" + connection.getState());

        if (connection != null
                && connection.getState() != Connection.CONNECTION_STATE_CONNECTED
                && connection.getState() != Connection.CONNECTION_STATE_CLOSEED) {
            connection.close(Connection.CONNECTION_REASON_CODE_CONNECT_TIMEOUT);
        }
    }

    private HostConnection.HostConnectionListener mHostListener =
            new HostConnection.HostConnectionListener() {
                @Override
                public void onConnectionConnected(HostConnection host, Connection connection) {
                    mThreadHandler.obtainMessage(EVENT_CONNECTION_ADDED, connection).sendToTarget();
                }

                @Override
                public void onHostClosed(HostConnection host, int errorCode) {
                    mThreadHandler
                            .obtainMessage(EVENT_HOSTCONNECTION_CLOSED, errorCode, 0, host)
                            .sendToTarget();
                }
            };

    private void handleHostConnectionClosed(HostConnection host, int errorCode) {
        if (host != null) {
            synchronized (mHostConnList) {
                mHostConnList.remove(host);
            }
        }

        // TODO: notify to UI
    }

    public void listen(int port) {
        mStopped = false;

        if (isHostPortUsed(port)) {
            Log.d(this, "port is already in used! port:" + port);
            return;
        }

        final HostConnection hostConnection =
                ConnectionFactory.createHostConnection(port,
                        Utils.createBackGroundWakeLock(mContext,
                                "host:" + SystemClock.elapsedRealtime()),
                        mHostListener);

        if (hostConnection != null) {
            synchronized (mHostConnList) {
                mHostConnList.add(hostConnection);
            }
        } else {
            // TODO:
        }
    }

    private boolean isHostPortUsed(int port) {
        synchronized (mHostConnList) {
            for (HostConnection connection : mHostConnList) {
                if (connection.getPort() == port) {
                    return true;
                }
            }
        }

        return false;
    }

    public interface SearchListener{
        void onSearchCompleted();
        void onSearchCanceled(int reason);
    }

    private List<SearchListener> mSearchListeners =
            new CopyOnWriteArrayList<SearchListener>();

    public boolean isHostSearching() {
        return HostSearchHandler.getInstance(mContext).isSearching();
    }

    public void searchHost(String ipSegment, int port, final SearchListener listener) {
        mStopped = false;

        if (listener != null) {
            synchronized (mSearchListeners) {
                mSearchListeners.add(listener);
            }
        }

        if (isHostSearching()) {
            Log.d(TAG, "searchHost, ipSegment:" + ipSegment + ", is in searching state!");
            return;
        }

        logConnectionList();

        HostSearchHandler.getInstance(mContext).searchServer(ipSegment, port, new HostSearchHandler.ServerSearchListener() {
            @Override
            public void onSearchCompleted() {
                Log.d(TAG, "searchHost, onSearchCompleted");
                notifySearchCompleted();
            }

            @Override
            public void onSearchCanceled(int reason) {
                Log.d(TAG, "searchHost, onSearchCanceled reason:" + reason);
                notifySearchCanceled(reason);
            }

            @Override
            public void onConnectionConnected(Connection connection) {
                Log.d(TAG, "searchHost, onConnectionConnected, ip:" + connection.getIp());
                mThreadHandler
                        .obtainMessage(EVENT_CONNECTION_ADDED, connection)
                        .sendToTarget();
            }
        });
    }

    private void notifySearchCompleted() {
        synchronized (mSearchListeners) {
            for (SearchListener listener : mSearchListeners) {
                listener.onSearchCompleted();
            }

            mSearchListeners.clear();
        }
    }

    private void notifySearchCanceled(int reason) {
        synchronized (mSearchListeners) {
            for (SearchListener listener : mSearchListeners) {
                listener.onSearchCanceled(reason);
            }

            mSearchListeners.clear();
        }
    }

    private void logConnectionList() {
        StringBuilder builder = new StringBuilder();

        synchronized (mConnections) {
            if (mConnections.size() > 0) {
                builder.append("mConnections, connection count:" + mConnections.size() + "\n");
                for (Connection connection : mConnections.values()) {
                    builder.append("        id:" + connection.getId()
                            + ", ip:" + connection.getIp()
                            + ", host:" + connection.isHost() + "\n");
                }
            } else {
                builder.append("mConnections: no connections existed!");
            }
        }

        Log.d(TAG, builder.toString());
    }
}
