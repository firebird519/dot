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
                                              final ConnectionManager.ReConnectRequest request,
                                              Connection.ConnectionListener listener) {
        Connection conn = new Connection(null, false, request);

        if (conn != null) {
            if (listener != null) {
                conn.addListner(listener);
            }

            conn.connect(ip, port);
        }

        return conn;
    }

    private static Socket createSocket(String ip, int port) {
        Socket socket = null;

        try {
            Log.d(TAG, "createSocket:" + ip + ", port:" + port);
            socket = SocketFactory.getDefault().createSocket(ip, port);

            socket.setKeepAlive(true);
        } catch (UnknownHostException e) {
            //e.printStackTrace();
            Log.d(TAG, "UnknownHost for ip:" + ip);
        } catch (IOException e) {
            Log.d(TAG, "IOException, io exception for ip:" + ip);
            //e.printStackTrace();
        } catch (Exception e) {
            //e.printStackTrace();
            Log.d(TAG, "Exception, exception for ip:" + ip + ", msg:" + e.getMessage());
        }

        return socket;
    }

    public static Connection createConnectionSync(String ip, int port) {
        Log.d(TAG, "createConnectionSync:" + ip + ", port:" + port);
        Socket socket = createSocket(ip, port);

        Connection conn = null;
        if (socket != null && socket.isConnected()) {
            conn = new Connection(socket, false);
        }

        return conn;
    }
}
