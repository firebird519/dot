package com.assistant.events;

import com.google.gson.Gson;

public abstract class EventBase {
    // not serialize this variable.
    public static transient Gson sGson = new Gson();

    public String toJsonString() {
        return sGson.toJson(this, this.getClass());
    }

    public static <T> T toEvent(String jsonString,  Class<T> cls) {
        return sGson.fromJson(jsonString, cls);
    }
}
