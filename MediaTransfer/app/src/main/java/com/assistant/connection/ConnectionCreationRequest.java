package com.assistant.connection;

import com.assistant.utils.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConnectionCreationRequest {
    private static final String TAG = "ConnectionCreationRequest";

    public static final int CONNECT_TYPE_NEW_CREATION = 0;
    public static final int CONNECTION_TYPE_RECONNECT = 1;

    String ipAddress; // key of this request.
    int type = CONNECT_TYPE_NEW_CREATION;
    int port;
    int connId;
    int retryTimes;
    int disconnectReason;
    long retryDelay = 0L;

    boolean isDropped = false;

    List<ConnectionCreationResponse> responseList
            = Collections.synchronizedList(new ArrayList<ConnectionCreationResponse>());

    ConnectionCreationRequest(Connection connection, int port, int reason, int retryTimes,
                              long delay, ConnectionCreationResponse response) {
        this(connection.getIp(), port, retryTimes, response);
        connId = connection.getId();
        disconnectReason = reason;
        retryDelay = delay;
        type = CONNECTION_TYPE_RECONNECT;
    }

    ConnectionCreationRequest(String ip, int connPort, int retryTimes,
                              ConnectionCreationResponse response) {
        ipAddress = ip;
        port = connPort;
        this.retryTimes = retryTimes;
        addResponse(response);

        connId = -1;
        disconnectReason = 0;
    }

    public static ConnectionCreationRequest generateConnectRequest(String ip,
                                                                   int connPort, int retryTimes, ConnectionCreationResponse response) {
        return new ConnectionCreationRequest(ip, connPort, retryTimes, response);
    }

    public static ConnectionCreationRequest generateReconnectRequest(Connection connection, int port,
                                                                     int reason, int retryTimes, long delay, ConnectionCreationResponse response) {
        return new ConnectionCreationRequest(connection, port, reason, retryTimes, delay, response);
    }


    public void addResponse(ConnectionCreationResponse response) {
        if (response != null && !isDropped()) {
            responseList.add(response);
        }
    }

    public void notifyResult(boolean success,
                             Connection connection,
                             ConnectionCreationRequest request,
                             int reason) {
        Log.d(TAG, "notifyResult, success:" + success + ", ip:" + request.ipAddress);
        isDropped = true;

        if (responseList.size() > 0) {
            for (ConnectionCreationResponse response : responseList) {
                response.onResponse(success, connection, request, reason);
            }

            responseList.clear();
        }
    }

    public boolean isDropped() {
        return isDropped;
    }

    public String toString() {
        return "[ConnectRequest] - connId:" + connId
                + " type:" + type
                + " ip:" + ipAddress
                + ", port:" + port
                + ", disconnect reason:" + disconnectReason
                + " isDropped:" + isDropped
                + ", retryTimes:" + retryTimes
                + ", responseList size:" + responseList.size();
    }
}
