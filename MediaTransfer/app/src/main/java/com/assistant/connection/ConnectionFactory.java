package com.assistant.connection;

import android.os.PowerManager;

import com.assistant.utils.Log;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

public class ConnectionFactory {
    private static final String TAG = "ConnectionFactory";

    public static HostConnection createHostConnection(int port,
                                                      PowerManager.WakeLock wakeLock,
                                                      HostConnection.HostConnectionListener listener) {
        HostConnection host  = new HostConnection();
        if (0 != host.listen(port, wakeLock, listener)) {
            // some error happened. null will be returned!
            return null;
        }

        return host;
    }

    public static Connection createConnection(String ip,
                                              int port,
                                              final ConnectionManager.ConnectRequest request,
                                              Connection.ConnectionListener listener) {
        Log.d(TAG, "createConnection0:" + ip);
        Connection conn = null;
        try {
            conn = new Connection(null, false, request);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(TAG, "createConnection1:" + ip);

        if (conn != null) {
            if (listener != null) {
                conn.addListner(listener);
            }

            Log.d(TAG, "createConnection:" + ip);

            conn.connect(ip, port);
        } else {
            Log.e(TAG, "WARNING: new Connection failed for ip:" + ip);
        }

        return conn;
    }
}
