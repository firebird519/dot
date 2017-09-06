package com.assistant.connection;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
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

    private Context mContext;

    public interface ConnectionManagerListener {
        void onConnectionAdded(int id);
        void onConnectionRemoved(int id, int reason);

        void onEventReceived(int id, Event event);
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
                    .obtainMessage(EVENT_CONNECTION_CONNECT_FAILED, reasonCode, 0, conn)
                    .sendToTarget();
        }

        @Override
        public void onClosed(Connection conn, int reasonCode) {
            Log.d(TAG, "connection closed for ip:" + conn.getIp() + ", id:" + conn.getId()
                    + ", reason:" + reasonCode);

            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_CLOSED, reasonCode, 0, conn)
                    .sendToTarget();
        }
    }

    private List<ConnectionManagerListener> mListeners =
            new CopyOnWriteArrayList<ConnectionManagerListener>();

    private final Map<Integer, Connection> mConnections =
            Collections.synchronizedMap(new HashMap<Integer, Connection>());

    private List<HostConnection> mHostConnList =
            Collections.synchronizedList(new ArrayList<HostConnection>());

    private static int sConnIdBase = -1;

    private ConnMgrThreadHandler mThreadHandler;
    private ConnectionDataTracker mDataTracker;

    private boolean mStopped = false;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_CLOSED = 1;
    private static final int EVENT_CONNECTION_CONNECT_FAILED = 2;
    private static final int EVENT_CONNECTION_CREATION_TIMEOUT = 3;
    private static final int EVENT_HOSTCONNECTION_CLOSED = 4;

    private static final int CONNECTION_CREATION_TIMEOUT_TIMESTAMP = 70*1000; //60s
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
                    handleConnectionRemoved((Connection)msg.obj, msg.arg1);
                    break;
                case EVENT_CONNECTION_CLOSED:
                    handleConnectionRemoved((Connection)msg.obj, msg.arg1);
                    break;
                case EVENT_CONNECTION_CREATION_TIMEOUT:
                    handleConnectionCreationTimeout((Connection) msg.obj);
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
        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
                String ipAddress = connection.getIp();
                if (isIpConnected(ipAddress) && !(Utils.DEBUG_CONNECTION && mConnections.size() <= 2)) {
                    Log.d(this, "handleConnectionAdded: ipAddress already connected!");
                    connection.close(Connection.CONNECTION_REASON_CODE_IP_ALREADY_CONNECTED);

                    logConnectionList();
                    return;
                }

                connection.setId(generateConnectionId());
                connection.setWakeLock(
                        generateNetworkWakeLock(String.valueOf(connection.getId()) + ":0"),
                        generateNetworkWakeLock(String.valueOf(connection.getId()) + ":1"));
                mConnections.put(connection.getId(), connection);
                connection.addListner(new ConnectionListenerImpl());
                mDataTracker.onConnectionAdded(connection);

                connection.startHeartBeat();

                notifyConnectionAdded(connection.getId());

                logConnectionList();
            }
        }
    }

    private void handleConnectionRemoved(Connection connection, int reason) {
        Log.d(TAG, "handleConnectionRemoved, id:" + connection.getId() + ", ip:"
                + connection.getIp() + ", reason:" + reason);
        synchronized (mConnections) {
            mConnections.remove(connection.getId());

            mDataTracker.onConnectionRemoved(connection);

            notifyConnectionRemoved(connection.getId(), reason);
            logConnectionList();
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
        /*int maxKey = -1;

        synchronized (mConnections) {
            Integer[] keys = getConnectionIds();
            if (keys == null) {
                return 0;
            }

            for (Integer key : keys) {
                if (maxKey < key) {
                    maxKey = key;
                }
            }
        }*/

        synchronized (mConnections) {
            sConnIdBase += 1;
        }

        Log.d(TAG, "generateConnectionId:" + sConnIdBase);

        return sConnIdBase;
    }

    public void stopAllConnections() {
        Log.d(TAG, "stopAllConnections");
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

    public void connectTo(final String ipAddress, int port, final ConnectionCreationCallback listener) {
        mStopped = false;

        if (isIpConnected(ipAddress)) {
            notifyConnectionCreationResult(null, listener, true);
            return;
        }

        Log.d(TAG, "connectTo ip:" + ipAddress);

        Connection connection =
                ConnectionFactory.createConnection(ipAddress, port, new Connection.ConnectionListener() {
                    @Override
                    public void onConnected(Connection conn) {
                        Log.d(TAG, "connectTo, onConnected for " + conn.getIp()
                                + ", ipAddress:" + ipAddress + ", mStopped:" + mStopped);

                        if (!mStopped) {
                            mThreadHandler
                                    .obtainMessage(EVENT_CONNECTION_ADDED, conn)
                                    .sendToTarget();

                            notifyConnectionCreationResult(conn, listener, true);
                        } else {
                            notifyConnectionCreationResult(conn, listener, false);
                        }

                        conn.removeListener(this);
                    }

                    @Override
                    public void onConnectFailed(Connection conn, int reasonCode) {
                        Log.d(TAG, "connectTo, onConnectFailed for " + conn.getIp()
                                + ", ipAddress:" + ipAddress);

                        notifyConnectionCreationResult(conn, listener, false);

                        conn.removeListener(this);
                    }

                    @Override
                    public void onClosed(Connection conn, int reasonCode) {
                        Log.d(TAG, "connectTo, onClosed for " + conn.getIp()
                                + ", ipAddress:" + ipAddress);

                        notifyConnectionCreationResult(conn, listener, false);

                        conn.removeListener(this);
                    }
                });

        if (connection == null) {
            Log.d(TAG, "connectTo ip:" + ipAddress + ", connection creation failed!");
            notifyConnectionCreationResult(null, listener, false);
        } else if(listener != null) {
            Message msg = mThreadHandler.obtainMessage(EVENT_CONNECTION_CREATION_TIMEOUT, connection);
            mThreadHandler.sendMessageDelayed(msg, CONNECTION_CREATION_TIMEOUT_TIMESTAMP);
        }
    }

    private void notifyConnectionCreationResult(Connection connection, ConnectionCreationCallback listener, boolean ret) {
        Log.d(TAG, "notifyConnectionCreationResult response:" + listener + ", ret:" + ret);
        if (listener == null) {
            return;
        }

        if (connection != null) {
            mThreadHandler.removeMessages(EVENT_CONNECTION_CREATION_TIMEOUT, connection);
        }

        listener.onResult(ret);
    }

    private void handleConnectionCreationTimeout(Connection connection) {
        Log.d(TAG, "handleConnectionCreationTimeout!");

        if (connection != null) {
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
                        generateNetworkWakeLock("host:" + SystemClock.elapsedRealtime()),
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

    public boolean isHostSearching() {
        return HostSearchHandler.getInstance(mContext).isSearching();
    }

    public void searchHost(String ipSegment, int port, final SearchListener listener) {
        mStopped = false;

        HostSearchHandler.getInstance(mContext).searchServer(ipSegment, port, new HostSearchHandler.ServerSearchListener() {
            @Override
            public void onSearchCompleted() {
                if (listener != null) {
                    listener.onSearchCompleted();
                }
            }

            @Override
            public void onSearchCanceled(int reason) {
                if (listener != null) {
                    listener.onSearchCanceled(reason);
                }
            }

            @Override
            public void onConnectionConnected(Connection connection) {
                handleConnectionAdded(connection);
            }
        });
    }

    private PowerManager.WakeLock generateNetworkWakeLock(String id) {
        PowerManager pm = (PowerManager)mContext.getSystemService(
                Context.POWER_SERVICE);
        return pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "connection:" + id);
    }

    private void logConnectionList() {
        StringBuilder builder = new StringBuilder();

        synchronized (mConnections) {
            if (mConnections.size() > 0) {
                builder.append("mConnections, connection count:" + mConnections.size() + "\n");
                for (Connection connection : mConnections.values()) {
                    builder.append("id:" + connection.getId() + ", ip:" + connection.getIp() + "\n");
                }
            } else {
                builder.append("mConnections: no connections existed!");
            }
        }

        Log.d(TAG, builder.toString());
    }
}
