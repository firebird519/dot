package com.assistant.connection;

import com.assistant.events.Event;

public class EventSendRequest {
    public Event event;

    public EventSendResponse response;
    public int retryCount;
    public long lastSendTime;

    public EventSendRequest(Event e, EventSendResponse r) {
        event = e;
        response = r;
        retryCount = 0;
        lastSendTime = 0L;
    }

    public String toString() {
        return "EventSendRequest, Event:" + event.toString()
                + ", retryCount:" + retryCount
                + ", response:" + response
                + ", lastSendTime:" + lastSendTime;
    }
}
