package pjsipapp.com.firebird;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by liyong on 17-7-21.
 */

public class PjSipBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d("PjSipBootReceiver", "received:" + intent.getAction());

        context.startService(new Intent(context, PjSipService.class));
    }
}
