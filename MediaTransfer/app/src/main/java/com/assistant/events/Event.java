package com.assistant.events;

import android.os.SystemClock;

public abstract class Event extends EventBase{
    public static final int STATE_SENDING = 0;
    public static final int STATE_SENT = 1;
    public static final int STATE_VERIFIED = 2;
    public static final int STATE_TIMEOUT = 3;
    public static final int STATE_FAILED = 4;

    public static final int EVENT_TYPE_VERIFY = 1;
    public static final int EVENT_TYPE_CLIENTNAME = 2;
    public static final int EVENT_TYPE_CHAT = 3;
    public static final int EVENT_TYPE_FILE = 4;

    public long uniqueId = SystemClock.elapsedRealtime();

    // use System.currentTimeMillis()
    public transient long createTime;

    public transient int mState;

    // indicate event shown to user or not.
    public transient boolean isShown = false;

    public transient int connId; // necessary?

    public transient boolean isReceived; // if it's true, means it send myself.

    public Event(int connectionId, long lTime) {
        this(connectionId, lTime, false);
    }

    public Event(int connectionId, long lTime, boolean received) {
        createTime = lTime;
        connId = connectionId;
        isReceived = received;
    }

    public void setEventCreateTime(long curTime) {
        createTime = curTime;
    }

    public long getEventCreatedElapsedTime() {
        return uniqueId;
    }

    public void setIsReceived(boolean received) {
        isReceived = received;
    }

    public void setConnId(int id) {
        connId = id;
    }

    public void setState(int state) {
        mState = state;
    }

    public abstract int getEventType();

    public String getEventClassName() {
        return getClass().toString();
    }
}
