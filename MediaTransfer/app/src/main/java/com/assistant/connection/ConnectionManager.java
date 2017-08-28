package com.assistant.connection;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.bytestring.ByteString;
import com.assistant.bytestring.ByteStringPool;
import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;
import com.assistant.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by alex on 17-8-19.
 *
 * Attention:
 * 1. current don't support big json content which bigger than 64k.
 * 2. for file transfer, if file size is smaller than 64k, then notify file data as ByteString.
 *    Otherwise saved file data to one temporary file and notify temporary file path name.
 *
 */

public class ConnectionManager {
    public static int DATA_HEADER_LEN_v1 = 22;
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

    private Set<ConnectionManagerListener> mListeners =
            new CopyOnWriteArraySet<>();

    private final Map<Integer, Connection> mConnections =
            Collections.synchronizedMap(new HashMap<Integer, Connection>());

    ArrayList<HostConnection> mHostConnList = new ArrayList<>();

    private ThreadPool mThreadPool = new ThreadPool(5);

    private ConnMgrThreadHandler mThreadHandler;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_REMOVED = 1;
    private static final int EVENT_CONNECTION_DATA_RECEIVED = 2;

    class ConnMgrThreadHandler extends Handler {
        public ConnMgrThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CONNECTION_ADDED:
                    notifyConnectionAdded(msg.arg1);
                    break;
                case EVENT_CONNECTION_REMOVED:
                    notifyConnectionRemoved(msg.arg1, msg.arg2);
                    break;
                case EVENT_CONNECTION_DATA_RECEIVED:
                    notifyDataReceived(msg.arg1, (String)msg.obj, (msg.arg2 == 1));
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

    public void addConnection(final Connection connection) {
        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
                String ipAddress = connection.getIp();
                if (isIpConnected(ipAddress) && !Utils.DEBUG) {
                    Log.d(this, "addConnection: ipAddress already connected!");
                    connection.close();
                    return;
                }

                connection.setId(generateConnectionId());
                mConnections.put(connection.getId(), connection);
                connection.addListner(new ConnectionListenerImpl(connection));
                connection.startHeartBeat();

                mThreadHandler.obtainMessage(EVENT_CONNECTION_ADDED, connection.getId()).
                        sendToTarget();

                new ConnectionReceiverThread(connection).start();
            }
        }
    }

    public Connection getConnection(int id) {
        return mConnections.get(id);
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
                    return true;
                }
            }
        }

        return false;
    }

    private Integer generateConnectionId() {
        int maxKey = -1;

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
        }

        return maxKey + 1;
    }

    private void removeConnection(Connection connection, int reason) {
        synchronized (mConnections) {
            mConnections.remove(connection);

            mThreadHandler.obtainMessage(EVENT_CONNECTION_REMOVED, connection.getId(), reason);
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

            if (conn != null) {
                if (TextUtils.isEmpty(filePathName)) {
                    byte[] header = generateDataHeader(bytesLen, 0);

                    ret = conn.send(header, DATA_HEADER_LEN_v1);
                    if (ret == DATA_HEADER_LEN_v1) {
                        ret = conn.send(bytes, bytesLen);

                        if (ret == bytesLen) {
                            success = true;
                        }
                    }
                }

                if (listener != null) {
                    if (success) {
                        listener.onSendProgress(eventId, 100);
                        listener.onResult(eventId, DataSendListener.RESULT_SUCESS, ret);
                    } else {
                        listener.onResult(eventId, DataSendListener.RESULT_FAILED, ret);
                    }
                }
            } else {
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

                            byte [] buffer = new byte[bufferSize];

                            int sentBytes = 0;
                            int bytesFileRead;
                            do {
                                bytesFileRead = fileInputStream.read(buffer);
                                ret = conn.send(buffer, bytesFileRead);

                                if (ret == bytesFileRead) {

                                    sentBytes += ret;
                                    listener.onSendProgress(eventId, (sentBytes * 100)/fileLen);
                                } else {
                                    success = false;
                                    break;
                                }
                            } while(sentBytes < fileLen);

                            if (sentBytes == fileLen) {
                                success = true;
                            }
                        } else if (ret == bytesLen) {
                            listener.onSendProgress(eventId, 100);
                            success = true;
                        }
                    }

                    fileInputStream.close();
                } catch(Exception e){
                    e.printStackTrace();
                }

                if (success) {
                    listener.onResult(eventId, DataSendListener.RESULT_SUCESS, 0);
                } else {
                    listener.onResult(eventId, DataSendListener.RESULT_FAILED, ret);
                }
            }
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

        try {
            buf.put("[v:1;j:".getBytes("UTF-8"));
            buf.putLong(jsonLen);
            buf.put(";f:".getBytes("UTF-8"));
            buf.putLong(fileLen);
            buf.put("]".getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

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
                    Log.d(this, "connection closed:" + mConnection.getIp());
                    break;
                }

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
                    if (mJsonLen <= ByteString.DEFAULT_STRING_SIZE) {
                        ByteString buf = handleFileReceiving((int)mJsonLen);
                        if (buf != null) {
                            if (mFileLen > 0) {
                                setState(RECEIVING_FILE);
                            } else {
                                setState(RECEIVING_HEADER);
                            }

                            Message msg = mThreadHandler.obtainMessage(
                                    EVENT_CONNECTION_DATA_RECEIVED, mConnection.getId(), 0);

                            msg.obj = buf.toString();
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
                    ByteString buf = handleFileReceiving((int)mFileLen);

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

        private ByteString handleFileReceiving(int len) {
            if (mConnection == null ||
                    mConnection.getState() != Connection.CONNECTION_STATE_CONNECTED) {
                return null;
            }

            if (len <= ByteString.DEFAULT_STRING_SIZE) {
                ByteString buf = ByteStringPool.getInstance().getByteString();
                int bytesReceived = 0;
                if (mConnection != null &&
                        mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                    bytesReceived = mConnection.receive(buf.data, mFileLen);
                }

                if (bytesReceived != len) {
                    // some problem happened.
                    buf.release();
                    buf = null;
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
                    if (mFileLen - receivedCount >= ByteString.DEFAULT_STRING_SIZE) {
                        bytesToReceive = ByteString.DEFAULT_STRING_SIZE;
                    } else {
                        bytesToReceive = mFileLen - receivedCount;
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
                } while (mFileLen > receivedCount);

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

    private class ConnectionListenerImpl implements Connection.ConnectionListener {
        private Connection mConnection;

        public ConnectionListenerImpl(Connection connection) {
            mConnection = connection;
        }

        @Override
        public void onConnected() {}

        @Override
        public void onConnectFailed(int reasonCode) {
            removeConnection(mConnection, reasonCode);
        }

        @Override
        public void onClosed(int reasonCode) {
            removeConnection(mConnection, reasonCode);
        }
    }

    private HostConnection.HostConnectionListener mHostListener =
            new HostConnection.HostConnectionListener() {
                @Override
                public void onConnectionConnected(HostConnection host, Connection connection) {
                    if (connection != null) {
                        addConnection(connection);
                    }
                }

                @Override
                public void onHostClosed(HostConnection host, int errorCode) {
                    if (host != null) {
                        mHostConnList.remove(host);
                    }

                    // TODO: notify to UI
                }
            };

    public void listen(int port) {
        final HostConnection hostConnection =
                ConnectionFactory.createHostConnection(port, mHostListener);

        if (hostConnection != null) {
            mHostConnList.add(hostConnection);
        }
    }

    public interface SearchListener{
        void onSearchCompleted();
        void onSearchCanceled(int reason);
    }

    public void searchHost(String ipSegment, int port, final SearchListener listener) {
        HostSearchHandler.getInstance(mContext).searchServer(ipSegment, port, new HostSearchHandler.ServerSearchListener() {
            @Override
            public void onSearchCompleted() {
                listener.onSearchCompleted();
            }

            @Override
            public void onSearchCanceled(int reason) {
                listener.onSearchCanceled(reason);
            }

            @Override
            public void onConnectionConnected(Connection connection) {
                addConnection(connection);
            }
        });
    }
}
