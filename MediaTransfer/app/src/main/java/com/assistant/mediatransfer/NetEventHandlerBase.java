package com.assistant.mediatransfer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.assistant.connection.ConnectionManager;
import com.assistant.connection.DataSendListener;
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.events.Event;
import com.assistant.mediatransfer.events.NetEvent;
import com.assistant.mediatransfer.events.VerifyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by alex on 17-8-27.
 */

public abstract class NetEventHandlerBase {
    protected ConnectionManager mConnectionManager;
    protected MediaTransferManager mMediaTransferManager;

    protected ThreadHandler mThreadHandler;

    private final List<Event> mToBeVerifiedEvents =
            Collections.synchronizedList(new ArrayList<Event>());

    private ConnectionManager.ConnectionManagerListener mConnectionMgrListener =
            new ConnectionManager.ConnectionManagerListenerBase() {
                @Override
                public void onConnectionAdded(int id) {
                    //
                    handleConnectionAdded(id);
                }

                @Override
                public void onConnectionRemoved(int id, int reason) {
                    //
                    handleConnectionRemoved(id);
                }

                @Override
                public void onDataReceived(int id, String data, boolean isFile) {
                    if (!isFile) {
                        handleNetEvent(id, data);
                    } else {
                        // TODO: to be implemented
                    }
                }
            };

    class ThreadHandler extends Handler {
        public static final int EVENT_TIMEOUT_CHECK = 0;

        public static final long TIMESTAMP_TIMEOUT_CHECK = 5000; //5s
        ThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (!handleThreadHandlerMessage(msg)) {
                switch (msg.what) {
                    case EVENT_TIMEOUT_CHECK:
                        handleEventTimeout();
                        break;
                    default:
                        break;
                }
            }
            super.handleMessage(msg);
        }
    }

    NetEventHandlerBase(MediaTransferManager mediaTransferManager,
                    ConnectionManager connectionManager ) {
        mConnectionManager = connectionManager;
        mConnectionManager.addListener(mConnectionMgrListener);

        mMediaTransferManager = mediaTransferManager;

        HandlerThread thread = new HandlerThread("NetEventHandlerBase");

        thread.start();

        mThreadHandler = new ThreadHandler(thread.getLooper());
    }

    abstract void handleNetEvent(int connId, String data);
    abstract void handleConnectionAdded(int connId);
    abstract void handleConnectionRemoved(int connId);
    abstract boolean handleThreadHandlerMessage(Message msg);

    public void sendEvent(int connId, Event event) {
        String eventName = event.getEventTypeName();

        cacheToBeVerifiedEvent(event);

        NetEvent netEvent = new NetEvent(eventName, event.toJsonString());
        byte[] bytes = netEvent.toBytes();
        mConnectionManager.sendEvent(connId, event.uniqueId,  bytes, bytes.length,
                new DataSendListener() {
            @Override
            public void onSendProgress(long eventId, int percent) {
                // TODO:
            }

            @Override
            public void onResult(long eventId, int ret, int failedReason) {
                Event event = getEventFromToBeVerifiedCache(eventId);

                if (event != null) {
                    updateEventState(event.connId, event, Event.STATE_SENT);
                } else {

                }
            }
        });
        //updateEventState(connId, event, Event.STATE_SENDING);
    }

    private void updateEventState(int connId, Event event, int state) {
        event.setState(state);
        notifyEventStateUpdated(connId, event);
    }

    private void handleEventTimeout() {
        synchronized (mToBeVerifiedEvents) {
            long sendTime;
            synchronized (mToBeVerifiedEvents) {
                for (Event event : mToBeVerifiedEvents) {
                    sendTime = event.uniqueId;
                    if (SystemClock.elapsedRealtime() - sendTime > 30*1000) {
                        updateEventState(event.connId, event, Event.STATE_SENT);
                    }
                }
            }
        }
    }

    private void cacheToBeVerifiedEvent(Event event) {
        synchronized (mToBeVerifiedEvents) {
            mToBeVerifiedEvents.add(event);

            mThreadHandler.sendEmptyMessageDelayed(ThreadHandler.EVENT_TIMEOUT_CHECK,
                    ThreadHandler.TIMESTAMP_TIMEOUT_CHECK);

        }
    }

    protected Event getEventFromToBeVerifiedCache(long eventId) {
        synchronized (mToBeVerifiedEvents) {
            for (Event event : mToBeVerifiedEvents) {
                if (event.uniqueId == eventId) {
                    return event;
                }
            }
        }

        return null;
    }

    protected void handleEventVerify(VerifyEvent verifyEvent) {
        String name;
        synchronized (mToBeVerifiedEvents) {
            for (Event event : mToBeVerifiedEvents) {
                name = event.getEventTypeName();

                if (name.equals(verifyEvent.eventName)
                        && event.uniqueId == verifyEvent.eventUnId) {
                    mToBeVerifiedEvents.remove(event);

                    if (mToBeVerifiedEvents.size() == 0) {
                        mThreadHandler.removeMessages(ThreadHandler.EVENT_TIMEOUT_CHECK);
                    }
                    break;
                }
            }
        }
    }

    protected void verifyEvent(int connId, String event, long msgIndex) {
        sendEvent(connId, new VerifyEvent(event, msgIndex));
    }

    private Set<ConnectionEventListener> mListeners =
            new CopyOnWriteArraySet<>();

    public void addListener(ConnectionEventListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ConnectionEventListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    protected void notifyClientAvailable(int connId, ClientInfo info) {
        for (ConnectionEventListener listener : mListeners) {
            listener.onClientAvailable(connId, info);
        }

    }

    protected void notifyEventReceived(int connId, Event event) {
        for (ConnectionEventListener listener : mListeners) {
            listener.onEventReceived(connId, event);
        }
    }

    protected void notifyEventStateUpdated(int connId, Event event) {
        for (ConnectionEventListener listener : mListeners) {
            listener.onEventStateUpdated(connId, event);
        }
    }
}
