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

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static final long HOST_RECONNECT_DELAY = 5000; //5s

    private Context mContext;

    public interface ConnectionManagerListener {
        void onConnectionAdded(int id);
        void onConnectionRemoved(int id, int reason);

        void onEventReceived(int id, Event event);
    }

    private class ConnectionListenerImpl implements Connection.ConnectionListener {
        @Override
        public void onConnected(Connection conn) {
            mThreadHandler.obtainMessage(EVENT_CONNECTION_ADDED, conn).sendToTarget();

            Log.d(TAG, "connection connected for ip:" + conn.getIp());
        }

        @Override
        public void onClosed(Connection conn, int reasonCode) {
            Log.d(TAG, "connection closed for ip:" + conn.getIp() + ", id:" + conn.getId()
                    + ", reason:" + reasonCode);

            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_CLOSED,
                            reasonCode, 0, conn)
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

    private static Integer sConnIdBase = -1;

    private int mPort;

    private ConnMgrThreadHandler mThreadHandler;
    private ConnectionDataTracker mDataTracker;
    private ConnectionCreationHandler mConnectionCreationHandler;
    private ClientsSearchHandler mClientsSearchHandler;

    private boolean mStopped = false;

    private boolean mReconnectFlag = true;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_CLOSED = 1;
    private static final int EVENT_HOSTCONNECTION_CLOSED = 3;
    private static final int EVENT_NOTIFY_CONNECTION_ADDED = 4;
    private static final int EVENT_NOTIFY_CONNECTION_REMOVED = 5;

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
                case EVENT_NOTIFY_CONNECTION_ADDED:
                    // TODO: add reconnect flag...?
                    notifyConnectionAdded(msg.arg1);
                    break;
                case EVENT_NOTIFY_CONNECTION_REMOVED:
                    notifyConnectionRemoved(msg.arg1, msg.arg2);
                    break;
                case EVENT_CONNECTION_CLOSED:
                    handleConnectionRemoved((Connection)msg.obj,
                            msg.arg1);
                    break;
                case EVENT_HOSTCONNECTION_CLOSED:
                    handleHostConnectionClosed((HostConnection) msg.obj, msg.arg1);
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

        mClientsSearchHandler = ClientsSearchHandler.getInstance(context);

        // use same looper with connection manager due to no long time cpu hosting work.
        mConnectionCreationHandler = new ConnectionCreationHandler(thread.getLooper(),
                this);

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

    private void handleConnectionAdded(final Connection connection) {
        Log.d(TAG, "handleConnectionAdded, ip:" + connection.getIp());
        logConnectionList();

        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
                String ipAddress = connection.getIp();

                if (isIpConnected(ipAddress) && !(Utils.DEBUG_CONNECT_SELF && mConnections.size() <= 2)) {
                    Log.d(this, "handleConnectionAdded: ipAddress already connected!");
                    connection.close(Connection.CONNECTION_REASON_CODE_CONNECT_ALREADY_CONNECTED);
                    return;
                }

                boolean reConnected = false;

                ConnectionCreationRequest request =
                        mConnectionCreationHandler.getPendingConnectRequest(connection.getIp());

                // this connection maybe reconnected and connection id already existed
                int id = connection.getId();

                if (request != null) {
                    id = request.connId;
                    reConnected = true;

                    if (connection.getId() != -1 && connection.getId() != id) {
                        Log.e(TAG, "why there are two different ids for same ip...");
                    }

                    Log.d(TAG, "handleConnectionAdded, drop request due to already connected!");
                    request.isDropped = true;
                }

                if (id < 0) {
                    id = generateConnectionId();
                }

                connection.setId(id);
                connection.addListner(new ConnectionListenerImpl());

                long curTime = SystemClock.currentThreadTimeMillis();
                connection.setWakeLock(Utils.createBackGroundWakeLock(mContext,
                                String.valueOf(connection.getId()) + ":send"),
                        Utils.createBackGroundWakeLock(mContext,
                                String.valueOf(connection.getId()) + ":receive"));

                Log.d(TAG, "handleConnectionAdded, wakelock created time:" +
                        (SystemClock.currentThreadTimeMillis() - curTime));

                mConnections.put(connection.getId(), connection);
                mDataTracker.onConnectionAdded(connection);
                connection.startHeartBeat();



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

    private void handleConnectionRemoved(Connection connection, int reason) {
        Log.d(TAG, "handleConnectionRemoved, id:" + connection.getId() + ", ip:"
                + connection.getIp() + ", reason:" + reason);
        logConnectionList();

        synchronized (mConnections) {
            if (mConnections.containsValue(connection)) {
                mConnections.remove(connection.getId());

                if (isReconnectAllowed(connection, reason)) {
                    Log.d(TAG, "handleConnectionRemoved, allowed to reconnect.");
                    long connectDelay = connection.isHost() ? HOST_RECONNECT_DELAY : 0;

                    ConnectionCreationRequest request =
                            ConnectionCreationRequest.generateReconnectRequest(connection,
                                    mPort,
                                    reason,
                                    3,
                                    connectDelay,
                                    new ConnectionCreationResponse(){
                        @Override
                        public void onResponse(boolean success,
                                               Connection connection,
                                               ConnectionCreationRequest connectRequest,
                                               int reason) {
                            Log.d(TAG, "handleConnectionRemoved, reconnect result:" + success);
                            if (!success) {
                                mDataTracker.onConnectionRemoved(connectRequest.connId);
                                mThreadHandler
                                        .obtainMessage(EVENT_NOTIFY_CONNECTION_REMOVED,
                                                connectRequest.connId,
                                                connectRequest.disconnectReason)
                                        .sendToTarget();
                            }
                        }
                    });

                    mConnectionCreationHandler.processConnectCreationRequest(request, connectDelay);
                } else {
                    mDataTracker.onConnectionRemoved(connection.getId());
                    mThreadHandler
                            .obtainMessage(EVENT_NOTIFY_CONNECTION_REMOVED,
                                    connection.getId(), reason)
                            .sendToTarget();
                }
            }
        }
    }

    public void setPort(int port) {
        mPort = port;
    }

    public Connection getConnection(int id) {
        synchronized (mConnections) {
            return mConnections.get(id);
        }
    }

    public Connection getConnection(String ipAddress) {
        synchronized (mConnections) {
            for (Connection conn : mConnections.values()) {
                if (TextUtils.equals(conn.getIp(), ipAddress)) {
                    Log.d(TAG, "getConnection:" + ipAddress);
                    return conn;
                }
            }
        }

        return null;
    }

    public ClientInfo getClientInfo(int connId) {
        Connection connection = getConnection(connId);

        if (connection != null) {
            return (ClientInfo) connection.getConnData();
        }

        return null;
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

    public boolean hasPendingConnectRequest(String ipAddress) {
        return null != mConnectionCreationHandler.getPendingConnectRequest(ipAddress);
    }

    private Integer generateConnectionId() {
        synchronized (sConnIdBase) {
            // there is no need to adjust connectionId when it is small enough.
            if (sConnIdBase > 10000) {
                int newIdBase = -1;
                for (Connection connection : mConnections.values()) {
                    if (connection.getId() > newIdBase) {
                        newIdBase = connection.getId();
                    }
                }

                if (sConnIdBase > newIdBase
                        && !mConnectionCreationHandler.hasActiveRequest()) {
                    Log.d(TAG, "generateConnectionId, adjust conn id base to:" + newIdBase);
                    sConnIdBase = newIdBase;
                }
            }

            sConnIdBase = sConnIdBase + 1;
        }

        Log.d(TAG, "generateConnectionId:" + sConnIdBase);

        return sConnIdBase;
    }

    public void disconnectAllConnections() {
        Log.d(TAG, "disconnectAllConnections");
        mStopped = true;

        mClientsSearchHandler.stopSearch();
        mConnectionCreationHandler.cancelAllRequests();

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

    public void addConnection(Connection connection) {
        mThreadHandler.obtainMessage(EVENT_CONNECTION_ADDED, connection).sendToTarget();
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

    public int getConnectionsSize() {
        return mConnections.size();
    }

    public boolean isReconnectAllowed() {
        return mReconnectFlag;
    }

    public void sendEvent(EventSendRequest request) {
        mDataTracker.sendEvent(request);
    }

    private boolean isReconnectAllowed(Connection connection, int reason) {
        if (mReconnectFlag
                && !mStopped
                && connection != null
                && !TextUtils.isEmpty(connection.getIp())
                && (reason == Connection.CONNECTION_REASON_CODE_IO_EXCEPTION)) {
            return true;
        }

        return false;
    }

    public void connectTo(final String ipAddress, int port,
                          final ConnectionCreationResponse response) {
        ConnectionCreationRequest request =
                mConnectionCreationHandler.getPendingConnectRequest(ipAddress);

        if (request != null) {
            Log.d(TAG, "connectTo, has pending connect request:" + request.toString());
            request.addResponse(response);
            return;
        }

        request = ConnectionCreationRequest.generateConnectRequest(ipAddress,
                port, 2, response);

        Log.d(TAG, "connectTo, " + request.toString());
        mConnectionCreationHandler.processConnectCreationRequest(request);
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
            Log.d(TAG, "listen failed!");
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
        void onSearchCanceled();
    }

    private List<SearchListener> mSearchListeners =
            new CopyOnWriteArrayList<SearchListener>();

    public boolean isClientSearching() {
        return mClientsSearchHandler.isClientSearching();
    }

    public void searchHost(String ipSegment, final SearchListener listener) {
        Log.d(TAG, "searchHost, ipSegment:" + ipSegment);
        mStopped = false;

        if (listener != null) {
            synchronized (mSearchListeners) {
                mSearchListeners.add(listener);
            }
        }

        if (isClientSearching()) {
            Log.d(TAG, "searchHost, ipSegment:" + ipSegment + ", is in searching state!");
            return;
        }

        logConnectionList();

        mClientsSearchHandler.addListener(
                new ClientsSearchHandler.ClientSearchListener() {
                    @Override
                    public void onSearchStarted() {}

                    @Override
                    public void onSearchCompleted() {
                        Log.d(TAG, "searchHost, onSearchCompleted");
                        mClientsSearchHandler.removeListener(this);
                        notifySearchCompleted();
                    }

                    @Override
                    public void onSearchCanceled() {
                        Log.d(TAG, "searchHost, onSearchCanceled.");
                        mClientsSearchHandler.removeListener(this);
                        notifySearchCanceled();
                    }
                });

        mClientsSearchHandler.searchClientInSegment(ipSegment);
    }

    private void notifySearchCompleted() {
        synchronized (mSearchListeners) {
            for (SearchListener listener : mSearchListeners) {
                listener.onSearchCompleted();
            }

            mSearchListeners.clear();
        }
    }

    private void notifySearchCanceled() {
        synchronized (mSearchListeners) {
            for (SearchListener listener : mSearchListeners) {
                listener.onSearchCanceled();
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

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        try {
            writer.println("  ConnectionManager:");
            writer.println("    mListeners size:" + mListeners.size());
            writer.println("    sConnIdBase:" + sConnIdBase);
            writer.println("    mPort:" + mPort);
            writer.println("    mStopped:" + mStopped);
            writer.println("    isReconnectAllowed:" + mReconnectFlag);

            writer.println("    isClientSearching:" + isClientSearching());
            writer.println("    mSearchListeners size:" + mSearchListeners.size());
            writer.println("");
            mClientsSearchHandler.dump(fd, writer, args);
            writer.println("");

            // mConnections
            writer.println("    Connections(" + mConnections.size() + "):");
            if (mConnections.size() > 0) {
                for (Connection connection : mConnections.values()) {
                    connection.dump(fd, writer, args);
                }
            } else {
                writer.println("      no connections!");
            }
            writer.println("");

            // mHostConnList
            writer.println("    HostConnections(" + mHostConnList.size() + "):");
            if (mHostConnList.size() > 0) {
                for (HostConnection hostConnection : mHostConnList) {
                    hostConnection.dump(fd, writer, args);
                }
            } else {
                writer.println("      no HostConnections!");
            }
            writer.println("");

            mDataTracker.dump(fd, writer, args);
            writer.println("");

            mConnectionCreationHandler.dump(fd, writer, args);
            writer.println("");
        } catch (Exception e) {
            Log.d(this, "Exception happened when dump:" + e.getMessage());
        }
    }
}
