package com.assistant.events;

import java.io.File;

public class EventFactory {
    public static Event toEvent(int connId, String json, int type, boolean isReceived) {
        Event event = null;
        switch (type) {
            case Event.EVENT_TYPE_CHAT:
                event = Event.toEvent(json, ChatMessageEvent.class);
                break;
            case Event.EVENT_TYPE_CLIENTNAME:
                event = Event.toEvent(json, ClientInfo.class);
                break;
            case Event.EVENT_TYPE_FILE:
                event = Event.toEvent(json, FileEvent.class);
                break;
            case Event.EVENT_TYPE_VERIFY:
                event = Event.toEvent(json, VerifyEvent.class);
                break;
            default:
                break;
        }

        if (event != null) {
            event.setConnId(connId);
            event.setIsReceived(isReceived);
            event.setEventCreateTime(System.currentTimeMillis());
        }

        return event;
    }

    public static FileEvent generateFileEvent(String strFilePathName) {
        File file = new File(strFilePathName);

        if (file.exists()) {
            file.lastModified();
            
        }

        return null;
    }
}
