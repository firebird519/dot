package com.assistant.connection;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.assistant.utils.Log;
import com.assistant.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConnectionCreationHandler extends Handler {
    private static final String TAG = "ConnectionCreationHandler";

    private static final int EVENT_CONNECT_REQUEST = 0;
    private static final int EVENT_CONNECT_REQUEST_TIMEOUT = 1;

    private static final int RECONNECT_DELAY_TIMESTAMP = 2*1000; //2s
    private static final int CONNECTION_CREATION_TIMEOUT_TIMESTAMP = 20*1000; //10s

    private ConnectionManager mConnectionManager;

    private boolean mStopped = false;

    private List<ConnectionCreationRequest> mRequestList =
            Collections.synchronizedList(new ArrayList<ConnectionCreationRequest>());

    public ConnectionCreationHandler(Looper looper, ConnectionManager connectionManager) {
        super(looper);

        mConnectionManager = connectionManager;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_CONNECT_REQUEST:
                handleRequest((ConnectionCreationRequest) msg.obj);
                break;
            case EVENT_CONNECT_REQUEST_TIMEOUT:
                handleRequestTimeoutEvent((ConnectionCreationRequest) msg.obj);
                break;
            default:
                break;
        }
        super.handleMessage(msg);
    }

    public void processConnectCreationRequest(ConnectionCreationRequest request) {
        processConnectCreationRequest(request, 0L);
    }

    public void processConnectCreationRequest(ConnectionCreationRequest request, long delayed) {
        Log.d(TAG, "processConnectCreationRequest:" + request + ", delay:" + delayed);

        // cancel stop flag when one new request coming
        mStopped = false;

        if (request == null) {
            Log.d(TAG, "request is null or request already existed");
            return;
        }

        synchronized (mRequestList) {
            if (request != null && !mRequestList.contains(request)) {
                mRequestList.add(request);
            }
        }

        // Add retry delay to avoid connect at same time in both host and client side.
        Message msg = obtainMessage(EVENT_CONNECT_REQUEST, request);
        sendMessageDelayed(msg, request.retryDelay + delayed);

        Message timeoutMsg = obtainMessage(EVENT_CONNECT_REQUEST_TIMEOUT, request);
        sendMessageDelayed(timeoutMsg,
                CONNECTION_CREATION_TIMEOUT_TIMESTAMP + delayed + request.retryDelay);
    }

    public void cancelAllRequests() {
        Log.d(TAG, "cancelAllRequests");
        mStopped = true;

        removeMessages(EVENT_CONNECT_REQUEST);
        removeMessages(EVENT_CONNECT_REQUEST_TIMEOUT);
        synchronized (mRequestList) {
            for(ConnectionCreationRequest request : mRequestList) {
                onResponse(request, null,
                        Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_CANCELED);
            }
        }
    }


    public ConnectionCreationRequest getPendingConnectRequest(String ipAddress) {
        Log.d(TAG, "getPendingConnectRequest, ipAddress:" + ipAddress);
        synchronized (mRequestList) {
            if (mRequestList.size() > 0) {
                logConnectRequestList();

                for (ConnectionCreationRequest request : mRequestList) {
                    Log.d(TAG, "getPendingConnectRequest, request ip:" + request.ipAddress);
                    if (TextUtils.equals(ipAddress, request.ipAddress)) {
                        Log.d(TAG, "getPendingConnectRequest, true for ip:" + ipAddress);
                        return request;
                    }
                }
            }
        }

        Log.d(TAG, "hasPendingConnectRequest, no request for:" + ipAddress);

        return null;
    }

    private void removeConnectRequest(ConnectionCreationRequest request) {
        logConnectRequestList();

        Log.d(TAG, "removeConnectRequest, request:" + request);

        removeMessages(EVENT_CONNECT_REQUEST, request);
        removeMessages(EVENT_CONNECT_REQUEST_TIMEOUT, request);
        if (request != null) {
            synchronized (mRequestList) {
                mRequestList.remove(request);
            }
        }
    }

    private void handleRequest(ConnectionCreationRequest request) {
        Log.d(TAG, "handleRequest, request:" + request);

        if (request == null) {
            Log.d(TAG, "request is null, handle next one.");
            return;
        }

        Connection connection = mConnectionManager.getConnection(request.ipAddress);

        if (connection != null
                && !(Utils.DEBUG_CONNECTION && mConnectionManager.getConnectionsSize() < 2)) {
            onResponse(request, connection, 0);
            return;
        }

        new Thread(new ConnectionCreationRunnable(request)).start();
    }

    private void handleRequestTimeoutEvent(ConnectionCreationRequest request) {
        Log.d(TAG, "handleRequestTimeoutEvent, request:" + request);
        onResponse(request, null, Connection.CONNECTION_REASON_CODE_CONNECT_TIMEOUT);
    }

    private boolean retryRequest(ConnectionCreationRequest request) {
        Log.d(TAG, "retryRequest, request:" + request);
        if (request != null) {
            request.retryTimes--;

            if (request.retryTimes > 0
                    && mConnectionManager.isReconnectAllowed()
                    && null == mConnectionManager.getConnection(request.ipAddress)) {
                processConnectCreationRequest(request,
                        RECONNECT_DELAY_TIMESTAMP);
                return true;
            }
        }

        return false;
    }

    private void onResponse(ConnectionCreationRequest request, Connection connection, int reason) {
        Log.d(TAG, "onResponse, request:" + request
                + ", connection:" + connection
                + ", reason:" + reason);

        removeConnectRequest(request);

        if (request != null && !request.isDropped) {
            boolean success = (connection != null
                    && connection.getState() == Connection.CONNECTION_STATE_CONNECTED);

            boolean response = true;
            if (success) {
                mConnectionManager.addConnection(connection);
            } else {
                response = !retryRequest(request);
            }

            if (response) {
                // transfer previous disconnect reason to notify to UI for reconnect type
                if (!success && request.type == ConnectionCreationRequest.CONNECTION_TYPE_RECONNECT) {
                    reason = request.disconnectReason;
                }

                request.notifyResult(success,
                        connection,
                        request,
                        reason);
            }
        } else {
            // close connection even it's connected success. Request is already canceled.
            if (connection != null && !connection.isClosed()) {
                connection.close(Connection.CONNECTION_REASON_CODE_CONNECT_REQUEST_CANCELED);
            }
        }
    }

    private void logConnectRequestList() {
        Log.d(TAG, "logConnectRequestList, size:"  + mRequestList.size());
        synchronized (mRequestList) {
            if (mRequestList.size() > 0) {
                for (ConnectionCreationRequest request : mRequestList) {
                    Log.d(TAG, "    "  + request.toString());
                }
            }
        }
    }

    private class ConnectionListener implements Connection.ConnectionListener {
        private ConnectionCreationRequest mRequest;

        public ConnectionListener(ConnectionCreationRequest request) {
            mRequest =request;
        }

        @Override
        public void onConnected(Connection conn) {
            Log.d(TAG, "ConnectionListener, onConnected for " + conn.getIp()
                    + ", ipAddress:" + conn.getIp()
                    + ", mStopped:" + mStopped);
            onResponse(mRequest, conn, 0);
            conn.removeListener(this);
        }

        @Override
        public void onConnectFailed(Connection conn, int reasonCode) {
            Log.d(TAG, "ConnectionListener, onConnectFailed for " + conn.getIp()
                    + ", ipAddress:" + conn.getIp());
            onResponse(mRequest, conn, reasonCode);
            conn.removeListener(this);
        }

        @Override
        public void onClosed(Connection conn, int reasonCode) {
            Log.d(TAG, "ConnectionListener, onClosed for " + conn.getIp()
                    + ", ipAddress:" + conn.getIp());
            onResponse(mRequest, conn, reasonCode);
            conn.removeListener(this);
        }
    }

    private class ConnectionCreationRunnable implements Runnable {
        private ConnectionCreationRequest mRequest;

        public ConnectionCreationRunnable(ConnectionCreationRequest request) {
            mRequest = request;
        }

        @Override
        public void run() {
            try {
                Connection connection =
                        ConnectionFactory.createConnection(mRequest.ipAddress,
                                mRequest.port,
                                mRequest.connId,
                                new ConnectionListener(mRequest));

                if (connection == null) {
                    Log.e(TAG, "new connection failed... possible?");
                    onResponse(mRequest, null, Connection.CONNECTION_REASON_CODE_UNKNOWN);
                }
            } catch (Exception e) {
                e.printStackTrace();

                onResponse(mRequest, null, Connection.CONNECTION_REASON_CODE_UNKNOWN);
            }
        }
    }
}
