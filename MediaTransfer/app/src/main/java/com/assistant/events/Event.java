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

    @Expose(serialize = false)
    public int mState;

    public int connId; // necessary?
    public long uniqueId = SystemClock.elapsedRealtime();
    public long time;

    public Event(int connectionId, long lTime) {
        time = lTime;
        connId = connectionId;
    }

    public void setEventCreateTime(long curTime) {
        time = curTime;
    }

    public String toJsonString() {
        return sGson.toJson(this, this.getClass());
    }

    public static <T> T toEvent(String jsonString,  Class<T> cls) {
        return sGson.fromJson(jsonString, cls);
    }

    public void setState(int state) {
        mState = state;
    }

    public abstract String getEventTypeName();
}
