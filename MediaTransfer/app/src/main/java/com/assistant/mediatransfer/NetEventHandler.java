package com.assistant.mediatransfer;

import android.os.Message;
import android.text.TextUtils;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.mediatransfer.events.ChatMessageEvent;
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.events.Event;
import com.assistant.mediatransfer.events.NetEvent;
import com.assistant.mediatransfer.events.VerifyEvent;
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


    // key string is unique id of one connection.
    private Map<String, Object> mMsgCollections =
            Collections.synchronizedMap(new HashMap<String, Object>(10));

    NetEventHandler(MediaTransferManager mediaTransferManager,
                    ConnectionManager connectionManager ) {
        super(mediaTransferManager, connectionManager);
    }

    public ArrayList<ChatMessageEvent> getMessageList(int connId) {
        Connection connection = mConnectionManager.getConnection(connId);

        if (connection != null) {
            ClientInfo clientInfo = (ClientInfo) connection.getConnData();

            if (clientInfo != null && TextUtils.isEmpty(clientInfo.uniqueId)) {
                return (ArrayList<ChatMessageEvent>)mMsgCollections.get(clientInfo.uniqueId);
            } else {
                Log.d(this, "clientInfo not received for id:" + connId);
            }
        } else {
            Log.d(this, "connection not avaible for id:" + connId);
        }

        return null;
    }

    @Override
    void handleConnectionAdded(int connId) {
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
            Log.d(this, "NetEvent parse error:" + data);
            return;
        }

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
                    connection.setConnData(clientInfo);
                    List<ChatMessageEvent> mMsgArray =
                            Collections.synchronizedList(new ArrayList<ChatMessageEvent>());

                    mMsgCollections.put(clientInfo.uniqueId, mMsgArray);

                    notifyClientAvailable(connId, clientInfo);

                    // when connection connected, client send info to host side first.
                    // host side will send info back after received client info.
                    if (connection.isHost()) {
                        sendClientInfoEvent(connId);
                    }
                }
                break;
            case NetEvent.EVENT_CHAT:
                ChatMessageEvent event = Event.toEvent(netEvent.jsonData, ChatMessageEvent.class);

                if (event != null) {
                    ArrayList<ChatMessageEvent> msgList = getMessageList(connId);

                    msgList.add(event);

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
}
