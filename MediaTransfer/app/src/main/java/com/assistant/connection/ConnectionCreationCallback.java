package com.assistant.connection;

/**
 * Created by liyong on 17-8-31.
 */

public abstract class ConnectionCreationCallback {
    public String ip;

    public abstract void onResult(boolean ret);
}
