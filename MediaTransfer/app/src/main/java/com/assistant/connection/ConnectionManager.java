package com.assistant.connection;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;

import com.assistant.bytestring.ByteString;
import com.assistant.bytestring.ByteStringPool;
import com.assistant.events.ClientInfo;
import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;
import com.assistant.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

        void onDataReceived(int id, String data, boolean isFile);
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
        public void onDataReceived(int id, String data, boolean isFile) {
            Log.d(this, "ConnectionManagerListenerBase.onDataReceived");
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
            Log.d(TAG, "connection connected failed for ip:" + conn.getIp());
            mThreadHandler
                    .obtainMessage(EVENT_CONNECTION_CONNECT_FAILED, reasonCode, 0, conn)
                    .sendToTarget();
        }

        @Override
        public void onClosed(Connection conn, int reasonCode) {
            Log.d(TAG, "connection closed for ip:" + conn.getIp() + ", id:" + conn.getId() + ", reason:" + reasonCode);

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

    private ThreadPool mThreadPool = new ThreadPool(5);

    private ConnMgrThreadHandler mThreadHandler;

    private boolean mStopped = false;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_CLOSED = 1;
    private static final int EVENT_CONNECTION_CONNECT_FAILED = 2;
    private static final int EVENT_CONNECTION_DATA_RECEIVED = 3;
    private static final int EVENT_CONNECTION_CREATION_TIMEOUT = 4;
    private static final int EVENT_HOSTCONNECTION_CLOSED = 5;

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
                case EVENT_CONNECTION_DATA_RECEIVED:
                    notifyDataReceived(msg.arg1, (String)msg.obj, (msg.arg2 == 1));
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

        if (thread != null) {
            mThreadHandler = new ConnMgrThreadHandler(thread.getLooper());
        }
    }

    public void handleConnectionAdded(final Connection connection) {
        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
                String ipAddress = connection.getIp();
                if (isIpConnected(ipAddress) && !(Utils.DEBUG && mConnections.size() <= 2)) {
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
                notifyConnectionAdded(connection.getId());

                connection.startHeartBeat();

                new ConnectionReceiverThread(connection).start();

                logConnectionList();
            }
        }
    }

    private void handleConnectionRemoved(Connection connection, int reason) {
        Log.d(TAG, "handleConnectionRemoved, id:" + connection.getId() + ", ip:"
                + connection.getIp() + ", reason:" + reason);
        synchronized (mConnections) {
            mConnections.remove(connection.getId());

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

    private void notifyDataReceived(int connId, String data, boolean isFile) {
        for(ConnectionManagerListener listener : mListeners) {
            listener.onDataReceived(connId, data, isFile);
        }
    }

    class EventSendRunnable implements Runnable {
        private int connId;
        private byte[] bytes;
        private long bytesLen;
        private long eventId;
        private DataSendListener listener;
        private String filePathName;
        EventSendRunnable(final int connectionId,
                          final long sendEventId,
                          final byte[] jsonBytes,
                          final long jsonLen,
                          final String strFilePathName,
                          final DataSendListener sendListener) {
            connId = connectionId;
            bytes = jsonBytes;
            bytesLen = jsonLen;
            eventId = sendEventId;
            filePathName = strFilePathName;
            listener = sendListener;
        }

        @Override
        public void run() {
            Connection conn = getConnection(connId);
            int ret = 0;
            boolean success = false;

            Log.d(TAG, "EventSendRunnable, conn:" + conn + ", bytesLen:" + bytesLen);

            if (conn != null) {
                Log.d(TAG, "EventSendRunnable, filePathName:" + filePathName);
                if (TextUtils.isEmpty(filePathName)) {
                    byte[] header = generateDataHeader(bytesLen, 0);

                    Log.d(TAG, "EventSendRunnable, header:" + header.length);

                    ret = conn.send(header, DATA_HEADER_LEN_v1);
                    if (ret == DATA_HEADER_LEN_v1) {
                        ret = conn.send(bytes, bytesLen);

                        if (ret == bytesLen) {
                            success = true;
                        }
                    }

                    Log.d(TAG, "EventSendRunnable, send ret:" + ret);

                    if (listener != null) {
                        if (success) {
                            listener.onSendProgress(eventId, 100);
                            listener.onResult(eventId, DataSendListener.RESULT_SUCESS, ret);
                        } else {
                            listener.onResult(eventId, DataSendListener.RESULT_FAILED, ret);
                        }
                    }
                } else {
                    Log.d(TAG, "EventSendRunnable, send file:" + filePathName);
                    FileInputStream fileInputStream;
                    try {
                        fileInputStream = new FileInputStream(filePathName);
                        int fileLen = fileInputStream.available();

                        byte[] header = generateDataHeader(bytesLen, fileLen);

                        ret = conn.send(header, DATA_HEADER_LEN_v1);
                        if (ret == DATA_HEADER_LEN_v1) {
                            ret = conn.send(bytes, bytesLen);

                            if (ret == bytesLen && fileLen > 0) {
                                int bufferSize =
                                        fileLen > Connection.SOCKET_DEFAULT_BUF_SIZE ?
                                                Connection.SOCKET_DEFAULT_BUF_SIZE : fileLen;

                                byte[] buffer = new byte[bufferSize];

                                int sentBytes = 0;
                                int bytesFileRead;
                                do {
                                    bytesFileRead = fileInputStream.read(buffer);
                                    ret = conn.send(buffer, bytesFileRead);

                                    if (ret == bytesFileRead) {

                                        sentBytes += ret;
                                        listener.onSendProgress(eventId, (sentBytes * 100) / fileLen);
                                    } else {
                                        success = false;
                                        break;
                                    }
                                } while (sentBytes < fileLen);

                                if (sentBytes == fileLen) {
                                    success = true;
                                }
                            } else if (ret == bytesLen) {
                                listener.onSendProgress(eventId, 100);
                                success = true;
                            }
                        }

                        fileInputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (success) {
                        listener.onResult(eventId, DataSendListener.RESULT_SUCESS, 0);
                    } else {
                        listener.onResult(eventId, DataSendListener.RESULT_FAILED, ret);
                    }
                }

            }

            Log.d(TAG, "EventSendRunnable, ended for connId:" + connId);
        }
    }

    /**
     * Send one jsonBytes packet for connection with connId.
     *
     * @param connId
     * @param jsonBytes : bytes of one json structure.
     * @param jsonLen
     * @param listener
     */
    public void sendEvent(final int connId,
                          final long eventId,
                          final byte[] jsonBytes,
                          final long jsonLen,
                          final DataSendListener listener) {
        Log.d(TAG, "sendEvent, connId:" + connId + ", eventId:" + eventId
                + ", jsonLen:" + jsonLen);
        mThreadPool.addTask(new EventSendRunnable(connId,
                eventId,
                jsonBytes,
                jsonLen,
                "",
                listener));
    }

    /**
     * send file content. file info should be already added to json bytes.
     *
     * @param connId
     * @param jsonBytes, this json data should be already contain file info of strFilePathName.
     * @param jsonLen
     * @param strFilePathName
     * @param listener
     */
    public void sendFile(final int connId,
                         final long eventId,
                         final byte[] jsonBytes,
                         final long jsonLen,
                         final String strFilePathName,
                         final DataSendListener listener) {
        Log.d(TAG, "sendFile, connId:" + connId + ", eventId:" + eventId
                + ", jsonLen:" + jsonLen + ", strFilePathName:" + strFilePathName);

        mThreadPool.addTask(new EventSendRunnable(connId,
                eventId,
                jsonBytes,
                jsonLen,
                strFilePathName,
                listener));
    }

    /**
     * version 1(22 bytes):"[v:(long);j:(long);f:(long)]"
     * v: version num. long type.
     * j: json length. long type.
     * f: file length. long type.
     */
    private byte[] generateDataHeader(long jsonLen, long fileLen) {
        ByteBuffer buf = ByteBuffer.allocate(DATA_HEADER_LEN_v1);

        buf.put("[v:".getBytes());

        //Log.d(TAG, "p1:" + buf.position());
        buf.putLong(1);
        //Log.d(TAG, "p2:" + buf.position());
        buf.put(";j:".getBytes());
        //Log.d(TAG, "p3:" + buf.position());
        buf.putLong(jsonLen);
        //Log.d(TAG, "p4:" + buf.position());
        buf.put(";f:".getBytes());
        //Log.d(TAG, "p5:" + buf.position());
        buf.putLong(fileLen);
        //Log.d(TAG, "p6:" + buf.position());
        buf.put("]".getBytes());

        return buf.array();
    }

    private class ConnectionReceiverThread extends Thread {
        private Connection mConnection;
        private ByteBuffer mHeaderBuf = ByteBuffer.allocate(DATA_HEADER_LEN_v1);

        private long mHeaderVersion;
        private long mJsonLen;
        private long mFileLen;

        private static final int RECEIVING_IDLE = 0;
        private static final int RECEIVING_HEADER = 1;
        private static final int RECEIVING_JSON = 2;
        private static final int RECEIVING_FILE = 3;

        private int mReceivingState = RECEIVING_HEADER;

        ConnectionReceiverThread(Connection connection) {
            mConnection = connection;
        }

        @Override
        public void run() {
            while(true) {
                if (mConnection.getState() != Connection.CONNECTION_STATE_CONNECTED) {
                    Log.d(this, "connection closed:" + mConnection.getIp()
                            + ", state:" + mConnection.getState());
                    break;
                }

                Log.d(TAG, "ConnectionReceiverThread, mReceivingState:" + mReceivingState);

                if (mReceivingState == RECEIVING_HEADER) {
                    int bytesReceived = 0;
                    if (mConnection != null &&
                            mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                        bytesReceived = mConnection.receive(mHeaderBuf.array(), DATA_HEADER_LEN_v1);
                    }

                    if (bytesReceived == DATA_HEADER_LEN_v1) {
                        handleConnectionHeader();
                    } else {
                        closeConnection();
                    }
                  } else if (mReceivingState == RECEIVING_JSON) {
                    Log.d(TAG, "ConnectionReceiverThread, mJsonLen:" + mJsonLen);
                    if (mJsonLen <= ByteString.DEFAULT_STRING_SIZE) {
                        ByteString buf = handleDataReceiving((int)mJsonLen);
                        if (buf != null) {
                            if (mFileLen > 0) {
                                setState(RECEIVING_FILE);
                            } else {
                                setState(RECEIVING_HEADER);
                            }

                            Message msg = mThreadHandler.obtainMessage(
                                    EVENT_CONNECTION_DATA_RECEIVED, mConnection.getId(), 0);

                            msg.obj = buf.toString();
                            Log.d(TAG, "ConnectionReceiverThread, received json:" + buf.toString());

                            buf.release();
                            msg.sendToTarget();
                        } else {
                            closeConnection();
                        }
                    } else {
                        Log.e(this, "some problems happened. one big json coming... ignore!");
                        closeConnection();
                    }

                } else if (mReceivingState == RECEIVING_FILE) {
                    ByteString buf = handleDataReceiving((int)mFileLen);

                    if (buf != null) {
                        setState(RECEIVING_HEADER);

                        Message msg = mThreadHandler.obtainMessage(
                                EVENT_CONNECTION_DATA_RECEIVED,
                                mConnection.getId(),
                                (mFileLen > ByteString.DEFAULT_STRING_SIZE ? 1 : 0));

                        msg.obj = buf.toString();
                        buf.release();
                        msg.sendToTarget();
                    } else {
                        closeConnection();
                    }
                } else {
                    // ??
                }
            }
        }

        private void setState(int state) {
            Log.d(TAG, "ConnectionReceiverThread, setState:" + state);
            mReceivingState  = state;

            if (state == RECEIVING_HEADER) {
                mFileLen = 0;
                mJsonLen = 0;
                mHeaderVersion = 0;
            }
        }

        /*
         * version 1(22 bytes):"[v:(long);j:(long);f:(long)]"
         *      v: version num. long type.
         *      j: json length. long type.
         *      f: file length. long type.
         *
         */
        private void handleConnectionHeader() {
            byte start = mHeaderBuf.get();
            byte v = mHeaderBuf.get();
            byte colon1 = mHeaderBuf.get();
            mHeaderVersion = mHeaderBuf.getLong();
            byte sep1 = mHeaderBuf.get();
            byte j = mHeaderBuf.get();
            byte colon2 = mHeaderBuf.get();
            mJsonLen = mHeaderBuf.getLong();
            byte sep2 = mHeaderBuf.get();
            byte f = mHeaderBuf.get();
            byte colon3 = mHeaderBuf.get();
            mFileLen = mHeaderBuf.getLong();
            byte end = mHeaderBuf.get();

            Log.d(TAG, "handleConnectionHeader, mHeaderVersion:" + mHeaderVersion
                    + ", mJsonLen:" + mJsonLen
                    + ", mFileLen:" + mFileLen);

            if (mHeaderVersion == DATA_HEADER_V1 &&
                    start == '[' &&
                    end == ']' &&
                    v == 'v' &&
                    j == 'j' &&
                    f == 'f' &&
                    colon1 == ':' &&
                    colon2 == ':' &&
                    colon3 == ':' &&
                    sep1 == ';' &&
                    sep2 == ';') {
                setState(RECEIVING_JSON);
            } else {
                // some problems happened! close connection.
                closeConnection();
            }

            // reset header buffer
            mHeaderBuf.clear();
        }

        private ByteString handleDataReceiving(int len) {
            if (mConnection == null ||
                    mConnection.getState() != Connection.CONNECTION_STATE_CONNECTED) {
                return null;
            }

            if (len <= ByteString.DEFAULT_STRING_SIZE) {
                ByteString buf = ByteStringPool.getInstance().getByteString();
                int bytesReceived = 0;
                if (mConnection != null &&
                        mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                    bytesReceived = mConnection.receive(buf.data, len);
                }

                if (bytesReceived != len) {
                    // some problem happened.
                    buf.release();
                    buf = null;
                } else {
                    buf.setDataLen(len);
                }

                return buf;
            } else {
                String path = Utils.getAppStoragePath(mContext);

                if (TextUtils.isEmpty(path)) {
                    // some problems happened that app file path get failed.
                    return null;
                }

                String fileName = String.valueOf(mConnection.hashCode()) +
                        "_" +
                        String.valueOf(System.currentTimeMillis() % 10000);

                File file = new File(path, fileName);
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                FileOutputStream fileOpStream = null;
                try {
                    fileOpStream = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    Log.e(this, "can't create FileOutputStream");
                    e.printStackTrace();
                    return null;
                }

                long receivedCount = 0;
                long bytesToReceive = 0;
                long bytesReceived = 0;

                boolean errorHappened = false;

                ByteString buf = ByteStringPool.getInstance().getByteString();
                do {
                    if (len - receivedCount >= ByteString.DEFAULT_STRING_SIZE) {
                        bytesToReceive = ByteString.DEFAULT_STRING_SIZE;
                    } else {
                        bytesToReceive = len - receivedCount;
                    }

                    if (mConnection != null &&
                            mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                        bytesReceived = mConnection.receive(buf.data, bytesToReceive);
                    }

                    if (bytesToReceive != bytesReceived) {
                        errorHappened = true;
                        break;
                    }

                    receivedCount += bytesReceived;

                    try {
                        fileOpStream.write(buf.data, 0, (int) bytesReceived);
                    } catch (IOException e) {
                        e.printStackTrace();

                        errorHappened = true;
                        break;
                    }
                } while (len > receivedCount);

                try {
                    fileOpStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    errorHappened = true;
                }

                if (errorHappened) {
                    buf.release();
                    buf = null;
                } else {
                    String filePathName = path + "/" + fileName;
                    int length = buf.putData(filePathName.getBytes());

                    if (length == 0) {
                        Log.d(this, "copy file path name to bytestring error.");
                        buf.release();
                        buf = null;
                    }
                }

                return buf;
            }
        }

        private void closeConnection() {
            // TODO: to made one logic for reconnect.
            if (mConnection != null) {
                mConnection.close();
            }
        }
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
        Log.d(TAG, "notifyConnectionCreationResult listener:" + listener + ", ret:" + ret);
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
