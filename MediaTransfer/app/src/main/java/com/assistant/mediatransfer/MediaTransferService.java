package com.assistant.mediatransfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionFactory;
import com.assistant.connection.HostConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by alex on 17-8-17.
 */

public class MediaTransferService extends Service {

    ArrayList<HostConnection> mHostConnList = new ArrayList<>();
    Map<HostConnection, Connection> mSocketMap =
            Collections.synchronizedMap(new HashMap<HostConnection, Connection>());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private HostConnection.HostConnectionListener mHostListener =
            new HostConnection.HostConnectionListener() {
        @Override
        public void onConnectionConnected(HostConnection host, Connection connection) {
            if (connection != null) {
                mSocketMap.put(host, connection);
            }

            // TODO: notify to UI and handle data transfer.
        }

        @Override
        public void onHostClosed(HostConnection host, int errorCode) {
            if (host != null) {
                mHostConnList.remove(host);
            }

            // TODO: notify to UI
        }
    };

    private void listenInternal(int port) {
        final HostConnection hostConnection =
                ConnectionFactory.createHostConnection(port, mHostListener);

        if (hostConnection != null) {
            mHostConnList.add(hostConnection);
        }
    }
}
