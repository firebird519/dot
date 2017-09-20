package com.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.assistant.utils.Log;

public class BootupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean startSearch = false;
        if (intent.getAction().equals("com.assistant.mediatransfer.startreceiver")
                || intent.getAction().equals(Intent.ACTION_PACKAGE_RESTARTED)) {
            startSearch = true;
        }

        Log.d(this, "onReceive, startSearch:" + startSearch + ", action:" + intent.getAction());
        MediaTransferService.startService(context, startSearch);
    }
}
