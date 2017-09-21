package com.assistant.connection;

public abstract class ConnectionCreationResponse {
    public String ip;

    public abstract void onResponse(boolean success, Connection connection,
                                    ConnectionCreationRequest request, int reason);
}
