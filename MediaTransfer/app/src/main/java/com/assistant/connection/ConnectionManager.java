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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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


    private Context mContext;

    public interface ConnectionManagerListener {
        public void onConnectionAdded(int id);
        public void onConnectionRemoved(int id, int reason);

        public void onDataReceived(int id, ByteString data, boolean isFile);
    }

    private Set<ConnectionManagerListener> mListeners =
            new CopyOnWriteArraySet<ConnectionManagerListener>();

    private Map<Integer, Connection> mConnections =
            Collections.synchronizedMap(new HashMap<Integer, Connection>());

    private ThreadPool mThreadPool = new ThreadPool(5);

    private ThreadHandler mThreadHandler;

    private static final int EVENT_CONNECTION_ADDED = 0;
    private static final int EVENT_CONNECTION_REMOVED = 1;
    private static final int EVENT_CONNECTION_DATA_RECEIVED = 2;

    class ThreadHandler extends Handler {
        public ThreadHandler(Looper looper) {
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
                    notifyDataReceived(msg.arg1, (ByteString)msg.obj, (msg.arg2 == 1));
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

        mThreadHandler = new ThreadHandler(thread.getLooper());
    }

    public void addConnection(final Connection connection) {
        synchronized (mConnections) {
            if (connection != null && !mConnections.containsValue(connection)) {
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

    private Integer generateConnectionId() {
        Integer[] keys = (Integer[])mConnections.keySet().toArray();

        int maxKey = -1;

        for (int i = 0; i < keys.length ; i ++) {
            if (maxKey < keys[i]) {
                maxKey = keys[i];
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

    public interface DataSendListener {
        void onSendProgress(int percent);
        void onResult(int ret, int failedReason);
    }

    public void sendData(int connId, byte[] data, int len, long timeout,
                         final DataSendListener listener) {

    }

    public void sendFile(int connId, String strFilePathName, long timeout,
                         final DataSendListener listener) {

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

                            msg.obj = buf;
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

                        msg.obj = buf;
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
            // TODO:....
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

        @Override
        public void onDataSendingProgress(int count, int progress) {}

        @Override
        public void onDataSendFailed(int reason) {}
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

    private void notifyDataReceived(int connId, ByteString data, boolean isFile) {
        for(ConnectionManagerListener listener : mListeners) {
            listener.onDataReceived(connId, data, isFile);
        }
    }

}