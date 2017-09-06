package com.assistant.connection;

import com.assistant.events.Event;

public class EventSendRequest {
    public Event event;

    public EventSendResponse response;

    public EventSendRequest(Event e, EventSendResponse r) {
        event = e;
        response = r;
    }
}
