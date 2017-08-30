package com.assistant.events;

/**
 * Created by alex on 17-8-21.
 */

public class EventHead extends Event{
    public int eventType;
    public String jsonData;
    public long unId;

    public EventHead(int type, String data) {
        super(0, 0L);
        eventType = type;
        jsonData = data;
        unId = System.currentTimeMillis();
    }

    public void setEventType(int type) {
        eventType = type;
        unId = System.currentTimeMillis();
    }

    public void setData(String data) {
        jsonData = data;
    }

    public byte[] toBytes() {
        return sGson.toJson(this, EventHead.class).getBytes();
    }

    @Override
    public int getEventType() {
        return EVENT_TYPE_EVENT_HEAD;
    }
}
