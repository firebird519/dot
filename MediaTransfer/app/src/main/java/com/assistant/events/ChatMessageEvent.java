package com.assistant.events;

/**
 * Created by liyong on 17-8-25.
 */

public class ChatMessageEvent extends Event {
    public int id; // chatMessageId
    public String message;

    public String connUniqueId;//

    public boolean isReceived; // if it's true, means it send myself.

    public ChatMessageEvent(String msg, long curTime, int connectionId, String userId, boolean received) {
        super(connectionId, curTime);
        message = msg;
        connUniqueId = userId;
        isReceived = received;
    }

    public void setIsReceived(boolean received) {
        isReceived = received;
    }


    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_CHAT;
    }
}
