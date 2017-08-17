package com.assistant.utils;

public class NetInfo {
    private static NetInfo sMe;
    public int mServerPort;
    public String mServerIp;
    public String mSearchStartIp;
    public String mSearchEndIp;
    public String mLocalIp;
    // status data
    public boolean mIsSearching;
    public int mSearchedIpCount;
    public boolean mForceSearchStop;
    public boolean mServerConnected;

    private NetInfo() {
        init();
    }

    public static NetInfo getInstance() {
        if (sMe == null) {
            sMe = new NetInfo();
        }

        return sMe;
    }

    public void init() {
        mServerPort = 0;
        mSearchStartIp = null;
        mSearchEndIp = null;
        mServerIp = null;
        mLocalIp = null;

        initStatusInfo();
    }

    public void initStatusInfo() {

        // status data
        mIsSearching = false;
        mSearchedIpCount = 0;
        mForceSearchStop = false;
        mServerConnected = false;
    }
}
