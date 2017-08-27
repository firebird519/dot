package com.assistant.mediatransfer.events;

/**
 * Created by alex on 17-8-21.
 */

public class ClientInfo extends Event {
    public String name;
    public String uniqueId;

    public ClientInfo(String clientName, String unId) {
        name = clientName;
        uniqueId = unId;
    }

    @Override
    public String getEventTypeName() {
        return NetEvent.EVENT_CLIENT_INFO;
    }
}
