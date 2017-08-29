package com.assistant.events;

/**
 * Created by liyong on 17-8-25.
 */

public class VerifyEvent extends Event {
    public  String eventName;
    public  long eventUnId; // use SystemClock.elapsedRealtime();

    public VerifyEvent(String name, long eventId) {
        super(0, 0L);
        eventName = name;
        eventUnId = eventId;
    }

    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_VERIFY;
    }
}
