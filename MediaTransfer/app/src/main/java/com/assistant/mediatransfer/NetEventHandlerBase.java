package com.assistant.mediatransfer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.assistant.connection.ConnectionManager;
import com.assistant.connection.DataSendListener;
import com.assistant.mediatransfer.events.Event;
import com.assistant.mediatransfer.events.NetEvent;
import com.assistant.mediatransfer.events.VerifyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

            }

            @Override
            public void onResult(long eventId, int ret, int failedReason) {
                // TODO: implement
            }
        });
    }

    private void handleEventTimeout() {
        synchronized (mToBeVerifiedEvents) {
            long sendTime;
            synchronized (mToBeVerifiedEvents) {
                for (Event event : mToBeVerifiedEvents) {
                    sendTime = event.uniqueId;
                    if (SystemClock.elapsedRealtime() - sendTime > 30*1000) {
                        // TODO: time out

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
}
