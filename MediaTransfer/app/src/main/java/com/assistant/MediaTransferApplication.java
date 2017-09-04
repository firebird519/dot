package com.assistant;

import android.app.Application;
import android.content.Intent;

import com.assistant.MediaTransferService;

/**
 * Created by liyong on 17-9-4.
 */

public class MediaTransferApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        startMediaTransferService();
    }

    void startMediaTransferService() {
        Intent intent = new Intent(this, MediaTransferService.class);
        startService(intent);
    }
}
