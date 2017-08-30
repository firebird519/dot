package com.assistant.events;

/**
 * Created by alex on 17-8-21.
 */

public class ClientInfo extends Event {
    public String name;
    public String uId;

    public ClientInfo(String clientName, String unId) {
        super(0, 0L);
        name = clientName;
        uId = unId;
    }

    @Override
    public int getEventType() {
        return Event.EVENT_TYPE_CLIENTNAME;
    }
}
