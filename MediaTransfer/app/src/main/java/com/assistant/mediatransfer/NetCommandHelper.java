package com.assistant.mediatransfer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.assistant.connection.ConnectionManager;
import com.google.gson.Gson;

/**
 * Created by liyong on 17-8-25.
 *
 * Handler all net command receive and send.
 *
 */

public class NetCommandHelper {
    Gson mGson = new Gson();
    NetCommand mNetCommand = new NetCommand("", "");

    ConnectionManager mConnectionManager;

    ThreadHandler mHandler;

    class ThreadHandler extends Handler {
        ThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    NetCommandHelper(ConnectionManager connectionManager ) {
        mConnectionManager = connectionManager;

        HandlerThread thread = new HandlerThread("NetCommandHelper");

        thread.start();

        mHandler = new ThreadHandler(thread.getLooper());
    }

    public NetCommand parserNetCommand(String json) {
        return mGson.fromJson(json, NetCommand.class);
    }

    public ClientInfo parserClientInfo(String json) {
        return mGson.fromJson(json, ClientInfo.class);
    }

    public String toJsonString(NetCommand cmd) {
        return mGson.toJson(cmd, NetCommand.class);
    }

    public byte[] toNetCommandJson(ClientInfo info) {
        String data = mGson.toJson(info, ClientInfo.class);

        synchronized (mNetCommand) {
            mNetCommand.setCommand(NetCommand.COMMAND_CLIENT_INFO);
            mNetCommand.setData(data);
            String json = mGson.toJson(mNetCommand, NetCommand.class);
            mNetCommand.setCommand("");
            mNetCommand.setData("");
            return json.getBytes();
        }
    }

}
