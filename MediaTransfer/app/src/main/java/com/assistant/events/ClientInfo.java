package com.assistant.events;

public class ClientInfo extends Event implements Cloneable {
    public String name;
    public String clientUniqueId;

    public ClientInfo(String clientName, String unId) {
        super(0, 0L);
        name = clientName;
        clientUniqueId = unId;
    }

    @Override
    public int getEventType() {
        return Event.EVENT_TYPE_CLIENTNAME;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        ClientInfo info = (ClientInfo) super.clone();

        info.name = name;
        info.clientUniqueId = clientUniqueId;
        return info;
    }

    @Override
    public String toString() {
        return "ClientInfo, name:" + name + ", clientUniqueId:" + clientUniqueId;
    }
}
