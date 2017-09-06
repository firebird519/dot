package com.assistant.events;

public class ChatMessageEvent extends Event {
    public int id; // chatMessageId
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
        return "ChatMessageEvent, connId:" + connId + ", message:" + message;
    }
}
