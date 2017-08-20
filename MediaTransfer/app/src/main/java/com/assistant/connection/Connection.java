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

        // TODO: move it out as one separate data sending listener.
        /*
         * count - data bytes has been sent.
         * progress - percent data for data have been sent.
         */
        void onDataSendingProgress(int count, int progress);
        void onDataSendFailed(int reason);
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
    public static final int CONNECTION_REASON_CODE_SEND_TIMEOUT = -6;

    private int mLastReasonCode;

    public static final int CONNECTION_STATE_NOT_CONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_CLOSING = 3;
    public static final int CONNECTION_STATE_CLOSE_MANUAL = 4;

    /*
     * indicate current connection state
     */
    private int mState;

    private int mId = -1;

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;

    ThreadPool mThreadPool;

    private static final int EVENT_DATA_SENDING_PROGRESS = 0;
    private static final int EVENT_DATA_SEND_FAILED = 1;
    private static final int EVENT_DATA_SEND_TIMEOUT = 2;
    private static final int EVENT_HEART_BEAT = 3;

    private static final int HEART_BEAT_TIMESTAMP = 5*60*1000; //5 min

    Handler mThreadHandler;

    public Connection(Socket socket) {
        mSocket = socket;

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

        mThreadPool = new ThreadPool(5);

        HandlerThread thread = new HandlerThread("connectionHandlerThread");
        mThreadHandler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case EVENT_DATA_SENDING_PROGRESS:
                        notifyDataSendingProgress(msg.arg1, msg.arg2);
                        break;
                    case EVENT_DATA_SEND_FAILED:
                        notifyDataSendFailed(msg.arg1);
                        break;
                    case EVENT_DATA_SEND_TIMEOUT:
                        notifyDataSendFailed(CONNECTION_REASON_CODE_SEND_TIMEOUT);

                        closeInteranl(CONNECTION_REASON_CODE_SEND_TIMEOUT);
                        break;
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
    }

    public int getId() {
        return mId;
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
     * This function will be send data to the other side of this socket in thread pool.
     */
    public int send(final byte[] data, final int count, final long timeout) {
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }
        if (mIsDataSending) {
            return CONNECTION_REASON_CODE_SOCKET_SENDING;
        }

        mIsDataSending = true;

        if (timeout > 0) {
            mThreadHandler.sendEmptyMessageDelayed(EVENT_DATA_SEND_TIMEOUT, timeout);
        }

        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                sendDataInternal(data, count);
            }
        });

        return 0;
    }

    private void sendDataInternal(final byte[] data, final int count) {
        try {
            mSocketOutputStream = new DataOutputStream(mSocket.getOutputStream());
        } catch (IOException ioe) {
            mThreadHandler.
                    obtainMessage(EVENT_DATA_SEND_FAILED,
                            CONNECTION_REASON_CODE_IO_EXCEPTION, 0).
                    sendToTarget();
            Log.d(this, "sendDataInternal, SocketOutputStream is not init.");

            return;
        }

        if (mSocketOutputStream == null) {
            mThreadHandler.
                    obtainMessage(EVENT_DATA_SEND_FAILED,
                            CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                    sendToTarget();
            Log.d(this, "sendDataInternal, SocketOutputStream is not init.");

            return;
        }

        int sentCount = 0;
        int sendCountEveryTime;
        int countNotSend = count;

        try {
            do {
                sendCountEveryTime = countNotSend > SOCKET_DEFAULT_BUF_SIZE ? SOCKET_DEFAULT_BUF_SIZE : countNotSend;
                synchronized (mSocketOutputStreamLock) {
                    if (mSocketOutputStream != null) {
                        mSocketOutputStream.write(data, sentCount, sendCountEveryTime);
                    } else {
                        mThreadHandler.
                                obtainMessage(EVENT_DATA_SEND_FAILED,
                                        CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                                sendToTarget();
                        Log.d(this, "sendDataInternal, SocketOutputStream is not init.");
                        break;
                    }
                }

                sentCount += sendCountEveryTime;
                countNotSend -= sendCountEveryTime;

                Log.d(this, "sendDataInternal, sentCount:" + sentCount +
                        ", countNotSend:" + countNotSend);

                mThreadHandler.
                        obtainMessage(EVENT_DATA_SENDING_PROGRESS, sentCount,
                                (sentCount * 100) / count).
                        sendToTarget();
            } while (countNotSend > 0);

            mThreadHandler.removeMessages(EVENT_DATA_SEND_TIMEOUT);

            mIsDataSending = false;

            synchronized (mSocketOutputStreamLock) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();
                } else {
                    mThreadHandler.
                            obtainMessage(EVENT_DATA_SEND_FAILED,
                                    CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                            sendToTarget();
                    Log.d(this, "sendDataInternal, SocketOutputStream is not init.");
                }

                mSocketOutputStream.close();
                mSocketOutputStream = null;
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
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

        if (Looper.myLooper() != Looper.getMainLooper()) {
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
                // TODO Auto-generated catch block
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

        return receivedCount;
    }

    private synchronized void createSocketAndNotify(String ip, int port) {
        try {
            mSocket = SocketFactory.getDefault().createSocket(ip, port);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            notifyConnectFailed(CONNECTION_REASON_CODE_UNKNOWN_HOST);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            notifyConnectFailed(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }

        initSocketSteam();

        notifyConnected();
    }

    private void initSocketSteam() {
        try {
            mSocketInputStream = new DataInputStream(mSocket.getInputStream());
        } catch (IOException ex) {
            ex.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
    }

    public void startHeartBeat() {
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
                        // TODO Auto-generated catch block
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

    private void notifyDataSendFailed(int reason) {
        for(ConnectionListener listener : mListeners) {
            listener.onDataSendFailed(reason);
        }
    }

    private void notifyClosed(int reason) {
        for(ConnectionListener listener : mListeners) {
            listener.onClosed(reason);
        }
    }

    private void notifyDataSendingProgress(int count, int progress) {
        for(ConnectionListener listener : mListeners) {
            listener.onDataSendingProgress(count, progress);
        }
    }
}
