package pjsipapp.com.firebird;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by liyong on 17-7-21.
 */

public class PjSipService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.e("PjSipService","--->PjSipService onCreate");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
