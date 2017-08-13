package com.assistant.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.assistant.bytestring.ByteString;
import com.assistant.bytestring.ByteStringPool;
import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
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

    public static final int SOCKET_DEFAULT_BUF_SIZE = 64*1024;

    public static final int CONNECTION_REASON_CODE_BASE = 0;
    public static final int CONNECTION_REASON_CODE_UNKNOWN_HOST = CONNECTION_REASON_CODE_BASE + 1;
    public static final int CONNECTION_REASON_CODE_IO_EXCEPTION = CONNECTION_REASON_CODE_BASE + 2;
    public static final int CONNECTION_REASON_CODE_SOCKET_SENDING = CONNECTION_REASON_CODE_BASE + 3;
    public static final int CONNECTION_REASON_CODE_NOT_CONNECTED = CONNECTION_REASON_CODE_BASE + 4;
    public static final int CONNECTION_REASON_CODE_SOCKET_RECEIVING= CONNECTION_REASON_CODE_BASE + 5;

    private int mLastReasonCode;

    public static final int CONNECTION_STATE_NOT_CONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_CLOSING = 3;
    /*
     * indicate current connection state
     */
    private int mState;

    private boolean mIsDataSending;
    private boolean mIsDataReceiving;
    private int mExpectedDataSize = 0;

    ThreadPool mThreadPool;

    private static final int EVENT_DATA_SENDING_PROGRESS = 0;
    private static final int EVENT_DATA_SEND_FAILED = 1;
    private static final int EVENT_DATA_RECEIVED = 2;

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

    public int send(final byte[] data, final int count) {
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }
        if (mIsDataSending) {
            return CONNECTION_REASON_CODE_SOCKET_SENDING;
        }


        mIsDataSending = true;

        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                sendDataInternal(data, count);
            }
        });

        return 0;
    }

    public int read(final int count) {
        if (mState != CONNECTION_STATE_CONNECTED) {
            return CONNECTION_REASON_CODE_NOT_CONNECTED;
        }

        if (mIsDataReceiving) {
            return CONNECTION_REASON_CODE_SOCKET_RECEIVING;
        }

        mIsDataReceiving = true;

        mExpectedDataSize = count;

        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                receivingDataInternal(count);
            }
        });

        return 0;
    }

    private void receivingDataInternal(int count) {
        int receivedCount = 0;
        int receiveEverytime = 0;

        ByteString byteString = ByteStringPool.getInstance().getByteString();

        int bufSize = byteString.getBufByteSize();
        int bufOffset = 0;

        while (true) {
            try {
                receiveEverytime = mSocketInputStream.read(byteString.data, bufOffset, bufSize - bufOffset);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();

                byteString.release();
                closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);

                // end current thread if some exception happened!
                return;
            }

            bufOffset += receiveEverytime;
            receivedCount += receiveEverytime;

            // TODO: log received data details for debug

            if (count == receivedCount || (bufOffset == bufSize)) {
                Message msg = mThreadHandler.
                        obtainMessage(EVENT_DATA_RECEIVED, byteString);

                msg.arg1 = bufOffset;

                msg.sendToTarget();

                if (count == receivedCount) {
                    mIsDataReceiving = false;

                    break;
                }

                byteString = ByteStringPool.getInstance().getByteString();

                bufSize = byteString.getBufByteSize();
                bufOffset = 0;
            }
        }
    }

    private void sendDataInternal(final byte[] data, final int count) {
        if (mSocketOutputStream == null) {
            mThreadHandler.
                    obtainMessage(EVENT_DATA_SEND_FAILED,
                            CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                    sendToTarget();
            Log.logd(this, "sendDataInternal, SocketOutputStream is not init.");

            return;
        }

        int sentCount = 0;
        int sendCountEveryTime;
        int countNotSend = count;

        try {
            do {
                sendCountEveryTime = countNotSend > SOCKET_DEFAULT_BUF_SIZE ? SOCKET_DEFAULT_BUF_SIZE : countNotSend;
                synchronized (mSocketOutputStream) {
                    if (mSocketOutputStream != null) {
                        mSocketOutputStream.write(data, sentCount, sendCountEveryTime);
                    } else {
                        mThreadHandler.
                                obtainMessage(EVENT_DATA_SEND_FAILED,
                                        CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                                sendToTarget();
                        Log.logd(this, "sendDataInternal, SocketOutputStream is not init.");
                        break;
                    }
                }

                sentCount += sendCountEveryTime;
                countNotSend -= sendCountEveryTime;

                Log.logd(this, "sendDataInternal, sentCount:" + sentCount +
                        ", countNotSend:" + countNotSend);

                mThreadHandler.
                        obtainMessage(EVENT_DATA_SENDING_PROGRESS, sentCount,
                                (sentCount * 100) / count).
                        sendToTarget();
            } while (countNotSend > 0);

            mIsDataSending = false;

            synchronized (mSocketOutputStream) {
                if (mSocketOutputStream != null) {
                    mSocketOutputStream.flush();
                } else {
                    mThreadHandler.
                            obtainMessage(EVENT_DATA_SEND_FAILED,
                                    CONNECTION_REASON_CODE_NOT_CONNECTED, 0).
                            sendToTarget();
                    Log.logd(this, "sendDataInternal, SocketOutputStream is not init.");
                }
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
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
            mSocketOutputStream = new DataOutputStream(mSocket.getOutputStream());
            mSocketInputStream = new DataInputStream(mSocket.getInputStream());
        } catch (IOException ex) {
            ex.printStackTrace();

            closeSocket(CONNECTION_REASON_CODE_IO_EXCEPTION);
        }
    }

    private synchronized void closeSocketOutputStream() {
        if (mSocketOutputStream != null) {
            synchronized (mSocketOutputStream) {
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

    private void notifyDataReceived(byte[] data, int count) {
        for(ConnectionListener listener : mListeners) {
            listener.onDataReceived(data, count);
        }
    }
}
