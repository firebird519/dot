package com.assistant.connection;

public abstract class ConnectionCreationCallback {
    public String ip;

    public abstract void onResult(boolean ret);
}
