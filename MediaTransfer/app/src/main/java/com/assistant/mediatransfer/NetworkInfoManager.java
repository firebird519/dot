package com.assistant.mediatransfer;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import com.assistant.utils.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkInfoManager {
    private static final String TAG = "NetworkManager";

    private static NetworkInfoManager sInstance;
    WifiManager mWifiManager;
    ConnectivityManager mCM;
    private Context mContext;
    private NetInfo mNetInfo = NetInfo.getInstance();

    private NetworkInfoManager(Context context) {
        mContext = context;

        mCM = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        IntentFilter intentFilter =
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    public static NetworkInfoManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NetworkInfoManager(context);
        }

        return sInstance;
    }

    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    public boolean isWifiConnected() {
        if (!mWifiManager.isWifiEnabled()) {
            return false;
        }

        NetworkInfo wifiNetInfo = mCM.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiNetInfo.isConnected();
    }

    public InetAddress getWifiIpAddress() {
        InetAddress address = null;

        try {
            address = InetAddress.getByName(getWifiIpAddressString());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return address;
    }

    public String getWifiIpAddressString() {
        /*
		String hostIpAddress = null;
    	try {
    		hostIpAddress = InetAddress.getLocalHost().getHostAddress();
    		
    		Log.d(TAG,"NetworkManager.getHostIpAddress:" + hostIpAddress);
    		

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (RuntimeException re) {
			re.printStackTrace();
		}
		*/
        // should remove it after verified if it is what we want.
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        String ipAddress = Formatter.formatIpAddress(wifiInfo.getIpAddress());

        Log.d(TAG, "NetworkManager.getHostIpAddress ipAddress:" + ipAddress);

        return ipAddress;
    }

    public int getWifiIpAddressInt() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        return wifiInfo.getIpAddress();
    }

    public int getWifiDhcpServerAddressInt() {
        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();

        return dhcpInfo.serverAddress;
    }

    // only support IPv4 address
    @SuppressWarnings("deprecation")
    public String getWifiDhcpServerAddressString() {
        DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();

        return Formatter.formatIpAddress(dhcpInfo.serverAddress);
    }

    public InetAddress getWifiDhcpServerAddress() {
        InetAddress address = null;

        try {
            address = InetAddress.getByName(getWifiDhcpServerAddressString());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return address;
    }
}
