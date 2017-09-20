package com.assistant.events;

import com.assistant.utils.Utils;

public class ChatMessageEvent extends Event {
    public String message;

    public String connUniqueId;//

    public ChatMessageEvent(String msg, long curTime, int connectionId, String userId, boolean received) {
        super(connectionId, curTime, received);
        message = msg;
        connUniqueId = userId;
    }

    @Override
    public int getEventType() {
        return EVENT_TYPE_CHAT;
    }

    @Override
    public String toString() {
        String log;

        if (Utils.DEBUG) {
            log = "ChatMessageEvent - " + super.toString()
                    + ", message:" + message;
        } else {
            log = "ChatMessageEvent";
        }

        return log;
    }
}
