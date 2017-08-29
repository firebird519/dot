package com.assistant.events;

/**
 * Created by alex on 17-8-21.
 */

public class NetEvent extends Event{
    // with ClientInfo Json
    public static final String EVENT_CLIENT_INFO = "clientInfo";

    // with ChatMessageEvent
    public static final String EVENT_CHAT = "chat";

    // to be defined
    public static final String EVENT_FILE = "file";

    // with VerifyEvent
    public static final String EVENT_VERIFY = "verify";

    public String name;
    public String jsonData;
    public long unId;

    public NetEvent(String eventName, String data) {
        super(0, 0L);
        name = eventName;
        jsonData = data;
        unId = System.currentTimeMillis();
    }

    public void setName(String cmd) {
        name = cmd;
        unId = System.currentTimeMillis();
    }

    public void setData(String data) {
        jsonData = data;
    }

    public byte[] toBytes() {
        return sGson.toJson(this, NetEvent.class).getBytes();
    }

    @Override
    public String getEventTypeName() {
        return "NetEvent";
    }
}
