package com.assistant.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.NetworkOnMainThreadException;
import android.os.PowerManager;
import android.text.TextUtils;

import com.assistant.utils.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
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

    public static final int CONNECTION_REASON_CODE_UNKNOWN = -1;
    public static final int CONNECTION_REASON_CODE_NOT_CONNECTED = -4;
    public static final int CONNECTION_REASON_CODE_IO_EXCEPTION = -2;
    public static final int CONNECTION_REASON_CODE_SOCKET_SENDING = -3;
    public static final int CONNECTION_REASON_CODE_SOCKET_RECEIVING= -5;
    public static final int CONNECTION_REASON_CODE_OUT_STREAM_CLOSED = -8;
    public static final int CONNECTION_REASON_CODE_CLOSE_MANUAL = -9;

    public static final int CONNECTION_REASON_CODE_CONNECT_UNKNOWN_HOST = -20;
    public static final int CONNECTION_REASON_CODE_CONNECT_TIMEOUT = -21;
    public static final int CONNECTION_REASON_CODE_CONNECT_REQUEST_EXISTED = -22;
    public static final int CONNECTION_REASON_CODE_CONNECT_REQUEST_CANCELED = -23;
    public static final int CONNECTION_REASON_CODE_CONNECT_ALREADY_CONNECTED = -24;

    private int mLastReasonCode;

    public static final int CONNECTION_STATE_NOT_CONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_CLOSEED = 4;

    /*
     * indicate current connection state
     */
    private int mState;
    private int mId = -1;
    private boolean mIsHost;
        private Object mConnData = null;
    private String mIpAddress;
    private int mPort;

    private int mToBeClosedReason = 0;

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;

    private static final long RECEIVE_WAKE_LOCK_TIMESTAMP = 2000; //2s
    private static final int HEART_BEAT_TIMESTAMP = 5*60*1000; //5 min

    private Handler mThreadHandler;
    private Object mSendBlockObj = new Object();
    private PowerManager.WakeLock mSendWakeLock;
    private PowerManager.WakeLock mReceiveWakeLock;

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

    public Connection(int connId, Socket socket, boolean isHost) {
        Log.d(TAG, "connId:" + connId + ", socket:" + socket + ", isHost:" + isHost);
        mSocket = socket;
        mIsHost = isHost;
        if (mIsHost) {
            TAG += "-HOST";
        }

        if (connId >= 0) {
            setId(connId);
        }

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

        Log.d(TAG, "init ended!");
    }

    public int getState() {
        return mState;
    }

    public String getIp() {
        if (TextUtils.isEmpty(mIpAddress) && mSocket != null) {
            mIpAddress = mSocket.getInetAddress().getHostAddress();
        }
        return mIpAddress;
    }

    public int getPort() {
        return mPort;
    }

    public void setWakeLock(PowerManager.WakeLock sendWakeLock,
                            PowerManager.WakeLock receiveWakeLock) {
        mSendWakeLock = sendWakeLock;
        mReceiveWakeLock = receiveWakeLock;
    }

    public void acquireSendWakeLock() {
        if (mSendWakeLock != null && !mSendWakeLock.isHeld()) {
            mSendWakeLock.acquire();
        }
    }

    public void releaseSendWakeLock() {
        if (mSendWakeLock != null && mSendWakeLock.isHeld()) {
            mSendWakeLock.release();
        }
    }

    public void acquireReceivWakeLock() {
        Log.d(TAG, "acquireReceivWakeLock before");
        if (mReceiveWakeLock != null && !mReceiveWakeLock.isHeld()) {
            mReceiveWakeLock.acquire(RECEIVE_WAKE_LOCK_TIMESTAMP);
        }
        Log.d(TAG, "acquireReceivWakeLock end");
    }

    public void setId(final int id) {
        Log.d(TAG, "setId, id:" + id + ", ip:" + getIp());

        if (mId != id) {
            TAG = TAG + "-" + id;
            mId = id;
        }
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
    }

    public void connect(final String ip, final int port) {
        mPort = port;
        mIpAddress = ip;

        setState(Connection.CONNECTION_STATE_CONNECTING);

        Log.d(TAG, "Connection connect ip:" + ip + ", port:" + port);

        createSocketAndNotify(ip, port);
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

        Log.d(TAG, "send, mState:" + mState + ", mIsDataSending:" + mIsDataSending +
                ",mSocketOutputStream:" + mSocketOutputStream);

        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (mSocketOutputStream == null) {
            Log.d(TAG, "send, SocketOutputStream is not init.");

            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        int result = 0;
        int sendCountEveryTime;
        int countNotSend = (int)size;

        try {
            synchronized (mSendBlockObj) {
                mIsDataSending = true;
                do {
                    sendCountEveryTime = countNotSend > SOCKET_DEFAULT_BUF_SIZE ? SOCKET_DEFAULT_BUF_SIZE : countNotSend;
                    synchronized (mSocketOutputStreamLock) {
                        if (mSocketOutputStream != null) {
                            mSocketOutputStream.write(data, result, sendCountEveryTime);
                        } else {
                            Log.d(TAG, "send, SocketOutputStream is not init.");
                            result = CONNECTION_REASON_CODE_OUT_STREAM_CLOSED;
                            break;
                        }
                    }

                    result += sendCountEveryTime;
                    countNotSend -= sendCountEveryTime;

                    Log.d(TAG, "send, result:" + result +
                            ", countNotSend:" + countNotSend);

                    if (isValidReason(mToBeClosedReason)) {
                        break;
                    }
                } while (countNotSend > 0);

                mIsDataSending = false;
            }

            Log.d(TAG, "send, end:" + result);

            synchronized (mSocketOutputStreamLock) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();

                    // ignore previous heart beat event which only needed if there is no data traffic.
                    startHeartBeat();
                }
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();
            mIsDataSending = false;

            mToBeClosedReason = CONNECTION_REASON_CODE_IO_EXCEPTION;
        }

        if (isValidReason(mToBeClosedReason)) {
            closeInteranl(mToBeClosedReason);

            if (size != result) {
                result = mToBeClosedReason;
            }
        }

        Log.d(TAG, "send, result:" + result + ", mToBeClosedReason:" + mToBeClosedReason);

        return result;
    }

    public int sendFile(final String filePathName) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        Log.d(TAG, "send, mState:" + mState + ", mIsDataSending:" + mIsDataSending +
                ",mSocketOutputStream:" + mSocketOutputStream);
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (mSocketOutputStream == null) {
            Log.d(TAG, "send, SocketOutputStream is not init.");

            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        FileInputStream fileInputStream;

        int fileLen = 0;
        int result = 0;
        int sentBytes = 0;

        try {
            fileInputStream = new FileInputStream(filePathName);
            fileLen = fileInputStream.available();

            int bufferSize =
                    fileLen > SOCKET_DEFAULT_BUF_SIZE ?
                            SOCKET_DEFAULT_BUF_SIZE : fileLen;

            byte[] buffer = new byte[bufferSize];
            int bytesFileRead;

            synchronized (mSendBlockObj) {
                mIsDataSending = true;

                do {
                    bytesFileRead = fileInputStream.read(buffer, 0, bufferSize);

                    // to the end of the file
                    if (bytesFileRead <= 0) {
                        break;
                    }

                    synchronized (mSocketOutputStreamLock) {
                        if (mSocketOutputStream != null) {
                            mSocketOutputStream.write(buffer, 0, bytesFileRead);
                        } else {
                            Log.d(TAG, "send, SocketOutputStream is not init.");
                            result = CONNECTION_REASON_CODE_OUT_STREAM_CLOSED;
                            break;
                        }
                    }

                    Log.d(TAG, "send, sentBytes:" + sentBytes +
                            ", countNotSend:" + (fileLen - sentBytes));


                    sentBytes += bytesFileRead;

                    if (isValidReason(mToBeClosedReason)) {
                        Log.d(TAG, "connection is requested to be closed:"
                                + mToBeClosedReason
                                + ", sentBytes:" + sentBytes);
                        break;
                    }
                } while (sentBytes < fileLen);

                mIsDataSending = false;
            }

            synchronized (mSocketOutputStreamLock) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();

                    // ignore previous heart beat event which only needed if there is no data traffic.
                    startHeartBeat();
                }
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();
            mIsDataSending = false;

            mToBeClosedReason = CONNECTION_REASON_CODE_IO_EXCEPTION;
        }

        Log.d(TAG, "send, result:" + result);

        if (isValidReason(mToBeClosedReason)) {
            closeInteranl(mToBeClosedReason);

            if (fileLen != sentBytes) {
                result = mToBeClosedReason;
            }
        } else {
            result = sentBytes;
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
            Log.d(TAG, "receive, connection closed!");
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.d(TAG, "receive, in main loop!");
            throw new NetworkOnMainThreadException();
        }

        if (buf == null || size <= 0) {
            Log.d(TAG, "receive parameter is not right! buf:" + buf + ", size:" + size);
            return CONNECTION_REASON_CODE_UNKNOWN;
        }

        if (mIsDataReceiving) {
            Log.d(TAG, "receive, is in receiving!");
            return CONNECTION_REASON_CODE_SOCKET_RECEIVING;
        }

        mIsDataReceiving = true;

        int receivedCount = 0;
        int receivedEverytime;

        //ByteString byteString = ByteStringPool.getInstance().getByteString();

        long bufSize = size; //byteString.getBufByteSize();

        Log.d(TAG, "receive, size:" + size);
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
                Log.d(TAG, "receive, mSocketInputStream is null.");
                break;
            }

            try {
                Log.d(TAG, "receive, expected:" + size);
                receivedEverytime = mSocketInputStream.read(buf, receivedCount, (int) bufSize - receivedCount);
                Log.d(TAG, "receive, receivedEverytime:" + receivedEverytime);
            } catch (IOException e) {
                closeInteranl(CONNECTION_REASON_CODE_IO_EXCEPTION);

                Log.d(TAG, "receive, receive exception:" + e.getMessage());

                // end current thread if some exception happened!
                return CONNECTION_REASON_CODE_IO_EXCEPTION;
            }

            Log.d(TAG, "receive, stream read receivedEverytime:" + receivedEverytime);
            if (receivedEverytime > 0) {
                acquireReceivWakeLock();
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

                receivedCount = receivedEverytime;
                break;
            } else if (receivedEverytime == 0) {
                try {
                    Thread.sleep(200);
                }catch (InterruptedException e) {

                }
            }
        }

        if (isValidReason(mToBeClosedReason)) {
            closeInteranl(mToBeClosedReason);

            return CONNECTION_REASON_CODE_NOT_CONNECTED;
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
            reason = CONNECTION_REASON_CODE_CONNECT_UNKNOWN_HOST;
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
/*        if (mState == CONNECTION_STATE_CONNECTED) {
            mThreadHandler.removeMessages(ThreadHandler.EVENT_HEART_BEAT);
            mThreadHandler.sendEmptyMessageDelayed(ThreadHandler.EVENT_HEART_BEAT, HEART_BEAT_TIMESTAMP);
        }*/
    }

    private void heartBeat() {
        sendUrgentData();

        startHeartBeat();
    }
    /*
     * used for socket heart-beat checking.
     */
    private void sendUrgentData() {
/*        mThreadPool.addTask(new Runnable() {

            @Override
            public void run() {
                synchronized (mSocketLock) {
                    if ((mSocket == null) || (mSocket.isClosed())) {
                        return;
                    }

                    // also use urgent data
                    // TODO: This urgent data can be received by client. think another way...
                    try {
                        mSocket.sendUrgentData(0xFF);
                    } catch (IOException e1) {
                        Log.d(TAG, "initSocketSteam, IOException, id:" + getId()
                                + ", exception:" + e1.getMessage());
                        close(CONNECTION_REASON_CODE_IO_EXCEPTION);
                    }
                }
            }
        });*/
    }

    private void closeSocketOutputStream() {
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

    private void closeSocketInputStream() {
        if (mSocketInputStream != null) {

            try {
                synchronized (mSocketInputStream) {
                    mIsDataReceiving = false;

                    mSocketInputStream.close();
                }
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

    public boolean isClosed() {
        return mState == CONNECTION_STATE_CLOSEED || mToBeClosedReason < 0;
    }

    private boolean isValidReason(int reason) {
        return reason < 0;
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
            mToBeClosedReason = 0;
            mLastReasonCode = reason;

            closeSocket();

            if (mSendWakeLock != null && mSendWakeLock.isHeld()) {
                mSendWakeLock.release();
            }

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
        int result = failedReason;

        switch (failedReason) {
            case CONNECTION_REASON_CODE_CONNECT_UNKNOWN_HOST:
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
    // responseList implementation
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
