package com.assistant.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.NetworkOnMainThreadException;

import com.assistant.bytestring.ByteString;
import com.assistant.bytestring.ByteStringPool;
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
 * Created by alex on 17-8-5.
 */

public class Connection {

    public interface ConnectionListener {
        void onConnected();
        void onConnectFailed(int reasonCode);
        void onClosed(int reasonCode);

        /*
         * count - data bytes has been sent.
         * progress - percent data for data have been sent.
         */
        void onDataSendingProgress(int count, int progress);
        void onDataSendFailed(int reason);

        void onDataReceived(ByteString string, int size);
    }

    private Set<ConnectionListener> mListeners =
            new CopyOnWriteArraySet<ConnectionListener>();

    private Socket mSocket;
    private DataOutputStream mSocketOutputStream;
    private DataInputStream mSocketInputStream;

    private Object mSocketOutputStreamLock = new Object();
    private Object mSocketLock = new Object();

    public static final int SOCKET_DEFAULT_BUF_SIZE = 64*1024;

    public static final int CONNECTION_REASON_CODE_BASE = 0;
    public static final int CONNECTION_REASON_CODE_UNKNOWN_HOST = CONNECTION_REASON_CODE_BASE + 1;
    public static final int CONNECTION_REASON_CODE_IO_EXCEPTION = CONNECTION_REASON_CODE_BASE + 2;
    public static final int CONNECTION_REASON_CODE_SOCKET_SENDING = CONNECTION_REASON_CODE_BASE + 3;
    public static final int CONNECTION_REASON_CODE_NOT_CONNECTED = CONNECTION_REASON_CODE_BASE + 4;
    public static final int CONNECTION_REASON_CODE_SOCKET_RECEIVING= CONNECTION_REASON_CODE_BASE + 5;
    public static final int CONNECTION_REASON_CODE_SEND_TIMEOUT = CONNECTION_REASON_CODE_BASE + 6;

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

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;

    ThreadPool mThreadPool;

    private static final int EVENT_DATA_SENDING_PROGRESS = 0;
    private static final int EVENT_DATA_SEND_FAILED = 1;
    private static final int EVENT_DATA_RECEIVED = 2;
    private static final int EVENT_DATA_SEND_TIMEOUT = 3;

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
                    case EVENT_DATA_RECEIVED:
                        notifyDataReceived((ByteString) msg.obj, msg.arg1);
                        break;
                    case EVENT_DATA_SEND_TIMEOUT:
                        notifyDataSendFailed(CONNECTION_REASON_CODE_SEND_TIMEOUT);

                        closeInteranl(CONNECTION_REASON_CODE_SEND_TIMEOUT);
                        break;
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

    public void connect(final String ip, final int port) {
        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                createSocketAndNotify(ip, port);
            }
        });
    }

    public int listen(int port) {
        return 0;
    }

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
     * Note: Please don't call this interface in main thread otherwise
     * NetworkOnMainThreadException will be thrown.
     *
     */
    public int receive(ByteString byteString, int count) {
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        if (byteString == null || count <= 0) {
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

        int bufSize = byteString.getBufByteSize();

        while (true) {
            if (mSocketInputStream == null) {
                break;
            }
            try {
                receivedEverytime = mSocketInputStream.read(byteString.data, receivedCount, bufSize - receivedCount);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();

                byteString.release();
                closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);

                // end current thread if some exception happened!
                return CONNECTION_REASON_CODE_IO_EXCEPTION;
            }

            receivedCount += receivedEverytime;

            // TODO: log received data details for debug

            if (count == receivedCount || (receivedCount == bufSize)) {
                Message msg = mThreadHandler.
                        obtainMessage(EVENT_DATA_RECEIVED, byteString);

                msg.arg1 = receivedCount;

                msg.sendToTarget();

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

    /*
     * used for socket heart-beat checking.
     */
    public void sendUrgentData() {
        mThreadPool.addTask(new Runnable() {

            @Override
            public void run() {
                // TODO: add synchronized for socket operation.
                // connection already closed. no need to check!
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

    private void notifyDataReceived(ByteString string, int size) {
        for(ConnectionListener listener : mListeners) {
            listener.onDataReceived(string, size);
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
