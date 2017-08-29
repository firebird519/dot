package com.assistant.events;

/**
 * Created by alex on 17-8-26.
 */

public class FileEvent extends Event {
    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_FILE;
    }

    public FileEvent(int connectionId, long curTime) {
        super(connectionId, curTime);
    }
}
