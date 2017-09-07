package com.assistant.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.NetworkOnMainThreadException;
import android.os.PowerManager;
import android.text.TextUtils;

import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
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
 * [json content]: event info or file header.
 * [file content]: file content if have.
 */

public class Connection {
    private String TAG = "Connection";

    public interface ConnectionListener {
        void onConnected(Connection connection);
        void onConnectFailed(Connection connection, int reasonCode);
        void onClosed(Connection connection, int reasonCode);
    }

    private Set<ConnectionListener> mListeners =
            new CopyOnWriteArraySet<>();

    private Socket mSocket;
    private DataOutputStream mSocketOutputStream;
    private DataInputStream mSocketInputStream;

    private final Object mSocketOutputStreamLock = new Object();
    private final Object mSocketLock = new Object();

    public static final int SOCKET_DEFAULT_BUF_SIZE = 64*1024;

    public static final int CONNECTION_REASON_CODE_UNKNOWN_HOST = -1;
    public static final int CONNECTION_REASON_CODE_IO_EXCEPTION = -2;
    public static final int CONNECTION_REASON_CODE_SOCKET_SENDING = -3;
    public static final int CONNECTION_REASON_CODE_NOT_CONNECTED = -4;
    public static final int CONNECTION_REASON_CODE_SOCKET_RECEIVING= -5;
    public static final int CONNECTION_REASON_CODE_IP_ALREADY_CONNECTED = -6;
    public static final int CONNECTION_REASON_CODE_CONNECT_TIMEOUT = -7;
    public static final int CONNECTION_REASON_CODE_OUT_STREAM_CLOSED = -8;
    public static final int CONNECTION_REASON_CODE_CLOSE_MANUAL = -9;

    private int mLastReasonCode;

    public static final int CONNECTION_STATE_NOT_CONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_CLOSEED = 4;

    /*
     * indicate current connection state
     */
    private int mState;
    private Object mConnData = null;
    private String mIpAddress;
    private int mPort;

    private int mToBeClosedReason = 100;

    private int mId = -1;

    private boolean mIsHost;

    private ConnectionManager.ReConnectRequest mReconnectRequest;

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;

    private ThreadPool mThreadPool;

    private static final int HEART_BEAT_TIMESTAMP = 5*60*1000; //5 min

    private Handler mThreadHandler;
    private PowerManager.WakeLock mSendWakeLock;
    private PowerManager.WakeLock mReceiveWakeLock;
    private static final long RECEIVE_WAKE_LOCK_TIMESTAMP = 1000; //2s

    private class ThreadHandler extends Handler {
        static final int EVENT_HEART_BEAT = 0;
        static final int EVENT_CONNECTION_CLOSE = 1;

        ThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_HEART_BEAT:
                    heartBeat();
                    break;
                case EVENT_CONNECTION_CLOSE:
                    closeInteranl(msg.arg1);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    public Connection(Socket socket, boolean isHost) {
        mSocket = socket;
        mIsHost = isHost;

        if (mIsHost) {
            TAG += "-HOST";
        }

        mThreadPool = new ThreadPool(3);

        HandlerThread thread = new HandlerThread("connectionHandlerThread");
        thread.start();
        mThreadHandler = new ThreadHandler(thread.getLooper());

        if (socket != null) {
            mPort = socket.getPort();
            mIpAddress = socket.getInetAddress().getHostAddress();

            if (socket.isConnected()) {
                setState(CONNECTION_STATE_CONNECTED);
                initSocketSteam();
            } else {
                setState(CONNECTION_STATE_NOT_CONNECTED);
            }
        } else {
            setState(CONNECTION_STATE_NOT_CONNECTED);
        }
    }

    public int getState() {
        return mState;
    }

    public String getIp() {
        synchronized (mSocketLock) {
            if (TextUtils.isEmpty(mIpAddress) && mSocket != null) {
                mIpAddress = mSocket.getInetAddress().getHostAddress();
            }
        }
        return mIpAddress;
    }

    public int getPort() {
        return mPort;
    }

    public int getReconnectCount() {
        return mReconnectRequest != null ? mReconnectRequest.retryCount : 0;
    }

    public void setReconnectRequest(ConnectionManager.ReConnectRequest request) {
        mReconnectRequest = request;
    }

    public ConnectionManager.ReConnectRequest getReconnectRequest() {
        return mReconnectRequest;
    }


    public void setWakeLock(PowerManager.WakeLock sendWakeLock,
                            PowerManager.WakeLock receiveWakeLock) {
        mSendWakeLock = sendWakeLock;
        mReceiveWakeLock = receiveWakeLock;
    }

    public void setId(final int id) {
        mId = id;

        TAG = TAG + ":" + id;

        Log.d(TAG, "setId, id:" + id + ", ip:" + getIp());
    }

    public int getId() {
        return mId;
    }

    public boolean isHost() {
        return mIsHost;
    }

    public void setConnData(Object info) {
        Log.d(TAG, "setConnData, id:" + getId() + ", info:"  +info);
        mConnData = info;
    }

    public Object getConnData() {
        return mConnData;
    }

    private void setState(int state) {
        Log.d(TAG, "setState, state:" + state + ", pre state:" + mState);
        mState = state;

        if (mState == CONNECTION_STATE_CONNECTED) {
            if (mReconnectRequest == null && !isHost()) {
                Log.d(TAG, "setState, test code to close connection" + getId());
                close(CONNECTION_REASON_CODE_IO_EXCEPTION);
                return;
            }
            mReconnectRequest = null;
        }
    }

    public void connect(final String ip, final int port) {
        mPort = port;
        mIpAddress = ip;

        setState(Connection.CONNECTION_STATE_CONNECTING);

        Log.d(TAG, "Connection connect ip:" + ip + ", port:" + port);
        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Connection connect ip:" + ip + " task running)");
                createSocketAndNotify(ip, port);
            }
        });
    }

    /*
     * This function will send data synchronized. don't block main thread.
     *
     * return: data size sent or failed code for sending.
     */
    // TODO: consider how to handle sending in progress when calling this...
    public synchronized int send(final byte[] data, final long size) {
        if (!mSendWakeLock.isHeld()) {
            mSendWakeLock.acquire();
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        Log.d(TAG, "send, mState:" + mState + ", mIsDataSending:" + mIsDataSending +
                ",mSocketOutputStream:" + mSocketOutputStream);
        if (mIsDataSending) {
            return CONNECTION_REASON_CODE_SOCKET_SENDING;
        }

        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (mSocketOutputStream == null) {
            Log.d(this, "send, SocketOutputStream is not init.");

            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        mIsDataSending = true;

        int result = 0;
        int sendCountEveryTime;
        int countNotSend = (int)size;

        try {
            do {
                sendCountEveryTime = countNotSend > SOCKET_DEFAULT_BUF_SIZE ? SOCKET_DEFAULT_BUF_SIZE : countNotSend;
                synchronized (mSocketOutputStreamLock) {
                    if (mSocketOutputStream != null) {
                        mSocketOutputStream.write(data, result, sendCountEveryTime);
                    } else {
                        Log.d(this, "send, SocketOutputStream is not init.");
                        result = CONNECTION_REASON_CODE_OUT_STREAM_CLOSED;
                        break;
                    }
                }

                result += sendCountEveryTime;
                countNotSend -= sendCountEveryTime;

                Log.d(this, "send, result:" + result +
                        ", countNotSend:" + countNotSend);

                if (isValidReason(mToBeClosedReason)) {
                    break;
                }
            } while (countNotSend > 0);

            synchronized (mSocketOutputStreamLock) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();

                    // ignore previous heart beat event which only needed if there is no data traffic.
                    startHeartBeat();
                }
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();

            closeInteranl(CONNECTION_REASON_CODE_IO_EXCEPTION);
            result = CONNECTION_REASON_CODE_IO_EXCEPTION;
        }

        mIsDataSending = false;
        Log.d(TAG, "send, result:" + result);

        if (isValidReason(mToBeClosedReason)) {
            closeInteranl(mToBeClosedReason);

            if (size != result) {
                result = mToBeClosedReason;
            }
        }

        if (mSendWakeLock.isHeld()) {
            mSendWakeLock.release();
        }

        return result;
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
            Log.d(this, "receive parameter is not right! buf:" + buf + ", size:" + size);
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
            if (mSocketInputStream == null && mSocket != null) {
                try {
                    synchronized (mSocketLock) {
                        Log.d(TAG, "receive, create new stream for socket data receiving!");
                        mSocketInputStream = new DataInputStream(mSocket.getInputStream());
                    }
                    mIsDataReceiving = true;
                } catch (IOException ex) {
                    ex.printStackTrace();

                    closeInteranl(CONNECTION_REASON_CODE_IO_EXCEPTION);

                    break;
                }
            }

            if (mSocketInputStream == null) {
                break;
            }

            try {
                receivedEverytime = mSocketInputStream.read(buf, receivedCount, (int) bufSize - receivedCount);
                if (!mReceiveWakeLock.isHeld()) {
                    mReceiveWakeLock.acquire(RECEIVE_WAKE_LOCK_TIMESTAMP);
                }
            } catch (IOException e) {
                e.printStackTrace();

                closeInteranl(CONNECTION_REASON_CODE_IO_EXCEPTION);

                // end current thread if some exception happened!
                return CONNECTION_REASON_CODE_IO_EXCEPTION;
            }

            Log.d(TAG, "receive, stream read receivedEverytime:" + receivedEverytime);
            if (receivedEverytime > 0) {
                receivedCount += receivedEverytime;

                if (size == receivedCount
                        || (receivedCount == bufSize)
                        || isValidReason(mToBeClosedReason)) {
                    Log.d(TAG, "receive, size:" + size +
                            ", bufSize:" + bufSize +
                            ", receivedCount:" + receivedCount);

                    mIsDataReceiving = false;

                    break;
                }
            } else if (receivedEverytime < 0) {
                closeSocketInputStream();

                try {
                    Thread.sleep(200);
                }catch (InterruptedException e) {

                }
            } else if (receivedEverytime == 0) {
                try {
                    Thread.sleep(200);
                }catch (InterruptedException e) {

                }
            }
        }

        if (isValidReason(mToBeClosedReason)) {
            closeInteranl(mToBeClosedReason);

            return 0;
        }

        // ignore previous heart beat event which only needed if there is no data traffic.
        if (receivedCount == size) {
            startHeartBeat();
        }

        Log.d(TAG, "receive, receivedCount:" + receivedCount);

        return receivedCount;
    }

    private void createSocketAndNotify(String ip, int port) {
        int reason = 0;
        Log.d(TAG, "createSocketAndNotify, ip:" + ip + ", port:" + port);

        try {
            synchronized (mSocketLock) {
                mSocket = SocketFactory.getDefault().createSocket(ip, port);
            }
        } catch (UnknownHostException e) {
            Log.d(TAG, "createSocketAndNotify, failed:" + e.getMessage());
            e.printStackTrace();
            reason = CONNECTION_REASON_CODE_UNKNOWN_HOST;
        } catch (IOException e) {
            Log.d(TAG, "createSocketAndNotify, failed:" + e.getMessage());
            reason = CONNECTION_REASON_CODE_IO_EXCEPTION;
        }

        Log.d(TAG, "createSocketAndNotify, mState:" + mState + ", mSocket:" + mSocket);

        // connection should be already closed...
        if (mState != CONNECTION_STATE_CONNECTING
                && mState != CONNECTION_STATE_CONNECTED) {
            closeSocket();
            return;
        }

        synchronized (mSocketLock) {
            if (mSocket != null) {
                try {
                    mSocket.setKeepAlive(true);
                } catch (SocketException e) {
                    Log.d(TAG, "createSocketAndNotify, setKeepAlive exception:" + e.getMessage());
                }

                setState(CONNECTION_STATE_CONNECTED);
            }
        }

        if (mState == CONNECTION_STATE_CONNECTED) {
            initSocketSteam();

            notifyConnected();
        } else {
            closeInteranl(reason);
        }
    }

    private void initSocketSteam() {
        try {
            synchronized (mSocketLock) {
                if (mSocket != null) {
                    mSocketOutputStream = new DataOutputStream(mSocket.getOutputStream());
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();

            Log.d(TAG, "initSocketSteam, IOException, id:" + getId()
                    + ", exception:" + ex.getMessage());

            close(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
    }

    public void startHeartBeat() {
        if (mState == CONNECTION_STATE_CONNECTED) {
            mThreadHandler.removeMessages(ThreadHandler.EVENT_HEART_BEAT);
            mThreadHandler.sendEmptyMessageDelayed(ThreadHandler.EVENT_HEART_BEAT, HEART_BEAT_TIMESTAMP);
        }
    }

    private void heartBeat() {
        sendUrgentData();

        if (mState == CONNECTION_STATE_CONNECTED) {
            mThreadHandler.sendEmptyMessageDelayed(ThreadHandler.EVENT_HEART_BEAT, HEART_BEAT_TIMESTAMP);
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
                        Log.d(TAG, "initSocketSteam, IOException, id:" + getId()
                                + ", exception:" + e1.getMessage());
                        close(CONNECTION_REASON_CODE_IO_EXCEPTION);
                    }
                }
            }
        });
    }

    private synchronized void closeSocketOutputStream() {
        if (mSocketOutputStream != null) {
            synchronized (mSocketOutputStreamLock) {
                try {
                    // output stream closed, data sending will be canceled.
                    mIsDataSending = false;

                    mSocketOutputStream.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            mSocketOutputStream = null;
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
        close(CONNECTION_REASON_CODE_CLOSE_MANUAL);
    }

    public void close(int reason) {
        Log.d(TAG, "close, reason:" + reason);
        mThreadHandler.obtainMessage(ThreadHandler.EVENT_CONNECTION_CLOSE, reason, 0)
                .sendToTarget();
    }

    private boolean isValidReason(int reason) {
        return reason <= 0;
    }

    private void closeInteranl(int reason) {
        Log.d(TAG, "closeInteranl, reason:" + reason
                + ", mState:" + mState
                + ", mIsDataSending:" + mIsDataSending
                + ", mToBeClosedReason:" + mToBeClosedReason
                + ", mLastReasonCode:" + mLastReasonCode);
        if (mState == CONNECTION_STATE_CLOSEED) {
            return;
        }

        if (!mIsDataSending) {
            mToBeClosedReason = 100;
            mLastReasonCode = reason;

            closeSocket();

            setState(CONNECTION_STATE_CLOSEED);
            notifyClosed(reason);
        } else {
            mToBeClosedReason = reason;
        }
    }

    private void closeSocket() {
        closeSocketOutputStream();
        closeSocketInputStream();
        synchronized (mSocketLock) {
            if (mSocket != null && !mSocket.isClosed()) {
                try {
                    mSocket.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            mSocket = null;
        }
    }

    public static int ConnectionFailedReasonToResponseFailedCode(int failedReason) {
        int result = EventSendResponse.FAILED_CONNECTION_CLOSED;

        switch (failedReason) {
            case CONNECTION_REASON_CODE_UNKNOWN_HOST:
            case CONNECTION_REASON_CODE_NOT_CONNECTED:
            case CONNECTION_REASON_CODE_OUT_STREAM_CLOSED:
            case CONNECTION_REASON_CODE_CONNECT_TIMEOUT:
                result = EventSendResponse.FAILED_CONNECTION_CLOSED;
                break;
            case CONNECTION_REASON_CODE_IO_EXCEPTION:
                result = EventSendResponse.FAILED_CONNECTION_IO_EXCEPTION;
                break;
            case CONNECTION_REASON_CODE_SOCKET_SENDING:
                result = EventSendResponse.FAILED_CONNECTION_SENDING;
                break;
            default:
                break;
        }

        return result;
    }

    //=====
    // response implementation
    public void addListner(ConnectionListener listener) {
        if (listener != null) {
            mListeners.add(listener);

            if (mState == CONNECTION_STATE_CLOSEED) {
                notifyClosed(mLastReasonCode);
            }
        }
    }


    public void removeListener(ConnectionListener listener) {
        mListeners.remove(listener);
    }

    private void notifyConnected() {
        for (ConnectionListener listener : mListeners) {
            listener.onConnected(this);
        }
    }

    private void notifyConnectFailed(int reason) {
        for (ConnectionListener listener : mListeners) {
            listener.onConnectFailed(this, reason);
        }
    }

    private void notifyClosed(int reason) {
        for (ConnectionListener listener : mListeners) {
            listener.onClosed(this, reason);
        }
    }
}
