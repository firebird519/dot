package com.assistant.events;

import com.assistant.utils.Utils;

public class FileEvent extends Event {
    // not serialized by gson.
    public transient String tempFilePathName;

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

        tempFilePathName = pathName;
    }

    @Override
    public String toString() {
        String log;
        if (Utils.DEBUG) {
            log = "FileEvent - " + super.toString()
                    + ", fileName:" + fileName
                    + ", temp file:" + tempFilePathName;
        } else {
            log = "FileEvent";
        }

        return log;
    }
}
