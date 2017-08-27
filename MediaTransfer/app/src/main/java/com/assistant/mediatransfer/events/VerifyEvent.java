package com.assistant.mediatransfer.events;

/**
 * Created by liyong on 17-8-25.
 */

public class VerifyEvent extends Event {
    public  String eventName;
    public  long eventUnId; // use SystemClock.elapsedRealtime();

    public VerifyEvent(String name, long eventId) {
        eventName = name;
        eventUnId = eventId;
    }

    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_VERIFY;
    }
}
