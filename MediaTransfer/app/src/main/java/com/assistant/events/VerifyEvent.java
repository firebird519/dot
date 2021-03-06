package com.assistant.events;

import com.assistant.utils.Utils;

/*
 * response event for Event. To indicate that client has been received corresponding Event
 */
public class VerifyEvent extends Event {
    public  int eventType;
    public  long eventUnId; // use SystemClock.elapsedRealtime();

    public VerifyEvent(int connId, int type, long eventId) {
        super(connId, 0L);
        eventType = type;
        eventUnId = eventId;
    }

    @Override
    public int getEventType() {
        return Event.EVENT_TYPE_VERIFY;
    }

    public String toString() {
        String log;
        if (Utils.DEBUG) {
            log = "VerifyEvent, eventType:" + eventType + ", eventUnId:" + eventUnId;
        } else {
            log = "VerifyEvent";
        }

        return log;
    }
}
