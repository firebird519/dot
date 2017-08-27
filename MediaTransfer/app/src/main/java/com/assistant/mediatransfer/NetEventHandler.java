package com.assistant.mediatransfer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.connection.DataSendListener;
import com.assistant.mediatransfer.events.ChatMessageEvent;
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.events.Event;
import com.assistant.mediatransfer.events.NetEvent;
import com.assistant.mediatransfer.events.VerifyEvent;

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

    // used to record message index for connections
    class ConnMessageInfo {
        private int connId;
        private int msgIndex;

        ConnMessageInfo(int id, int index) {
            connId = id;
            msgIndex = index;
        }

        public int getConnId() {
            return connId;
        }

        public synchronized int increaseIndex() {
            msgIndex += 1;

            return msgIndex;
        }

        public synchronized int getMsgIndex() {
            return msgIndex;
        }
    }

    private Map<Integer, Object> mConnMsgInfos =
            Collections.synchronizedMap(new HashMap<Integer, Object>(10));


    NetEventHandler(MediaTransferManager mediaTransferManager,
                    ConnectionManager connectionManager ) {
        super(mediaTransferManager, connectionManager);
    }

    @Override
    void handleConnectionAdded(int connId) {
        mConnMsgInfos.put(connId, new ConnMessageInfo(connId, 0));
    }

    @Override
    void handleConnectionRemoved(int connId) {
        mConnMsgInfos.remove(connId);
    }

    @Override
    boolean handleThreadHandlerMessage(Message msg) {
        return false;
    }

    @Override
    void handleNetEvent(int connId, String data) {
        NetEvent event = Event.toEvent(data, NetEvent.class);

        if (!NetEvent.EVENT_VERIFY.equals(event.name)) {
            verifyEvent(connId, event.name, event.unId);
        }

        switch (event.name) {
            case NetEvent.EVENT_VERIFY:
                handleEventVerify(Event.toEvent(event.jsonData, VerifyEvent.class));
                break;
            case NetEvent.EVENT_CLIENT_INFO:
                ClientInfo clientInfo = Event.toEvent(event.jsonData, ClientInfo.class);

                Connection connection = mConnectionManager.getConnection(connId);
                if (connection != null) {
                    connection.setConnData(clientInfo);
                    List<ChatMessageEvent> mMsgArray =
                            Collections.synchronizedList(new ArrayList<ChatMessageEvent>());

                    mMsgCollections.put(clientInfo.uniqueId, mMsgArray);

                    // when connection connected, client send info to host side first.
                    // host side will send info back after received client info.
                    if (connection.isHost()) {
                        sendClientInfoEvent(connId);
                    }
                }
                break;
            case NetEvent.EVENT_CHAT:
                // notify to UI.
                break;
            case NetEvent.EVENT_FILE:
                break;
        }
    }

    private void sendClientInfoEvent(int connId) {
        sendEvent(connId, mMediaTransferManager.getClientInfo());
    }
}
