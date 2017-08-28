package com.assistant.mediatransfer.events;

import java.util.Date;

/**
 * Created by liyong on 17-8-25.
 */

public class ChatMessageEvent extends Event {
    public int id; // chatMessageId
    public String message;
    public long date;
    public String connUniqueId;//

    public boolean isReceived; // if it's true, means it send myself.

    public ChatMessageEvent(String msg, long date, int connectionId, String userId, boolean received) {
        message = msg;
        connId = connectionId;
        connUniqueId = userId;
        isReceived = received;
    }

    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_CHAT;
    }
}
