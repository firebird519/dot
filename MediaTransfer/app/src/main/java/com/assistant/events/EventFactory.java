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

    public static FileEvent generateFileEvent(int connId, String strFilePathName) {
        File file = new File(strFilePathName);

        FileEvent event = null;
        if (file.exists() && file.isFile()) {
            event = new FileEvent(connId, strFilePathName, System.currentTimeMillis());

            event.fileName = file.getName();
            event.fileLastWriteTime = file.lastModified();
            event.fileSize = file.length();
        }

        return event;
    }
}
