package com.assistant.events;

public class FileEvent extends Event {
    // not serialized by gson.
    public transient String filePathName;

    public String fileName;
    public long fileLastWriteTime;
    public long fileSize;

    public int fileAttributes;
    public long fileCreateTime;
    public long fileLastAccessTime;
    public int fileReserved1; // 0

    @Override
    public int getEventType() {
        return EVENT_TYPE_FILE;
    }

    public FileEvent(int connectionId, String pathName, long curTime) {
        super(connectionId, curTime);

        filePathName = pathName;
    }

    @Override
    public String toString() {
        return "FileEvent - " + super.toString()
                + ", fileName:" + fileName
                + ", temp file:" + filePathName;
    }
}
