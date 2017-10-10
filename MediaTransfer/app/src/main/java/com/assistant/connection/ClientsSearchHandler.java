package com.assistant.connection;

import android.content.Context;
import android.text.TextUtils;

import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.mediatransfer.NetworkInfoManager;
import com.assistant.utils.Log;

import com.assistant.utils.ThreadPool;
import com.assistant.utils.IPv4Utils;
import com.assistant.utils.Utils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/*
 * Handler server search.
 */
public class ClientsSearchHandler {
    private static final String TAG = "ClientsSearchHandler";

    public static final int SERVER_SEARCH_CANCELED = 2;

    public static final int IP_MARK_CONNECT_FAILED = 9;
    public static final int IP_MARK_IDLE = 0;
    public static final int IP_MARK_CONNECTED = 1;
    public static final int IP_MARK_CONNECTING = 2;

    private static final int POOL_SIZE = 10;

    private static ClientsSearchHandler sInstance;

    private Context mContext;
    private String mSelfWifiIp;
    private boolean mCanceled = false;

    private ThreadPool mThreadPool;

    private List<String> mIgnoredIpList = Collections.synchronizedList(new ArrayList<String>());

    private List<IpSegmentScanner> mScannerList =
            Collections.synchronizedList(new ArrayList<IpSegmentScanner>());

    private List<ServerSearchListener> mListeners =
            Collections.synchronizedList(new ArrayList<ServerSearchListener>());

    private ClientsSearchHandler(Context context) {
        mContext = context;
    }

    public static ClientsSearchHandler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ClientsSearchHandler(context);
        }

        return sInstance;
    }

    public void addListener(ServerSearchListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ServerSearchListener listener) {
        mListeners.remove(listener);
    }

    /*
     * search XXX.XXX.XXX.* ip addresses which idAddress in.
     */
    public synchronized void searchClientInSegment(String ipSegment) {
        mCanceled = false;
        if (!Utils.isIpPattern(ipSegment)) {
            Log.d(this, "searchClientInSegment: ip is not valid:" + ipSegment);
            return;
        }

        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(mContext);
        String selfWifiIp = networkInfoManager.getWifiIpAddressString();

        if (TextUtils.isEmpty(mSelfWifiIp) || !mSelfWifiIp.equals(selfWifiIp)) {
            mIgnoredIpList.remove(mSelfWifiIp);
            mSelfWifiIp = selfWifiIp;

            mIgnoredIpList.add(mSelfWifiIp);
        }

        if (mThreadPool == null) {
            mThreadPool = new ThreadPool(POOL_SIZE);
        }

        IpSegmentScanner scanner = getScanner(ipSegment);

        if (scanner != null) {
            if (!scanner.isSearching()) {
                scanner.start();
            }
        } else {
            MediaTransferManager mediaTransferManager = MediaTransferManager.getInstance(mContext);
            scanner = new IpSegmentScanner(ipSegment, mediaTransferManager.getPort());
            mScannerList.add(scanner);

            scanner.start();
        }
    }

    public void addIgnoredIp(String ipAddress) {
        if (!mIgnoredIpList.contains(ipAddress)) {
            Log.d(this, "addIgnoredIp, ipAddress:" + ipAddress);
            mIgnoredIpList.add(ipAddress);
        }
    }

    public void stopSearch(int reason) {
        mCanceled = true;
        onSearchCanceled(reason);
    }

    public boolean isClientSearching() {
        if (mCanceled) {
            return false;
        }

        if (mScannerList.size() > 0) {
            for(IpSegmentScanner scanner : mScannerList) {
                if (scanner.isSearching()) {
                    return true;
                }
            }
        }

        return false;
    }

    private IpSegmentScanner getScanner(String ipSegment) {
        IpSegmentScanner scanner = null;
        if (mScannerList.size() > 0) {
            for(IpSegmentScanner ipSegmentScanner : mScannerList) {
                if (ipSegmentScanner.isInScannerCoverage(ipSegment)) {
                    scanner = ipSegmentScanner;
                    break;
                }
            }
        }

        return scanner;
    }

    private boolean isIpConnectAllowed(byte[] ip) {
        if (ip == null || ip.length != 4) {
            return false;
        }

        if (mIgnoredIpList.size() > 0) {
            for (String ipAddress : mIgnoredIpList) {
                byte[] byteIp = IPv4Utils.ipToBytesByReg(ipAddress);
                if (byteIp != null && byteIp.length == 4) {
                    if (byteIp[0] == ip[0]
                            && byteIp[1] == ip[1]
                            && byteIp[2] == ip[2]
                            && byteIp[3] == ip[3]) {
                        Log.e(this, "isIpConnectAllowed, ip not allowed:" + ipAddress);
                        return false;
                    }
                } else {
                    Log.e(this, "isIpConnectAllowed, ip converted error:" + ipAddress);
                }
            }
        }

        return true;
    }

    private synchronized void onScannerCompleted(IpSegmentScanner scanner) {
        mScannerList.remove(scanner);

        if (mScannerList.size() == 0) {
            for(ServerSearchListener listener : mListeners) {
                listener.onSearchCompleted();
            }

            clean();
        }
    }

    private synchronized void onSearchCanceled(int reason) {
        for(ServerSearchListener listener : mListeners) {
            listener.onSearchCanceled(reason);
        }

        clean();
    }

    private void clean() {
        if (mThreadPool != null) {
            mThreadPool.stop();
        }

        mThreadPool = null;

        mScannerList.clear();

        mListeners.clear();
    }

    public interface ServerSearchListener {
        void onSearchCompleted();
        void onSearchCanceled(int reason);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        try {
            writer.println("  ClientsSearchHandler:");

            writer.println("    self ip:" + mSelfWifiIp);
            writer.println("    mListeners size:" + mListeners.size());
            writer.println("    canceled:" + mCanceled);
            String ignoredIp = "";
            if (mIgnoredIpList.size() > 0) {
                for (String ip : mIgnoredIpList) {
                    ignoredIp += ip + " ";
                }
            } else {
                ignoredIp = "no ignored ip.";
            }

            writer.println("    Ignored ip:" + ignoredIp);
            writer.println("    Scanner list:" + mScannerList.size());
            writer.flush();

            if (mScannerList.size() > 0) {
                for (IpSegmentScanner scanner : mScannerList) {
                    scanner.dump(fd, writer, args);
                    writer.println("");
                }
            }
            writer.println("");
        } catch (Exception e) {
            Log.d(this, "Exception happened when dump:" + e.getMessage());
        }
    }

    private class IpSegmentScanner {
        private String mIpSegment;
        private int mPort;
        private int[] mIpSearchStateMask = new int[256];

        private boolean mIsSearching = false;

        private ConnectionCreationListener mConnectionListener = new ConnectionCreationListener();

        public IpSegmentScanner(String ip, int port) {
            mIpSegment = ip;
            mPort = port;
        }

        public void start() {
            Log.d(this, "start, mIpSegment:" + mIpSegment + ", mPort:" + mPort);
            if (mIsSearching) {
                Log.d(this, "start, ip segment already in searching...");
                return;
            }

            clean();

            byte[] ip;
            if (isInScannerCoverage(mSelfWifiIp)) {
                Log.d(this, "start, search self ip segment.");
                ip = IPv4Utils.ipToBytesByInet(mSelfWifiIp);

                if (ip == null) {
                    onScanCompleted();
                    return;
                }

                mIsSearching = true;

                byte selfIpByte = ip[3];
                // mark self ip failed state to avoid search later
                int index = Utils.byteToInt(selfIpByte);

                if (Utils.DEBUG_CONNECTION) {
                    createConnectingTask(ip, mPort, false);
                } else {
                    setIpMask(index, IP_MARK_CONNECT_FAILED);
                }

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

                Log.d(TAG, "start, scan high priority ip, start:" + start + ", end:" + end);

                // try to connect nearby ip first
                for (int i = start; i < end; i++) {
                    ip[3] = (byte) i;

                    if (isIpConnectAllowed(ip)) {
                        createConnectingTask(ip, mPort, Utils.DEBUG_CONNECTION);
                    }
                }
            } else {
                ip = IPv4Utils.ipToBytesByInet(mIpSegment);

                if (ip == null) {
                    onScanCompleted();
                    return;
                }

                mIsSearching = true;
            }

            for (int i = 0; i < 256; i++) {
                ip[3] = (byte) i;

                if (isIpConnectAllowed(ip)) {
                    createConnectingTask(ip, mPort, Utils.DEBUG_CONNECTION);
                }
            }

            // search end. notify failed and do necessary clean
            if (!hasSearchingMask()) {
                onScanCompleted();
            }
        }

        public void clean() {
            mIsSearching = false;
            resetSearchIpMask();
        }

        boolean isSearching() {
            return mIsSearching;
        }

        private boolean isInScannerCoverage(String ipAddress) {
            try {
                return mIpSegment.substring(0, mIpSegment.lastIndexOf(".")).equals(
                        ipAddress.substring(0, ipAddress.lastIndexOf(".")));
            } catch (Exception e) {
                Log.e(this, "isInScannerCoverage, ip parser error." +
                        " mIpSegment:" + mIpSegment + ",ipAddress:" + ipAddress);
                return false;
            }
        }

        private boolean hasSearchingMask() {
            for (int i = 0; i < 256; i++) {
                if (mIpSearchStateMask[i] == IP_MARK_CONNECTING
                        || mIpSearchStateMask[i] == IP_MARK_IDLE) {
                    return true;
                }
            }

            return false;
        }

        private synchronized void setIpMask(int index, int mark) {
            mIpSearchStateMask[index] = mark;
        }

        private synchronized  void setIpMask(String ip, int mark) {
            byte[] ipBytes = IPv4Utils.ipToBytesByInet(ip);

            if (ipBytes!= null && ipBytes.length == 4) {
                int index = Utils.byteToInt(ipBytes[3]);
                Log.d(TAG, "setIpMask, ip:" + ip + ", index:" + index + ", mark:" + mark);

                mIpSearchStateMask[index] = mark;
            } else {
                try {
                    int index = Integer.parseInt(ip.substring(ip.lastIndexOf(".")));

                    Log.d(TAG, "setIpMask, ip:" + ip + ", index:" + index + ", mark:" + mark);

                    mIpSearchStateMask[index] = mark;
                } catch (Exception e) {
                    Log.e(this, "setIpMask, ip parser error:" + ip);
                }
            }
        }

        private void resetSearchIpMask() {
            for (int i = 0; i < 256; i++) {
                mIpSearchStateMask[i] = IP_MARK_IDLE;
            }
        }

        private synchronized boolean isIpIdle(int index) {
            return mIpSearchStateMask[index] == IP_MARK_IDLE;
        }

        private void createConnectingTask(final byte ip[], final int port, final boolean isFake) {
            if (ip.length != 4) {
                Log.e(TAG, "createConnectingTask, ip addrss error:" + Utils.byteToInt(ip[3]));
                return;
            }

            int index = Utils.byteToInt(ip[3]);
            String ipAddress = IPv4Utils.bytesToIp(ip);

            if (ConnectionManager.getInstance(mContext).isIpConnected(ipAddress)) {
                Log.d(TAG, "ip:" + ipAddress + " already connected!");
                setIpMask(index, IP_MARK_CONNECTED);
                return;
            } else if (ConnectionManager.getInstance(mContext).hasPendingConnectRequest(ipAddress)) {
                Log.d(TAG, "ip:" + ipAddress + " has reconnecting request!");
                setIpMask(index, IP_MARK_CONNECT_FAILED);
                return;
            }

            if (!isIpIdle(index)) {
                Log.d(TAG, "createConnectingTask, ip not idle:" + Utils.byteToInt(ip[3]));
                return;
            }

            if (isFake) {
                Log.d(TAG, "createConnectingTask, debug mode, IP:" + ipAddress + " ignored!" );
                setIpMask(index, IP_MARK_CONNECT_FAILED);

                return;
            }

            setIpMask(index, IP_MARK_CONNECTING);

            Log.d(TAG, "createConnectingTask, create connecting task for ip:" + ipAddress);

            mThreadPool.addTask(new ConnectionCreateTask(ipAddress,port));
        }

        private void onScanCompleted() {
            mIsSearching = false;
            clean();

            onScannerCompleted(this);
        }

        class ConnectionCreationListener implements Connection.ConnectionListener {
            @Override
            public void onConnected(Connection connection) {
                Log.d(TAG, "IP:" + connection.getIp() + " connected!");
                connection.removeListener(this);
                handleConnectionCreationResult(connection, true);
            }

            @Override
            public void onClosed(Connection connection, int reasonCode) {
                Log.d(TAG, "IP:" + connection.getIp() + " connection closed!");
                connection.removeListener(this);
                handleConnectionCreationResult(connection, false);
            }
        }

        private synchronized void handleConnectionCreationResult(Connection connection,
                                                                 boolean success) {
            if (success) {
                ConnectionManager.getInstance(mContext).addConnection(connection);
                // add connection
            } else {
                Log.d(TAG, "handleConnectionCreationResult,  search failed or canceled for ip:"
                        + connection.getIp());
                connection.close();
            }
            onConnectCompleted(connection.getIp(), success);
        }

        private synchronized void onConnectCompleted(String ipAddress, boolean success) {
            Log.d(TAG, "onConnectCompleted,  ip:" + ipAddress + ", result:" + success);

            if (success && !mCanceled) {
                setIpMask(ipAddress, IP_MARK_CONNECTED);
                // add connection
            } else {
                setIpMask(ipAddress, IP_MARK_CONNECT_FAILED);
            }

            // search end. notify failed and do necessary clean
            if (!hasSearchingMask()) {
                Log.d(TAG, "onConnectCompleted,  search ended!");
                onScanCompleted();
            }
        }

        class ConnectionCreateTask implements Runnable {
            private String ip;
            private int port;

            ConnectionCreateTask(String ipAddress, int iPort) {
                ip = ipAddress;
                port = iPort;
            }

            @Override
            public void run() {
                Log.d(TAG, "ConnectionCreateTask, ip:" + ip + ", mIsSearching:" + mIsSearching);
                if (mCanceled) {
                    Log.d(TAG, "ConnectionCreateTask, canceled for ip:" + ip);
                    return;
                }

                try {
                    ConnectionFactory.createConnection(ip, port, mConnectionListener);
                } catch (Exception e) {
                    Log.e(TAG, "ConnectionCreateTask exception happened for ip:" + ip);
                    onConnectCompleted(ip, false);
                }
                Log.d(TAG, "ConnectionCreateTask, ip:" + ip + ", createConnection completed!");
            }
        }

        public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            try {
                writer.println("  IpSegmentScanner:");

                writer.println("    mIpSegment:" + mIpSegment);
                writer.println("    mPort:" + mPort);
                writer.println("    mIsSearching:" + mIsSearching);

                writer.println("    Ip mark:");
                String maskArray = "";
                int index = 0;
                for (int mask : mIpSearchStateMask) {
                    maskArray += mask + " ";
                    index++;

                    if (index != 1 && index%32 == 0) {
                        maskArray += "\n";
                    }
                }

                writer.println(maskArray);

                writer.flush();
            } catch (Exception e) {
                Log.d(this, "Exception happened when dump:" + e.getMessage());
            }
        }
    }
}
