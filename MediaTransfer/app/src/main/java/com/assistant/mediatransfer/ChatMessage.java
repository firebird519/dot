package com.assistant.mediatransfer;

import java.util.Date;

/**
 * Created by liyong on 17-8-25.
 */

public class ChatMessage {
    public int id; // chatMessageId
    public String message;
    public Date date; // android.os.SystemClock.uptimeMillis();
    public String ip;
    public String userId;// observed
    public String name;
    public boolean isSent; // if it's true, means it send myself.
}
