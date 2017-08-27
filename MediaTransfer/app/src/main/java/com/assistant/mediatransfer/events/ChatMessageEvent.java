package com.assistant.mediatransfer.events;

import java.util.Date;

/**
 * Created by liyong on 17-8-25.
 */

public class ChatMessageEvent extends Event {
    public int id; // chatMessageId
    public String message;
    public Date date; // android.os.SystemClock.uptimeMillis();
    public String userId;// client unique id
    public int connId; // necessary?
    public boolean isSent; // if it's true, means it send myself.

    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_CHAT;
    }
}
