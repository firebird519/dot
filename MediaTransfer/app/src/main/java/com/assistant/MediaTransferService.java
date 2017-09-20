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
import com.assistant.utils.Log;

public class MediaTransferService extends Service {

    private static final String NETWORK_SEARCH_EXTRA = "network_search";
    private static final String QUIT_EXTRA = "quit_flags";

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
        Log.d(this, "onStartCommand, intent:" +  intent);
        if (intent != null && intent.hasExtra(QUIT_EXTRA)) {
            Log.d(this, "has quit extra, remove stick flag and stop self");
            flags = 0;

            stopSelf();

            MediaTransferApplication.getInstance().quit(false);
            System.exit(0);
        } else {
            flags = START_STICKY;

            // to keep service in foreground to avoid be killed.
            // and don't start service when network is off.
            showForegroundNotification();

            tryStartListenAndSearch(intent);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void tryStartListenAndSearch(Intent intent) {
        boolean isNetworkOn = isNetworkOn();
        boolean hasSearchExtra = (intent != null
                && intent.getBooleanExtra(NETWORK_SEARCH_EXTRA,false));

        Log.d(this, "tryStartListenAndSearch, isNetworkOn:" + isNetworkOn
                + ", has search extra:" + hasSearchExtra);

        if (intent != null && intent.getExtras() != null) {
            for(String extra : intent.getExtras().keySet()) {
                Log.d(this, "   tryStartListenAndSearch, extra:" + extra + ", value:" + intent.getExtras().get(extra));
            }
        }

        if (hasSearchExtra && isNetworkOn()) {
            mMediaTransferManager =
                    MediaTransferManager.getInstance(getApplicationContext());

            mMediaTransferManager.startListen();

            mMediaTransferManager.startSearchHost(null);
        }
    }

    private boolean isNetworkOn() {
        SharePreferencesHelper sharePreferencesHelper
                = SharePreferencesHelper.getInstance(getApplicationContext());

        return (1 == sharePreferencesHelper.getInt(
                SharePreferencesHelper.SP_KEY_NETWORK_ON, 1));
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

    /*
    * To remove stick flag for this service to avoid it restarted after process ended!
    *
    */
    public static void startServiceForQuit(Context context) {
        Intent intent = new Intent(context, MediaTransferService.class);
        intent.putExtra(QUIT_EXTRA, "1");
        context.startService(intent);
    }

    private void showForegroundNotification() {
        Log.d(this, "showForegroundNotification");
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

        Log.d(this, "onDestroy");

        stopForeground(true);

        if (!MediaTransferApplication.getInstance().isQuited()) {
            // send one receiver out and try to start process again.
            Intent intent = new Intent("com.assistant.mediatransfer.startreceiver");
            sendBroadcast(intent);
        }
    }
}
