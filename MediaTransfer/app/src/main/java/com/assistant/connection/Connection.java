package com.assistant.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.NetworkOnMainThreadException;

import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.net.SocketFactory;

/**
 * This connection will be send/receive data using below format:
 * [Data header] + [json content] + [file content](if have)
 *
 * [Data header], custom length of data.
 * version 1(22 bytes):"[v:(long);j:(long);f:(long)]"
 *      v: version num. long type.
 *      j: json length. long type.
 *      f: file length. long type.
 *
 * [json content]: command or file header.
 * [file content]: file content if have.
 */

public class Connection {
    public interface ConnectionListener {
        void onConnected();
        void onConnectFailed(int reasonCode);
        void onClosed(int reasonCode);
    }

    private Set<ConnectionListener> mListeners =
            new CopyOnWriteArraySet<ConnectionListener>();

    private Socket mSocket;
    private DataOutputStream mSocketOutputStream;
    private DataInputStream mSocketInputStream;

    private Object mSocketOutputStreamLock = new Object();
    private Object mSocketLock = new Object();

    public static final int SOCKET_DEFAULT_BUF_SIZE = 64*1024;

    public static final int CONNECTION_REASON_CODE_UNKNOWN_HOST = -1;
    public static final int CONNECTION_REASON_CODE_IO_EXCEPTION = -2;
    public static final int CONNECTION_REASON_CODE_SOCKET_SENDING = -3;
    public static final int CONNECTION_REASON_CODE_NOT_CONNECTED = -4;
    public static final int CONNECTION_REASON_CODE_SOCKET_RECEIVING= -5;

    private int mLastReasonCode;

    public static final int CONNECTION_STATE_NOT_CONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_CLOSE_MANUAL = 3;

    /*
     * indicate current connection state
     */
    private int mState;
    private Object mConnData = null;

    private int mId = -1;

    // TODO: this uniqueId should be always same for same client.
    // which should be sent to each other when connected.
    // CURRENT STEP1: we only try to make sure it's unique for all connections.
    private String mUniqueId;

    private boolean mIsHost;

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;

    private ThreadPool mThreadPool;

    private static final int EVENT_HEART_BEAT = 0;

    private static final int HEART_BEAT_TIMESTAMP = 5*60*1000; //5 min

    private Handler mThreadHandler;

    public Connection(Socket socket, boolean isHost) {
        mSocket = socket;
        mIsHost = isHost;

        if (socket != null) {
            if (socket.isConnected()) {
                mState = CONNECTION_STATE_CONNECTED;
                initSocketSteam();
            } else {
                mState = CONNECTION_STATE_NOT_CONNECTED;
            }
        } else {
            mState = CONNECTION_STATE_NOT_CONNECTED;
        }

        mThreadPool = new ThreadPool(2);

        HandlerThread thread = new HandlerThread("connectionHandlerThread");
        thread.start();
        mThreadHandler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case EVENT_HEART_BEAT:
                        heartBeat();
                    default:
                        break;
                }
                super.handleMessage(msg);
            }
        };
    }

    public int getState() {
        return mState;
    }

    public String getIp() {
        if (mSocket != null) {
            return mSocket.getInetAddress().getHostAddress();
        }
        return "";
    }

    public void setId(final int id) {
        mId = id;
        mUniqueId = System.currentTimeMillis() + ":" + mId;
    }

    public int getId() {
        return mId;
    }

    public void setUniqueId(String uniqueId) {
        mUniqueId = uniqueId;
    }

    public String getUniqueId() {
        return mUniqueId;
    }

    public boolean isHost() {
        return mIsHost;
    }

    public void setConnData(Object info) {
        mConnData = info;
    }

    public Object getConnData() {
        return mConnData;
    }

    public void connect(final String ip, final int port) {
        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                createSocketAndNotify(ip, port);
            }
        });
    }

    /*
     * This function will send data synchronized. don't block main thread.
     *
     * return: data size sent or failed code for sending.
     */
    public int send(final byte[] data, final long size) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }
        if (mIsDataSending) {
            return CONNECTION_REASON_CODE_SOCKET_SENDING;
        }

        if (mSocketOutputStream == null) {
            Log.d(this, "send, SocketOutputStream is not init.");

            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        mIsDataSending = true;

        int sentCount = 0;
        int sendCountEveryTime;
        int countNotSend = (int)size;

        try {
            do {
                sendCountEveryTime = countNotSend > SOCKET_DEFAULT_BUF_SIZE ? SOCKET_DEFAULT_BUF_SIZE : countNotSend;
                synchronized (mSocketOutputStreamLock) {
                    if (mSocketOutputStream != null) {
                        mSocketOutputStream.write(data, sentCount, sendCountEveryTime);
                    } else {
                        Log.d(this, "send, SocketOutputStream is not init.");
                        break;
                    }
                }

                sentCount += sendCountEveryTime;
                countNotSend -= sendCountEveryTime;

                Log.d(this, "send, sentCount:" + sentCount +
                        ", countNotSend:" + countNotSend);
            } while (countNotSend > 0);

            mIsDataSending = false;

            synchronized (mSocketOutputStreamLock) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();

                    // ignore previous heart beat event which only needed if there is no data traffic.
                    startHeartBeat();
                }
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }

        return sentCount;
    }

    /*
     * Block and receive bytes which can be hold by cache or specify count.
     *
     * throw: NetworkOnMainThreadException if this function is called in main thread.
     */
    public int receive(byte[] buf, long size) {
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        if (buf == null || size <= 0) {
            Log.d(this, "receive parameter is not right!");
            return 0;
        }

        if (mIsDataReceiving) {
            return CONNECTION_REASON_CODE_SOCKET_RECEIVING;
        }

        mIsDataReceiving = true;

        int receivedCount = 0;
        int receivedEverytime;

        //ByteString byteString = ByteStringPool.getInstance().getByteString();

        long bufSize = size; //byteString.getBufByteSize();

        while (true) {
            if (mSocketInputStream == null) {
                break;
            }
            try {
                receivedEverytime = mSocketInputStream.read(buf, receivedCount, (int)bufSize - receivedCount);
            } catch (IOException e) {
                e.printStackTrace();

                closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);

                // end current thread if some exception happened!
                return CONNECTION_REASON_CODE_IO_EXCEPTION;
            }

            receivedCount += receivedEverytime;

            // TODO: log received data details for debug

            if (size == receivedCount || (receivedCount == bufSize)) {
                mIsDataReceiving = false;

                break;
            }
        }

        // ignore previous heart beat event which only needed if there is no data traffic.
        startHeartBeat();

        return receivedCount;
    }

    private synchronized void createSocketAndNotify(String ip, int port) {
        try {
            mSocket = SocketFactory.getDefault().createSocket(ip, port);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            notifyConnectFailed(CONNECTION_REASON_CODE_UNKNOWN_HOST);
        } catch (IOException e) {
            e.printStackTrace();
            notifyConnectFailed(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }

        initSocketSteam();

        notifyConnected();
    }

    private void initSocketSteam() {
        try {
            mSocketInputStream = new DataInputStream(mSocket.getInputStream());
            mSocketOutputStream = new DataOutputStream(mSocket.getOutputStream());
        } catch (IOException ex) {
            ex.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
    }

    public void startHeartBeat() {
        mThreadHandler.removeMessages(EVENT_HEART_BEAT);
        mThreadHandler.sendEmptyMessageDelayed(EVENT_HEART_BEAT, HEART_BEAT_TIMESTAMP);
    }

    private void heartBeat() {
        sendUrgentData();

        if (mState == CONNECTION_STATE_CONNECTED) {
            mThreadHandler.sendEmptyMessageDelayed(EVENT_HEART_BEAT, HEART_BEAT_TIMESTAMP);
        }
    }
    /*
     * used for socket heart-beat checking.
     */
    private void sendUrgentData() {
        mThreadPool.addTask(new Runnable() {

            @Override
            public void run() {
                synchronized (mSocketLock) {
                    if ((mSocket == null) || (mSocket.isClosed())) {
                        return;
                    }

                    // also use urgent data
                    try {
                        mSocket.sendUrgentData(0xFF);
                    } catch (IOException e1) {
                        e1.printStackTrace();

                        closeInteranl(CONNECTION_REASON_CODE_IO_EXCEPTION);
                    }
                }
            }
        });
    }

    private synchronized void closeSocketOutputStream() {
        synchronized (mSocketOutputStreamLock) {
            if (mSocketOutputStream != null) {
                try {
                    // output stream closed, data sending will be canceled.
                    mIsDataSending = false;

                    mSocketInputStream.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            mSocketInputStream = null;
        }
    }

    private synchronized void closeSocketInputStream() {
        if (mSocketInputStream != null) {
            try {
                mIsDataReceiving = false;

                mSocketInputStream.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            mSocketInputStream = null;
        }
    }

    public void close() {
        closeInteranl(CONNECTION_STATE_CLOSE_MANUAL);
    }

    private void closeInteranl(int reason) {
        closeSocket(reason);

        mListeners.clear();
    }

    private void closeSocket(int reason) {
        mLastReasonCode = reason;

        closeSocketOutputStream();
        closeSocketInputStream();

        if (mSocket != null && !mSocket.isClosed()) {
            try {
                mSocket.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        mState = CONNECTION_STATE_NOT_CONNECTED;
        mSocket = null;

        notifyClosed(CONNECTION_REASON_CODE_IO_EXCEPTION);
    }

    //=====
    // listener implementation
    public void addListner(ConnectionListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ConnectionListener listener) {
        mListeners.remove(listener);
    }

    private void notifyConnected() {
        for(ConnectionListener listener : mListeners) {
            listener.onConnected();
        }
    }

    private void notifyConnectFailed(int reason) {
        for(ConnectionListener listener : mListeners) {
            listener.onConnectFailed(reason);
        }
    }

    private void notifyClosed(int reason) {
        for(ConnectionListener listener : mListeners) {
            listener.onClosed(reason);
        }
    }
}
