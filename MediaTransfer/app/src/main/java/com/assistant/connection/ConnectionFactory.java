package com.assistant.connection;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * Created by alex on 17-8-17.
 */

public class ConnectionFactory {


    public static HostConnection createHostConnection(int port, HostConnection.HostConnectionListener listener) {
        HostConnection host  = new HostConnection();
        if (0 != host.listen(port, listener)) {
            // some error happened. null will be returned!
            return null;
        }

        return host;
    }

    public static Connection createConnection(String ip, int port,
                                              Connection.ConnectionListener listener) {
        Connection conn = new Connection(null);

        if (conn != null) {
            conn.addListner(listener);

            conn.connect(ip, port);
        }

        return conn;
    }

    private static Socket createSocket(String ip, int port) {
        Socket socket = null;

        try {
            socket = SocketFactory.getDefault().createSocket(ip, port);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return socket;
    }

    public static Connection createConnectionSync(String ip, int port) {
        Socket socket = createSocket(ip, port);

        Connection conn = null;
        if (socket != null && socket.isConnected()) {
            conn = new Connection(socket);
        }

        return conn;
    }
}
