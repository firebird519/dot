package com.assistant.connection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.assistant.bytestring.ByteString;
import com.assistant.bytestring.ByteStringPool;
import com.assistant.events.Event;
import com.assistant.events.EventFactory;
import com.assistant.events.FileEvent;
import com.assistant.events.NetEvent;
import com.assistant.events.VerifyEvent;
import com.assistant.utils.Log;
import com.assistant.utils.ThreadPool;
import com.assistant.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConnectionDataTracker extends Handler {
    private static final String TAG = "ConnectionDataTracker";
    private static final int EVENT_SEND_NETEVENT = 0;
    private static final int EVENT_NETEVENT_RECEIVED = 1;
    private static final int EVENT_SEND_NETEVENT_TIMEOUT = 2;
    private static final int EVENT_SEND_NETEVENT_RETRY = 3;

    public static final int MAX_NETEVENT_SEND_RETRY_COUNT = 3;

    public static final long SEND_EVENT_TIMEOUT_TIMESTAMP = 30000; //30s

    public interface DataTrackerListener {
        void onEventReceived(int connId, Event event);
    }

    private Context mContext;

    ConnectionManager mConnectionManager;

    private ThreadPool mThreadPool = new ThreadPool(5);

    private final Map<Integer, List<EventSendRequest>> mConnectionSendQueues =
            Collections.synchronizedMap(new HashMap<Integer, List<EventSendRequest>>());

    private final List<EventSendRequest> mToBeVerifiedRequests =
            Collections.synchronizedList(new ArrayList<EventSendRequest>());

    public ConnectionDataTracker(Context context, ConnectionManager connectionManager, Looper looper) {
        super(looper);

        mContext = context;
        mConnectionManager = connectionManager;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SEND_NETEVENT:
                handleEventSendRequest((EventSendRequest)msg.obj);
                break;
            case EVENT_NETEVENT_RECEIVED:
                handleReceivedNetEvent(msg.arg1, (NetEvent) msg.obj);
                break;
            case EVENT_SEND_NETEVENT_TIMEOUT:
                handleEventSendTimeout();
                break;
            case EVENT_SEND_NETEVENT_RETRY:
                handleEventSendRequestRetry((EventSendRequest)msg.obj);
                break;
            default:
                break;
        }
        super.handleMessage(msg);
    }

    public void onConnectionAdded(Connection connection) {
        Log.d(TAG, "onConnectionAdded, id:" + connection.getId());

        synchronized (mConnectionSendQueues) {
            List<EventSendRequest> sendQueue = mConnectionSendQueues.get(connection.getId());
            if (sendQueue == null) {
                mConnectionSendQueues.put(connection.getId(),
                        Collections.synchronizedList(new ArrayList<EventSendRequest>(3)));
            }

            new ConnectionReceiverThread(connection).start();
        }
    }

    public void onConnectionRemoved(Connection connection) {
        synchronized (mConnectionSendQueues) {
            List<EventSendRequest> sendQueue = mConnectionSendQueues.remove(connection.getId());

            if (sendQueue != null && sendQueue.size() > 0) {
                for (EventSendRequest request : sendQueue) {
                    if (request.response != null) {
                        request.response.onResult(connection.getId(),
                                request.event.uniqueId,
                                EventSendResponse.RESULT_FAILED,
                                EventSendResponse.FAILED_CONNECTION_CLOSED);
                    }
                }
            }
        }
    }

    private Set<DataTrackerListener> mListeners =
            new CopyOnWriteArraySet<>();

    public void addListener(DataTrackerListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public void removeListener(DataTrackerListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyEventReceived(int connId, Event event) {
        Log.d(TAG, "notifyEventReceived, connId:" + connId
                + ", event:" + event.getEventClassName());
        for(DataTrackerListener listener : mListeners) {
            listener.onEventReceived(connId, event);
        }
    }

    private void handleEventSendRequestRetry(EventSendRequest request) {
        // request already timeout. not retry again!
        if (!mToBeVerifiedRequests.contains(request)) {
            Log.d(TAG, "handleEventSendRequestRetry, request timeout:" + request.toString());
            return;
        }

        Log.d(TAG, "handleEventSendRequestRetry, resend request:" + request.toString());
        sendEvent(request);
    }

    public void sendEvent(EventSendRequest request) {
        if (request == null) {
            return;
        }

        List<EventSendRequest> queue = getSendRequestQueue(request.event.connId);

        // maybe request already in queue, it's one retry...
        if (queue != null && !queue.contains(request)) {
            queue.add(request);

            Log.d(TAG, "sendEvent, connId:" + request.event.connId
                    + ", queue size:" + queue.size());
        }

        processNextRequest(request.event.connId);
    }

    private void handleEventSendRequest(EventSendRequest request) {
        if (request == null || request.event == null) {
            Log.d(TAG, "handleEventSendRequest, request or event is null!");
            //processNextRequest(-1);
            return;
        }

        Log.d(TAG, "handleEventSendRequest, connId:" + request.event.connId
                        + ", eventId:" + request.event.toString());

        // don't put verify event into to be verified queue.
        // and it this request is retry request, it should be already in to be verify list.
        if (request.event.getEventType() != Event.EVENT_TYPE_VERIFY
                && !mToBeVerifiedRequests.contains(request)) {
            mToBeVerifiedRequests.add(request);
        }

        request.lastSendTime = SystemClock.elapsedRealtime();

        NetEvent netEvent =
                new NetEvent(request.event.getEventType(),
                        request.event.uniqueId,
                        request.event.toJsonString());

        byte[] bytes = netEvent.toBytes();
        String strFilePathName = "";
       if (request.event instanceof FileEvent) {
            strFilePathName = ((FileEvent) request.event).filePathName;
        }

        Log.d(TAG, "handleEventSendRequest, request:" + request.toString());
        mThreadPool.addTask(new EventSendRunnable(request.event.connId,
                request.event.uniqueId,
                bytes,
                bytes.length,
                strFilePathName,
                request));
    }

    private List<EventSendRequest> getSendRequestQueue(int connId) {
        return mConnectionSendQueues.get(connId);
    }

    private void processNextRequest(int connId) {
        Log.d(TAG, "processNextRequest, connId:" + connId);

        List<EventSendRequest> queue = getSendRequestQueue(connId);
        if (queue != null && queue.size() > 0) {
            EventSendRequest request = queue.remove(0);

            Log.d(TAG, "processNextRequest, connId:" + request.event.connId
                    + ", queue size:" + queue.size());

            obtainMessage(EVENT_SEND_NETEVENT, request).sendToTarget();
        }
    }

    private void handleReceivedNetEvent(int connId, NetEvent netEvent) {
        if (netEvent == null) {
            Log.d(this, "handleNetEvent, possible?");
            return;
        }

        Log.d(this, "handleReceivedNetEvent, connId:" + connId
                + ", netEvent:" + netEvent.jsonData);

        Event event = EventFactory.toEvent(connId,
                netEvent.jsonData,
                netEvent.eventType,
                true);

        if (event instanceof VerifyEvent) {
            handleEventVerified((VerifyEvent) event);

            return;
        }

        verifyEvent(connId, netEvent.eventType, netEvent.eventId);
        if (event != null) {
            notifyEventReceived(connId, event);
        } else {
            Log.e(TAG, "handleReceivedNetEvent parse json failed.");
        }
    }

    private void verifyEvent(int connId, int eventType, long eventId) {
        Log.d(TAG, "verifyEvent, connId:" + connId
                + ", eventType:" + eventType + ", eventId:" + eventId);
        VerifyEvent verifyEvent = new VerifyEvent(connId, eventType, eventId);

        sendEvent(new EventSendRequest(
                verifyEvent, null));
    }

    private void handleEventVerified(VerifyEvent verifyEvent) {
        if (verifyEvent != null) {
            synchronized (mToBeVerifiedRequests) {
                for (EventSendRequest request: mToBeVerifiedRequests) {
                    if (request.event.uniqueId == verifyEvent.eventUnId) {
                        request.event.setState(Event.STATE_VERIFIED);
                        mToBeVerifiedRequests.remove(request);
                        break;
                    }
                }
            }
        }
    }

    private void handleEventSendTimeout() {
        synchronized (mToBeVerifiedRequests) {
            long timeoutTimestamp;
            for (EventSendRequest request: mToBeVerifiedRequests) {

                if (request.event.getEventType() != Event.EVENT_TYPE_FILE) {
                    timeoutTimestamp = SEND_EVENT_TIMEOUT_TIMESTAMP;
                } else {
                    timeoutTimestamp = getTimeoutTimestampForFileSend((FileEvent)request.event);
                }

                if (SystemClock.elapsedRealtime() - request.lastSendTime > timeoutTimestamp) {
                    request.event.setState(Event.STATE_TIMEOUT);

                    if (!tryResendEvent(request, EventSendResponse.FAILED_TIMEOUT)) {
                        if (request.response != null) {
                            request.response.onResult(request.event.connId,
                                    request.event.uniqueId,
                                    EventSendResponse.RESULT_FAILED,
                                    EventSendResponse.FAILED_TIMEOUT);
                        }

                        mToBeVerifiedRequests.remove(request);
                    }
                }
            }
        }
    }

    /*
     * ATTENTION: As currently only for wifi network. define timeout 30s stamp for 1M data.
     */
    private long getTimeoutTimestampForFileSend(FileEvent event) {
        return SEND_EVENT_TIMEOUT_TIMESTAMP
                * event.fileSize/(1024*1024);
    }

    private boolean tryResendEvent(EventSendRequest request, int failedReason) {
        Log.d(TAG, "tryResendEvent, request retryCount:" + request.retryCount
                + ", failedReason:"  +failedReason);
        if (request.retryCount < MAX_NETEVENT_SEND_RETRY_COUNT &&
                (failedReason == EventSendResponse.FAILED_CONNECTION_CLOSED
                || failedReason == EventSendResponse.FAILED_CONNECTION_SENDING
                || failedReason == EventSendResponse.FAILED_CONNECTION_IO_EXCEPTION)) {
            request.retryCount ++;
            obtainMessage(EVENT_SEND_NETEVENT_RETRY ,request).sendToTarget();
            return true;
        }

        return false;
    }

    class EventSendRunnable implements Runnable {
        private int connId;
        private byte[] bytes;
        private long bytesLen;
        private long eventId;
        private EventSendResponse response;
        private String filePathName;

        private EventSendRequest mRequest;

        EventSendRunnable(final int connectionId,
                          final long sendEventId,
                          final byte[] jsonBytes,
                          final long jsonLen,
                          final String strFilePathName,
                          final EventSendRequest request) {
            connId = connectionId;
            bytes = jsonBytes;
            bytesLen = jsonLen;
            eventId = sendEventId;
            filePathName = strFilePathName;
            response = request.response;
            mRequest = request;
        }

        @Override
        public void run() {
            Connection conn = mConnectionManager.getConnection(connId);
            int ret = 0;
            boolean success = false;

            Log.d(TAG, "EventSendRunnable, conn:" + conn + ", bytesLen:" + bytesLen);

            if (conn != null) {
                Log.d(TAG, "EventSendRunnable, filePathName:" + filePathName);
                if (TextUtils.isEmpty(filePathName)) {
                    byte[] header = generateDataHeader(bytesLen, 0);

                    Log.d(TAG, "EventSendRunnable, header len:" + header.length);

                    ret = conn.send(header, ConnectionManager.DATA_HEADER_LEN_v1);

                    if (ret == ConnectionManager.DATA_HEADER_LEN_v1) {
                        ret = conn.send(bytes, bytesLen);

                        if (ret == bytesLen) {
                            success = true;
                        }
                    }

                    Log.d(TAG, "EventSendRunnable, send ret:" + ret);

                    notifySendProgress(100);
                    responseSendResult(success, 0, ret);
                } else {
                    Log.d(TAG, "EventSendRunnable, send file:" + filePathName);
                    FileInputStream fileInputStream;
                    try {
                        fileInputStream = new FileInputStream(filePathName);
                        int fileLen = fileInputStream.available();

                        byte[] header = generateDataHeader(bytesLen, fileLen);

                        ret = conn.send(header, ConnectionManager.DATA_HEADER_LEN_v1);
                        if (ret == ConnectionManager.DATA_HEADER_LEN_v1) {
                            ret = conn.send(bytes, bytesLen);

                            if (ret == bytesLen && fileLen > 0) {
                                int bufferSize =
                                        fileLen > Connection.SOCKET_DEFAULT_BUF_SIZE ?
                                                Connection.SOCKET_DEFAULT_BUF_SIZE : fileLen;

                                byte[] buffer = new byte[bufferSize];

                                int sentBytes = 0;
                                int bytesFileRead;
                                do {
                                    bytesFileRead = fileInputStream.read(buffer);
                                    ret = conn.send(buffer, bytesFileRead);

                                    if (ret == bytesFileRead) {

                                        sentBytes += ret;

                                        notifySendProgress((sentBytes * 100) / fileLen);
                                    } else {
                                        success = false;
                                        break;
                                    }
                                } while (sentBytes < fileLen);

                                if (sentBytes == fileLen) {
                                    success = true;
                                }
                            } else if (ret == bytesLen) {
                                notifySendProgress(100);
                                success = true;
                            }
                        }

                        fileInputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    responseSendResult(success, ret, ret);
                }

                processNextRequest(connId);
            } else {
                responseSendResult(false, EventSendResponse.FAILED_CONNECTION_CLOSED, 0);
            }
            Log.d(TAG, "EventSendRunnable, ended for connId:" + connId);
        }

        /**
         * version 1(22 bytes):"[v:(long);j:(long);f:(long)]"
         * v: version num. long type.
         * j: json length. long type.
         * f: file length. long type.
         */
        private byte[] generateDataHeader(long jsonLen, long fileLen) {
            ByteBuffer buf = ByteBuffer.allocate(ConnectionManager.DATA_HEADER_LEN_v1);

            buf.put("[v:".getBytes());

            //Log.d(TAG, "p1:" + buf.position());
            buf.putLong(1);
            //Log.d(TAG, "p2:" + buf.position());
            buf.put(";j:".getBytes());
            //Log.d(TAG, "p3:" + buf.position());
            buf.putLong(jsonLen);
            //Log.d(TAG, "p4:" + buf.position());
            buf.put(";f:".getBytes());
            //Log.d(TAG, "p5:" + buf.position());
            buf.putLong(fileLen);
            //Log.d(TAG, "p6:" + buf.position());
            buf.put("]".getBytes());

            Log.d(TAG, "generateDataHeader, jsonLen:" + jsonLen
                    + ", fileLen:" + fileLen
                    + ", json:" + Utils.bytesToHexString(buf.array()));

            return buf.array();
        }

        void notifySendProgress(int percent) {
            if (response != null) {
                response.onSendProgress(eventId, percent);
            }
        }
        void responseSendResult(boolean isSucess, int reason, int sendBytesCount) {
            if (response != null) {
                if (isSucess) {
                    response.onResult(connId,
                            eventId,
                            EventSendResponse.RESULT_SUCESS,
                            sendBytesCount);
                } else {
                    int failedCode = Connection.ConnectionFailedReasonToResponseFailedCode(reason);
                    if (!tryResendEvent(mRequest, failedCode)) {
                        response.onResult(connId,
                                eventId,
                                EventSendResponse.RESULT_FAILED,
                                failedCode);
                    }
                }
            }
        }
    }

    private class ConnectionReceiverThread extends Thread {
        private Connection mConnection;
        private ByteBuffer mHeaderBuf = ByteBuffer.allocate(ConnectionManager.DATA_HEADER_LEN_v1);

        private long mHeaderVersion;
        private long mJsonLen;
        private long mFileLen;

        private static final int RECEIVING_IDLE = 0;
        private static final int RECEIVING_HEADER = 1;
        private static final int RECEIVING_JSON = 2;
        private static final int RECEIVING_FILE = 3;

        private int mReceivingState = RECEIVING_HEADER;

        ConnectionReceiverThread(Connection connection) {
            mConnection = connection;
        }

        @Override
        public void run() {
            while(true) {
                if (mConnection.getState() != Connection.CONNECTION_STATE_CONNECTED) {
                    Log.d(this, "connection closed:" + mConnection.getIp()
                            + ", state:" + mConnection.getState());
                    break;
                }

                Log.d(TAG, "ConnectionReceiverThread, mReceivingState:" + mReceivingState);

                if (mReceivingState == RECEIVING_HEADER) {
                    int bytesReceived = 0;
                    if (mConnection != null &&
                            mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                        bytesReceived = mConnection.receive(mHeaderBuf.array(), ConnectionManager.DATA_HEADER_LEN_v1);
                    }

                    if (bytesReceived == ConnectionManager.DATA_HEADER_LEN_v1) {
                        handleConnectionHeader();
                    } else {
                        closeConnection();
                    }
                } else if (mReceivingState == RECEIVING_JSON) {
                    Log.d(TAG, "ConnectionReceiverThread, mJsonLen:" + mJsonLen);
                    if (mJsonLen <= ByteString.DEFAULT_STRING_SIZE) {
                        ByteString buf = handleDataReceiving((int)mJsonLen);
                        if (buf != null) {
                            if (mFileLen > 0) {
                                setState(RECEIVING_FILE);
                            } else {
                                setState(RECEIVING_HEADER);
                            }

                            if (Utils.DEBUG) {
                                Log.d(TAG, "toEvent:" + buf.toString());
                            }

                            NetEvent event = null;
                            try {
                                event = NetEvent.toEvent(buf.toString(), NetEvent.class);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception for json parser. json:" + buf.toString());
                            }

                            if (event != null) {
                                Log.d(this, "NetEvent parsed:"
                                        + buf.toString());

                                obtainMessage(EVENT_NETEVENT_RECEIVED,
                                        mConnection.getId(), 0, event)
                                        .sendToTarget();
                            } else {
                                Log.e(this, "NetEvent parse failed. Ignored! data:"
                                        + buf.toString());
                            }

                            buf.release();
                        } else {
                            closeConnection();
                        }
                    } else {
                        Log.e(this, "some problems happened. one big json coming... ignore!");
                        closeConnection();
                    }

                } else if (mReceivingState == RECEIVING_FILE) {
                    ByteString buf = handleDataReceiving((int)mFileLen);

                    if (buf != null) {
                        setState(RECEIVING_HEADER);

                            /*
                            Message msg = mThreadHandler.obtainMessage(
                                    EVENT_CONNECTION_DATA_RECEIVED,
                                    mConnection.getId(),
                                    (mFileLen > ByteString.DEFAULT_STRING_SIZE ? 1 : 0));

                            msg.obj = buf.toString();
                            msg.sendToTarget();*/
                        buf.release();
                    } else {
                        closeConnection();
                    }
                } else {
                    // ??
                }
            }
        }

        private void setState(int state) {
            Log.d(TAG, "ConnectionReceiverThread, setState:" + state);
            mReceivingState  = state;

            if (state == RECEIVING_HEADER) {
                mFileLen = 0;
                mJsonLen = 0;
                mHeaderVersion = 0;
            }
        }

        /*
         * version 1(22 bytes):"[v:(long);j:(long);f:(long)]"
         *      v: version num. long type.
         *      j: json length. long type.
         *      f: file length. long type.
         *
         */
        private void handleConnectionHeader() {
            try {
                Log.d(TAG, "handleConnectionHeader, received header:"
                        + Utils.bytesToHexString(mHeaderBuf.array()));
            } catch (Exception e) {
                e.printStackTrace();
            }

            byte start = mHeaderBuf.get();
            byte v = mHeaderBuf.get();
            byte colon1 = mHeaderBuf.get();
            mHeaderVersion = mHeaderBuf.getLong();
            byte sep1 = mHeaderBuf.get();
            byte j = mHeaderBuf.get();
            byte colon2 = mHeaderBuf.get();
            mJsonLen = mHeaderBuf.getLong();
            byte sep2 = mHeaderBuf.get();
            byte f = mHeaderBuf.get();
            byte colon3 = mHeaderBuf.get();
            mFileLen = mHeaderBuf.getLong();
            byte end = mHeaderBuf.get();

            Log.d(TAG, "handleConnectionHeader, mHeaderVersion:" + mHeaderVersion
                    + ", mJsonLen:" + mJsonLen
                    + ", mFileLen:" + mFileLen);

            if (mHeaderVersion == ConnectionManager.DATA_HEADER_V1 &&
                    start == '[' &&
                    end == ']' &&
                    v == 'v' &&
                    j == 'j' &&
                    f == 'f' &&
                    colon1 == ':' &&
                    colon2 == ':' &&
                    colon3 == ':' &&
                    sep1 == ';' &&
                    sep2 == ';') {
                setState(RECEIVING_JSON);
            } else {
                Log.d(TAG, "handleConnectionHeader, mHeaderBuf not right:"
                        + mHeaderBuf.toString());

                // some problems happened! close connection.
                closeConnection();
            }

            // reset header buffer
            mHeaderBuf.clear();
        }

        private ByteString handleDataReceiving(int len) {
            if (mConnection == null ||
                    mConnection.getState() != Connection.CONNECTION_STATE_CONNECTED) {
                return null;
            }

            if (len <= ByteString.DEFAULT_STRING_SIZE) {
                ByteString buf = ByteStringPool.getInstance().getByteString();
                int bytesReceived = 0;
                if (mConnection != null &&
                        mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                    bytesReceived = mConnection.receive(buf.data, len);
                }

                if (bytesReceived != len) {
                    // some problem happened.
                    buf.release();
                    buf = null;
                } else {
                    buf.setDataLen(len);
                }

                return buf;
            } else {
                String path = Utils.getAppStoragePath(mContext);

                if (TextUtils.isEmpty(path)) {
                    // some problems happened that app file path get failed.
                    return null;
                }

                String fileName = String.valueOf(mConnection.hashCode()) +
                        "_" +
                        String.valueOf(System.currentTimeMillis() % 10000);

                File file = new File(path, fileName);
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                FileOutputStream fileOpStream = null;
                try {
                    fileOpStream = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    Log.e(this, "can't create FileOutputStream");
                    e.printStackTrace();
                    return null;
                }

                long receivedCount = 0;
                long bytesToReceive = 0;
                long bytesReceived = 0;

                boolean errorHappened = false;

                ByteString buf = ByteStringPool.getInstance().getByteString();
                do {
                    if (len - receivedCount >= ByteString.DEFAULT_STRING_SIZE) {
                        bytesToReceive = ByteString.DEFAULT_STRING_SIZE;
                    } else {
                        bytesToReceive = len - receivedCount;
                    }

                    if (mConnection != null &&
                            mConnection.getState() == Connection.CONNECTION_STATE_CONNECTED) {
                        bytesReceived = mConnection.receive(buf.data, bytesToReceive);
                    }

                    if (bytesToReceive != bytesReceived) {
                        errorHappened = true;
                        break;
                    }

                    receivedCount += bytesReceived;

                    try {
                        fileOpStream.write(buf.data, 0, (int) bytesReceived);
                    } catch (IOException e) {
                        e.printStackTrace();

                        errorHappened = true;
                        break;
                    }
                } while (len > receivedCount);

                try {
                    fileOpStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    errorHappened = true;
                }

                if (errorHappened) {
                    buf.release();
                    buf = null;
                } else {
                    String filePathName = path + "/" + fileName;
                    int length = buf.putData(filePathName.getBytes());

                    if (length == 0) {
                        Log.d(this, "copy file path name to bytestring error.");
                        buf.release();
                        buf = null;
                    }
                }

                return buf;
            }
        }

        private void closeConnection() {
            if (mConnection != null) {
                mConnection.close(Connection.CONNECTION_REASON_CODE_IO_EXCEPTION);
            }
        }
    }
}
