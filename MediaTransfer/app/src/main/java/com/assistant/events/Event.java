package com.assistant.events;

import android.os.SystemClock;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

/**
 * Created by alex on 17-8-26.
 */

public abstract class Event {
    // not serialize this variable.
    @Expose(serialize = false)
    static Gson sGson = new Gson();

    public static final int STATE_SENDING = 0;
    public static final int STATE_SENT = 1;
    public static final int STATE_VERIFIED = 2;
    public static final int STATE_TIMEOUT = 3;
    public static final int STATE_FAILED = 4;

    public static final int EVENT_TYPE_EVENT_HEAD = 0;

    public static final int EVENT_TYPE_VERIFY = 1;
    public static final int EVENT_TYPE_CLIENTNAME = 2;
    public static final int EVENT_TYPE_CHAT = 3;
    public static final int EVENT_TYPE_FILE = 4;

    @Expose(serialize = false)
    public int mState;

    // indicate event shown to user or not.
    @Expose(serialize = false)
    public boolean isShown = false;

    @Expose(serialize = false)
    public int connId; // necessary?

    public long uniqueId = SystemClock.elapsedRealtime();
    public long time;

    @Expose(serialize = false)
    public boolean isReceived; // if it's true, means it send myself.

    public Event(int connectionId, long lTime) {
        this(connectionId, lTime, false);
    }

    public Event(int connectionId, long lTime, boolean received) {
        time = lTime;
        connId = connectionId;
        isReceived = received;
    }

    public void setEventCreateTime(long curTime) {
        time = curTime;
    }

    public void setIsReceived(boolean received) {
        isReceived = received;
    }

    public String toJsonString() {
        return sGson.toJson(this, this.getClass());
    }

    public static <T> T toEvent(String jsonString,  Class<T> cls) {
        return sGson.fromJson(jsonString, cls);
    }

    public void setConnId(int id) {
        connId = id;
    }

    public void setState(int state) {
        mState = state;
    }

    public abstract int getEventType();
}
