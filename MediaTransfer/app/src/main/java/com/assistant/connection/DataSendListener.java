package com.assistant.connection;

/**
 * Created by alex on 17-8-26.
 */

public interface DataSendListener {
    public static final int RESULT_SUCESS = 0;
    public static final int RESULT_FAILED = -1;

    void onSendProgress(long eventId, int percent);
    void onResult(long eventId, int result, int failedReason);
}
