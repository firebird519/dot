package com.assistant.connection;

import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by alex on 17-8-5.
 */

public class HostConnection {
    public static final int HOST_CONNECTION_ERROR_CODE_CLOSED_MANUAL = 0;
    public static final int HOST_CONNECTION_ERROR_CODE_CLOSED_IOERROR = 1;

    public interface HostConnectionListener {
        void onConnectionConnected(HostConnection host, Connection socket);
        void onHostClosed(HostConnection host, int errorCode);
    }

    private int mPort;

    private Set<HostConnectionListener> mListeners =
            new CopyOnWriteArraySet<HostConnectionListener>();

    private ServerSocket mServerSocket;

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

    public int listen(int port, HostConnectionListener listener) {
        if (port <= 0 || listener == null) {
            return -1;
        }

        if (mServerSocket != null) {
            return -1;
        }

        mPort = port;

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
                Socket socket;

                while (true) {
                    if (mServerSocket == null && mServerSocket.isClosed()) {
                        break;
                    }

                    try {
                        socket = mServerSocket.accept();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();

                        if (mServerSocket != null && !mServerSocket.isClosed()) {
                            mHandler.obtainMessage(
                                    EVENT_HOSTSOCKET_CLOSED, HOST_CONNECTION_ERROR_CODE_CLOSED_IOERROR, 0).
                                    sendToTarget();
                        }

                        break;
                    }

                    if (socket != null) {
                        mHandler.obtainMessage(EVENT_SOCKET_CONNECTED, new Connection(socket)).sendToTarget();
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
