package com.assistant.events;

/**
 * inherrit from Event to reuse gson object.
 */
public class NetEvent extends EventBase {
    public int eventType;
    public String jsonData;
    public long eventId;
    public long netEventId;

    public NetEvent(int type, long evId, String data) {
        eventType = type;
        jsonData = data;
        eventId = evId;
        netEventId = System.currentTimeMillis();
    }

    public void setEventType(int type) {
        eventType = type;
        netEventId = System.currentTimeMillis();
    }

    public void setData(String data) {
        jsonData = data;
    }

    public byte[] toBytes() {
        return sGson.toJson(this, NetEvent.class).getBytes();
    }
}
