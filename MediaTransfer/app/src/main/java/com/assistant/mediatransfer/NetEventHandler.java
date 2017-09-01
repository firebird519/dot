package com.assistant.mediatransfer;

import android.os.Message;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.events.EventHead;
import com.assistant.events.VerifyEvent;
import com.assistant.utils.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Handler all net event receive and send.
 *
 */

public class NetEventHandler extends NetEventHandlerBase {


    // TODO: key string use unique id of one connection.
    private Map<Integer, Object> mMsgCollections =
            Collections.synchronizedMap(new HashMap<Integer, Object>(10));

    NetEventHandler(MediaTransferManager mediaTransferManager,
                    ConnectionManager connectionManager ) {
        super(mediaTransferManager, connectionManager);
    }

    public List<Event> getMessageList(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        if (connection != null) {
            ClientInfo clientInfo = (ClientInfo) connection.getConnData();

            Log.d(this, "getMessageList, get msg list for connId:" + connId);
            if (clientInfo != null) {
                return (List<Event>)mMsgCollections.get(connId);
            } else {
                Log.d(this, "getMessageList, clientInfo not received for id:" + connId);
            }
        } else {
            Log.d(this, "getMessageList, connection not avaible for id:" + connId);
        }

        return null;
    }

    @Override
    void handleConnectionAdded(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        Log.d(this, "handleConnectionAdded, connId:" + connId);
        if (connection != null && !connection.isHost()) {
            sendClientInfoEvent(connId);
        }
    }

    @Override
    void handleConnectionRemoved(int connId) {

    }

    @Override
    boolean handleThreadHandlerMessage(Message msg) {
        return false;
    }

    @Override
    void handleNetEvent(int connId, String data) {
        EventHead eventHead = Event.toEvent(data, EventHead.class);

        if (eventHead == null) {
            Log.d(this, "handleNetEvent, EventHead parse error:" + data);
            return;
        }

        Log.d(this, "handleNetEvent, connId:" + connId + ", netEvent:" + eventHead.jsonData);

        if (eventHead.eventType != Event.EVENT_TYPE_VERIFY) {
            verifyEvent(connId, eventHead.eventType, eventHead.unId);
        }

        switch (eventHead.eventType) {
            case Event.EVENT_TYPE_VERIFY:
                handleEventVerify(Event.toEvent(eventHead.jsonData, VerifyEvent.class));
                break;
            case Event.EVENT_TYPE_CLIENTNAME:
                ClientInfo clientInfo = Event.toEvent(eventHead.jsonData, ClientInfo.class);

                Connection connection = mConnectionManager.getConnection(connId);
                if (connection != null) {

                    Log.d(this, "handleNetEvent, received ClientInfoEvent, connId:" + connection.getId());
                    connection.setConnData(clientInfo);
                    List<Event> mMsgArray =
                            Collections.synchronizedList(new ArrayList<Event>());

                    Log.d(this, "handleNetEvent, create msg list for uid:" + clientInfo.uId);
                    mMsgCollections.put(connId, mMsgArray);

                    notifyClientAvailable(connId, clientInfo);

                    // when connection connected, client send info to host side first.
                    // host side will send info back after received client info.
                    if (connection.isHost()) {
                        Log.d(this, "handleNetEvent, response ClientInfoEvent");
                        sendClientInfoEvent(connId);
                    }
                }
                break;
            case Event.EVENT_TYPE_CHAT:
                ChatMessageEvent event = Event.toEvent(eventHead.jsonData, ChatMessageEvent.class);

                if (event != null) {
                    event.setConnId(connId);
                    event.setIsReceived(true);
                    event.setEventCreateTime(System.currentTimeMillis());

                    recordEvent(connId, event);

                    // notify to UI.
                    notifyEventReceived(connId, event);
                } else {
                    Log.d(this, "Event parser error:" + eventHead.jsonData);
                }
                break;
            case Event.EVENT_TYPE_FILE:
                break;
        }
    }

    private void sendClientInfoEvent(int connId) {
        sendEvent(connId, mMediaTransferManager.getClientInfo());
    }

    void recordEvent(int connId, Event event){
        Log.d(this, "recordEvent, record event for connId:" + connId);
        List<Event> msgList = getMessageList(connId);

        if (msgList != null) {
            msgList.add(event);
        } else {
            Log.d(this, "recordEvent, ChatMessageEvent message list not found!");
        }
    }
}
