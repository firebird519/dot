package com.assistant.connection;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.assistant.utils.NetworkInfoManager;
import com.assistant.utils.ThreadPool;
import com.assistant.utils.IPv4Utils;
import com.assistant.utils.Utils;


/*
 * Handler server search.
 */
public class HostSearchHandler {
    public static final int SERVER_SEARCH_FAILED_WIFI_NOT_CONNECTED = 1;
    public static final int SERVER_SEARCH_CANCELED = 2;

    public static final int IP_MARK_CONNECT_FAILED = -1;
    public static final int IP_MARK_IDLE = 0;
    public static final int IP_MARK_CONNECTED = 1;
    public static final int IP_MARK_CONNECTING = 2;
    private static final String TAG = "HostSearchHandler";
    private static final int poolSize = 10;
    private static HostSearchHandler sInstance;

    int mPort;

    // TODO: move this ip mask out and to support different network segment.
    int[] mSearchIpMask = new int[256];
    private Context mContext;
    private ThreadPool mThreadPool;
    private boolean mIsSearching;

    private ServerSearchListener mListener;

    private HostSearchHandler(Context context) {
        mContext = context;
        mPort = 0; //TODO: add one default port
    }

    public static HostSearchHandler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HostSearchHandler(context);
        }

        return sInstance;
    }

    public void stopSearch(int reason) {
        cleanSeachingHandler();

        if (mListener != null && reason == SERVER_SEARCH_CANCELED) {
            mListener.onSearchCanceled(SERVER_SEARCH_CANCELED);
        }
    }

    boolean isSearching() {
        return mIsSearching;
    }

    private void cleanSeachingHandler() {
        mIsSearching = false;

        if (mThreadPool != null) {
            mThreadPool.stop();
        }

        mThreadPool = null;
        mListener = null;

        // release self if possible.
        resetSearchIpMask();
        sInstance = null;
    }

    private void resetSearchIpMask() {
        for (int i = 0; i < 256; i++) {
            mSearchIpMask[i] = 0;
        }
    }

    private boolean hasSearchingMask() {
        for (int i = 0; i < 256; i++) {
            if (mSearchIpMask[i] == IP_MARK_CONNECTING || mSearchIpMask[i] == IP_MARK_IDLE) {
                return true;
            }
        }

        return false;
    }

    private void setIpMask(String ip, int mark) {
        byte[] ipBytes = IPv4Utils.ipToBytesByInet(ip);

        if (ipBytes.length == 4) {
            int index = Utils.byteToInt(ipBytes[3]);
            Log.d(TAG, "setIpMask, ip:" + ip + ", index:" + index);

            mSearchIpMask[index] = mark;
        }
    }

    private void setByteIpMask(int index, int mark) {
        mSearchIpMask[index] = mark;
    }

    /*
     * search XXX.XXX.XXX.* ip addresses which idAddress in.
     */
    public void searchServer(String ipSegment, int port, ServerSearchListener listener) {
        // no listener set, invalid server search action
        if (listener == null) {
            Log.d(TAG,
                    "searchServer:listener is null. Invalid server search request!");
            return;
        }

        mPort = port;
        mListener = listener;

        // IPv4: 192.168.1.0
        NetworkInfoManager networkManager = NetworkInfoManager.getInstance(mContext);
        if (!networkManager.isWifiConnected()) {
            mListener.onSearchCanceled(
                    SERVER_SEARCH_FAILED_WIFI_NOT_CONNECTED);
            return;
        }

        if (isSearching()) {
            Log.d(TAG, "searchServer, searching...");
            return;
        }

        mIsSearching = true;
        resetSearchIpMask();
        if (mThreadPool == null) {
            mThreadPool = new ThreadPool(poolSize);
        }

        if (TextUtils.isEmpty(ipSegment)) {
            ipSegment = networkManager.getWifiIpAddress().getHostAddress();
        }

        // Get wifi ip address, this ip should ignored when searching server.
        byte[] ip = IPv4Utils.ipToBytesByInet(ipSegment);
        Log.d(TAG, "Wifi Address:" + IPv4Utils.bytesToIp(ip));

        byte selfIpByte = ip[3];
        // mark self ip failed state to avoid search later
        int index = Utils.byteToInt(selfIpByte);
        mSearchIpMask[index] = IP_MARK_CONNECT_FAILED;

        // get dhcp server ip and mark as failed to avoid search
        /*byte[] dhcpServerIp = networkManager.getWifiDhcpServerAddress().getAddress();
        Log.d(TAG, "Wifi dhcp server Address:"
                + IPv4Utils.bytesToIp(dhcpServerIp));

        index = Utils.byteToInt(dhcpServerIp[3]);
        mSearchIpMask[index] = IP_MARK_CONNECT_FAILED;*/

        // reset last byte to prepare all other ip byte.
        ip[3] = 0;

        // defined server ip. only test purpose
        /*String serverIp = mContext.getResources().getString(R.string.server_ip);

        if (!TextUtils.isEmpty(serverIp)) {
            Log.d(TAG, "--->searchServer, serverIp:" + serverIp + "\n");
            byte[] server = IPv4Utils.ipToBytesByInet(serverIp);
            if (server.length == 4) {
                // check to avoid equal self ip or dhcp servier
                if (server[3] != selfIpByte && server[3] != dhcpServerIp[3]) {
                    createConnectingTask(server, port);
                }
            }
        }*/

        int start = 0;
        int end = 0;

        // first we try to connect ip near wifi ip address, before 10 and after 10
        start = Utils.byteToInt(selfIpByte) - 10;
        if (start < 0) {
            start = 0;
        }

        end = Utils.byteToInt(selfIpByte) + 10;
        if (end > 255) {
            end = 255;
        }

        Log.d(TAG, "searchServer, start:" + start + ", end:" + end);

        for (int i = start; i < end; i++) {
            ip[3] = (byte) i;

            createConnectingTask(ip, port);
        }

        for (int i = 0; i < 256; i++) {
            ip[3] = (byte) i;

            createConnectingTask(ip, port);
        }
    }

    private void createConnectingTask(final byte ip[], final int port) {
        if (ip.length != 4) {
            Log.e(TAG, "createConnectingTask, ip addrss error:" + Utils.byteToInt(ip[3]));
            return;
        }

        int index = Utils.byteToInt(ip[3]);

        if (mSearchIpMask[index] != IP_MARK_IDLE) {
            Log.d(TAG, "createConnectingTask, ip not idle:" + Utils.byteToInt(ip[3]));
            return;
        }

        setByteIpMask(index, IP_MARK_CONNECTING);

        Log.d(TAG, "createConnectingTask, create connecting task for ip:" + IPv4Utils.bytesToIp(ip));

        mThreadPool.addTask(new Runnable() {
            @Override
            public void run() {
                Connection connection =
                        ConnectionFactory.createConnectionSync(IPv4Utils.bytesToIp(ip), port);

                if (connection != null) {
                        String ip = connection.getIp();

                        String CONNECTION_TAG = TAG + "[" + ip + "]";

                        int state = connection.getState();
                        Log.d(CONNECTION_TAG, "onConnectingUpdate, ip:" + ip
                                + ",state:" + state);

                        if (Connection.CONNECTION_STATE_CONNECTING == state) {
                            setIpMask(ip, IP_MARK_CONNECTING);
                        } else if (Connection.CONNECTION_STATE_CONNECTED == state) {
                            setIpMask(ip, IP_MARK_CONNECTED);

                            if (mListener != null) {
                                mListener.onConnectionConnected(connection);
                            }
                        }
                } else {
                    setIpMask(IPv4Utils.bytesToIp(ip), IP_MARK_CONNECT_FAILED);
                }

                // search end. notify failed and do necessary clean
                if (!hasSearchingMask()) {
                    if (mListener != null) {
                        mListener.onSearchCompleted();
                    }

                    cleanSeachingHandler();
                }
            }
        });
    }

    public interface ServerSearchListener {
        void onConnectionConnected(Connection connection);
        void onSearchCompleted();
        void onSearchCanceled(int reason);
    }
}