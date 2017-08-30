package com.assistant.events;

/**
 * Created by liyong on 17-8-25.
 */

public class VerifyEvent extends Event {
    public  int eventType;
    public  long eventUnId; // use SystemClock.elapsedRealtime();

    public VerifyEvent(int type, long eventId) {
        super(0, 0L);
        eventType = type;
        eventUnId = eventId;
    }

    @Override
    public int getEventType() {
        return EVENT_TYPE_VERIFY;
    }
}
