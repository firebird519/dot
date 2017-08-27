package com.assistant.mediatransfer.events;

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

    public long uniqueId = SystemClock.elapsedRealtime();

    public String toJsonString() {
        return sGson.toJson(this, this.getClass());
    }

    public static <T> T toEvent(String jsonString,  Class<T> cls) {
        return sGson.fromJson(jsonString, cls);
    }

    public abstract String getEventTypeName();
}
