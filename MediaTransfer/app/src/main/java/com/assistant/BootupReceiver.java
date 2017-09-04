package com.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by liyong on 17-9-4.
 */

public class BootupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        startMediaTransferService(context);
    }

    void startMediaTransferService(Context context) {
        Intent intent = new Intent(context, MediaTransferService.class);
        context.startService(intent);
    }
}
