package com.assistant.connection;

public interface EventSendResponse {
    int RESULT_SUCESS = 0;
    int RESULT_FAILED = -1;

    void onSendProgress(long eventId, int percent);

    // TODO: define failed reason code
    void onResult(int connId, long eventId, int result, int failedReason);
}
