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
            host.close();
            return null;
        }

        return host;
    }

    public static Connection createConnection(String ip,
                                              int port,
                                              Connection.ConnectionListener listener) {
        return createConnection(ip, port, -1, listener);
    }

    public static Connection createConnection(String ip,
                                              int port,
                                              int connId,
                                              Connection.ConnectionListener listener) {
        Log.d(TAG, "createConnection:" + ip + ", port:" + port + ", id:" + connId);
        Connection conn = null;
        try {
            conn = new Connection(connId, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (conn != null) {
            if (listener != null) {
                conn.addListner(listener);
            }

            Log.d(TAG, "createConnection connect to:" + ip);

            conn.connect(ip, port);
        } else {
            Log.e(TAG, "WARNING: new Connection failed for ip:" + ip);
        }

        return conn;
    }
}
