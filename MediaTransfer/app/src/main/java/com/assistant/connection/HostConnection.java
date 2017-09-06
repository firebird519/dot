package com.assistant.connection;

import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;

import com.assistant.utils.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class HostConnection {
    private static String TAG = "HostConnection";

    public static final int HOST_CONNECTION_ERROR_CODE_CLOSED_MANUAL = 0;
    public static final int HOST_CONNECTION_ERROR_CODE_CLOSED_IOERROR = 1;

    public interface HostConnectionListener {
        void onConnectionConnected(HostConnection host, Connection socket);
        void onHostClosed(HostConnection host, int errorCode);
    }

    private int mPort;

    private Set<HostConnectionListener> mListeners =
            new CopyOnWriteArraySet<>();

    private ServerSocket mServerSocket;

    private PowerManager.WakeLock mWakeLock;

    private static final int EVENT_SOCKET_CONNECTED = 0;
    private static final int EVENT_HOSTSOCKET_CLOSED = 1;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SOCKET_CONNECTED:
                    notifySocketConnected((Connection) msg.obj);
                    break;
                case EVENT_HOSTSOCKET_CLOSED:
                    closeInternal(msg.arg1);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public HostConnection() {
        mPort = 0;
    }

    public int getPort() {
        return mPort;
    }

    public int listen(int port, PowerManager.WakeLock wakeLock, HostConnectionListener listener) {
        if (port <= 0 || listener == null) {
            return -1;
        }

        if (mServerSocket != null) {
            return -1;
        }

        TAG = TAG + ":" + port;

        mPort = port;
        mWakeLock = wakeLock;

        try {
            mServerSocket = new ServerSocket(port);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return -1;
        }

        mListeners.add(listener);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;

                while (true) {
                    if (mServerSocket == null && mServerSocket.isClosed()) {
                        break;
                    }

                    try {
                        socket = mServerSocket.accept();
                        if (!mWakeLock.isHeld()) {
                            mWakeLock.acquire(2000);
                        }

                        socket.setKeepAlive(true);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();

                        if (mServerSocket != null && !mServerSocket.isClosed()) {
                            mHandler.obtainMessage(
                                    EVENT_HOSTSOCKET_CLOSED, HOST_CONNECTION_ERROR_CODE_CLOSED_IOERROR, 0).
                                    sendToTarget();
                        }

                        break;
                    } catch (Exception e) {
                        Log.d(TAG, "listen, exception:" + e.getMessage());
                    }

                    if (socket != null) {
                        mHandler.obtainMessage(EVENT_SOCKET_CONNECTED,
                                        new Connection(socket, true))
                                .sendToTarget();
                    }
                }
            }
        }).start();

        return 0;
    }

    public boolean isClosed() {
        return !(mServerSocket != null && !mServerSocket.isClosed());
    }

    public void close() {
        closeInternal(HOST_CONNECTION_ERROR_CODE_CLOSED_MANUAL);
    }

    private void closeInternal(int errorCode) {
        if (mServerSocket != null && !mServerSocket.isClosed()) {
            try {
                mServerSocket.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            notifySocketClosed(errorCode);
        }
    }

    private void notifySocketConnected(Connection connection) {
        for(HostConnectionListener listener : mListeners) {
            listener.onConnectionConnected(this, connection);
        }
    }

    private void notifySocketClosed(int errorCode) {
        for(HostConnectionListener listener : mListeners) {
            listener.onHostClosed(this, errorCode);
        }
    }
}
