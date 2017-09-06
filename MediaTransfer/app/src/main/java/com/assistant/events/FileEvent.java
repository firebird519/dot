package com.assistant.events;

public class FileEvent extends Event {
    public String filePathName;

    @Override
    public int getEventType() {
        return EVENT_TYPE_FILE;
    }

    public FileEvent(int connectionId, long curTime) {
        super(connectionId, curTime);
    }
}
