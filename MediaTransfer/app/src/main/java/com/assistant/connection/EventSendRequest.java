package com.assistant.connection;

import com.assistant.events.Event;

public class EventSendRequest {
    public static final int STATE_WAITING = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_SENT = 2;
    public static final int STATE_FAILED = 3;

    public Event event;

    public EventSendResponse response;
    public int retryCount;
    public long lastSendTime;
    public int mState;

    public EventSendRequest(Event e, EventSendResponse r) {
        event = e;
        response = r;
        retryCount = 0;
        lastSendTime = 0L;
    }

    public void setState(int state) {
        mState = state;
    }

    public int getState() {
        return mState;
    }

    public void responseProgress(int percent) {
        if (response != null) {
            response.onSendProgress(event.uniqueId, percent);
        }
    }

    public void responseResult(boolean success, int reason, int sentBytesCount) {
        if (response != null) {
            if (success) {
                response.onResult(event.connId,
                        event.uniqueId,
                        EventSendResponse.RESULT_SUCESS,
                        sentBytesCount);
            } else {
                response.onResult(event.connId,
                        event.uniqueId,
                        EventSendResponse.RESULT_FAILED,
                        reason);
            }
        }
    }

    public String toString() {
        return "EventSendRequest, Event:" + event.toString()
                + ", retryCount:" + retryCount
                + ", response:" + response
                + ", lastSendTime:" + lastSendTime;
    }
}
