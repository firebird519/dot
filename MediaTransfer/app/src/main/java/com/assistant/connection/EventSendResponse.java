package com.assistant.connection;

public interface EventSendResponse {
    int RESULT_SUCESS = 0;
    int RESULT_FAILED = -1;

    int FAILED_CONNECTION_CLOSED = -1;
    int FAILED_CONNECTION_IO_EXCEPTION = -2;
    int FAILED_TIMEOUT = -3;
    int FAILED_CONNECTION_SENDING = -4;

    void onSendProgress(long eventId, int percent);

    void onResult(int connId, long eventId, int result, int failedReason);
}
