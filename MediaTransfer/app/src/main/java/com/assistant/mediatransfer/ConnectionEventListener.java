package com.assistant.mediatransfer;

import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.events.Event;

/**
 * Created by alex on 17-8-28.
 */

public interface ConnectionEventListener {
    void onClientAvailable(int connId, ClientInfo info);
    void onEventReceived(int connId, Event event);
    void onEventStateUpdated(int connId, Event event);
}
