package com.assistant;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.ui.MainActivity;

public class MediaTransferService extends Service {

    private static final String NETWORK_SEARCH_EXTRA = "network_search";
    private MediaTransferManager mMediaTransferManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        flags = START_STICKY;

        tryStartListenAndSearch(intent);

        return super.onStartCommand(intent, flags, startId);
    }

    private void tryStartListenAndSearch(Intent intent) {
        SharePreferencesHelper sharePreferencesHelper
                = SharePreferencesHelper.getInstance(getApplicationContext());

        if ((intent != null && intent.getBooleanExtra(NETWORK_SEARCH_EXTRA, false))
                && 1 == sharePreferencesHelper.getInt(
                SharePreferencesHelper.SP_KEY_NETWORK_ON, 1)) {
            mMediaTransferManager =
                    MediaTransferManager.getInstance(getApplicationContext());

            mMediaTransferManager.startListen();

            mMediaTransferManager.startSearchHost(null);

            // to keep service in foreground to avoid be killed.
            // and don't start service when network is off.
            showForegroundNotification();
        }
    }

    /*
     * This function is created as one util function to start self.
     *
     */
    public static void startService(Context context, boolean startSearch) {
        Intent intent = new Intent(context, MediaTransferService.class);
        intent.putExtra(NETWORK_SEARCH_EXTRA, startSearch);
        context.startService(intent);
    }

    private void showForegroundNotification() {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(getResources().getString(R.string.app_name))
                        .setContentText(getResources().getString(R.string.notification_keepalive_text));
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);

        startForeground(0, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // send one receiver out and try to start process again.
        Intent intent = new Intent("com.assistant.mediatransfer.startreceiver");
        sendBroadcast(intent);

        stopForeground(true);
    }
}
