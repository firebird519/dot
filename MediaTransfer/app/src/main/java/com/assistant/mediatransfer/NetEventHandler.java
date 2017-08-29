package com.assistant.mediatransfer;

import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.events.NetEvent;
import com.assistant.events.VerifyEvent;
import com.assistant.utils.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by liyong on 17-8-25.
 *
 * Handler all net name receive and send.
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

    public List<ChatMessageEvent> getMessageList(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        if (connection != null) {
            ClientInfo clientInfo = (ClientInfo) connection.getConnData();

            Log.d(this, "getMessageList, get msg list for connId:" + connId);
            if (clientInfo != null) {
                return (List<ChatMessageEvent>)mMsgCollections.get(connId);
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
        NetEvent netEvent = Event.toEvent(data, NetEvent.class);

        if (netEvent == null) {
            Log.d(this, "handleNetEvent, NetEvent parse error:" + data);
            return;
        }

        Log.d(this, "handleNetEvent, connId:" + connId + ", netEvent:" + netEvent.jsonData);

        if (!NetEvent.EVENT_VERIFY.equals(netEvent.name)) {
            verifyEvent(connId, netEvent.name, netEvent.unId);
        }

        switch (netEvent.name) {
            case NetEvent.EVENT_VERIFY:
                handleEventVerify(Event.toEvent(netEvent.jsonData, VerifyEvent.class));
                break;
            case NetEvent.EVENT_CLIENT_INFO:
                ClientInfo clientInfo = Event.toEvent(netEvent.jsonData, ClientInfo.class);

                Connection connection = mConnectionManager.getConnection(connId);
                if (connection != null) {

                    Log.d(this, "handleNetEvent, received ClientInfoEvent, connId:" + connection.getId());
                    connection.setConnData(clientInfo);
                    List<ChatMessageEvent> mMsgArray =
                            Collections.synchronizedList(new ArrayList<ChatMessageEvent>());

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
            case NetEvent.EVENT_CHAT:
                ChatMessageEvent event = Event.toEvent(netEvent.jsonData, ChatMessageEvent.class);

                if (event != null) {
                    event.setIsReceived(true);
                    event.setEventCreateTime(System.currentTimeMillis());

                    recordChatEvent(connId, event);

                    // notify to UI.
                    notifyEventReceived(connId, event);
                } else {
                    Log.d(this, "Event parser error:" + netEvent.jsonData);
                }
                break;
            case NetEvent.EVENT_FILE:
                break;
        }
    }

    private void sendClientInfoEvent(int connId) {
        sendEvent(connId, mMediaTransferManager.getClientInfo());
    }

    void recordChatEvent(int connId, ChatMessageEvent event){
        Log.d(this, "recordChatEvent, record event for connId:" + connId);
        List<ChatMessageEvent> msgList = getMessageList(connId);

        if (msgList != null) {
            msgList.add(event);
        } else {
            Log.d(this, "recordChatEvent, ChatMessageEvent message list not found!");
        }
    }
}
