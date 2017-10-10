package com.assistant.utils;

import java.net.InetAddress;

/**
 * @author michael <br>
 *         blog: http://sjsky.iteye.com <br>
 *         mail: sjsky007@gmail.com
 */
public class IPv4Utils {
    private final static String TAG = "IPv4Utils";

    private final static int INADDRSZ = 4;

    /**
     * ��IP��ַת��Ϊ�ֽ�����
     *
     * @param ipAddr
     * @return byte[]
     */
    public static byte[] ipToBytesByInet(String ipAddr) {
        try {
            return InetAddress.getByName(ipAddr).getAddress();
        } catch (Exception e) {
            Log.d(TAG, "ipToBytesByInet, ip parser error:" + ipAddr);
        }

        return null;
    }

    /**
     * ��IP��ַת��Ϊint
     *
     * @param ipAddr
     * @return int
     */
    public static byte[] ipToBytesByReg(String ipAddr) {
        byte[] ret = new byte[4];
        try {
            String[] ipArr = ipAddr.split("\\.");
            ret[0] = (byte) (Integer.parseInt(ipArr[0]) & 0xFF);
            ret[1] = (byte) (Integer.parseInt(ipArr[1]) & 0xFF);
            ret[2] = (byte) (Integer.parseInt(ipArr[2]) & 0xFF);
            ret[3] = (byte) (Integer.parseInt(ipArr[3]) & 0xFF);
            return ret;
        } catch (Exception e) {
            Log.d(TAG, "ipToBytesByInet, ip parser error:" + ipAddr);
        }

        return null;
    }

    /**
     * �ֽ�����ת��ΪIP
     *
     * @param bytes
     * @return int
     */
    public static String bytesToIp(byte[] bytes) {
        return new StringBuffer().append(bytes[0] & 0xFF).append('.').append(
                bytes[1] & 0xFF).append('.').append(bytes[2] & 0xFF)
                .append('.').append(bytes[3] & 0xFF).toString();
    }

    /**
     * ����λ����� byte[] -> int
     *
     * @param bytes
     * @return int
     */
    public static int bytesToInt(byte[] bytes) {
        int addr = bytes[3] & 0xFF;
        addr |= ((bytes[2] << 8) & 0xFF00);
        addr |= ((bytes[1] << 16) & 0xFF0000);
        addr |= ((bytes[0] << 24) & 0xFF000000);
        return addr;
    }

    /**
     * ��IP��ַת��Ϊint
     *
     * @param ipAddr
     * @return int
     */
    public static int ipToInt(String ipAddr) {
        try {
            return bytesToInt(ipToBytesByInet(ipAddr));
        } catch (Exception e) {
            throw new IllegalArgumentException(ipAddr + " is invalid IP");
        }
    }

    /**
     * ipInt -> byte[]
     *
     * @param ipInt
     * @return byte[]
     */
    public static byte[] intToBytes(int ipInt) {
        byte[] ipAddr = new byte[INADDRSZ];
        ipAddr[3] = (byte) ((ipInt >>> 24) & 0xFF);
        ipAddr[2] = (byte) ((ipInt >>> 16) & 0xFF);
        ipAddr[1] = (byte) ((ipInt >>> 8) & 0xFF);
        ipAddr[0] = (byte) (ipInt & 0xFF);
        return ipAddr;
    }

    /**
     * ��int->ip��ַ
     *
     * @param ipInt
     * @return String
     */
    public static String intToIp(int ipInt) {
        return new StringBuilder().append(((ipInt >> 24) & 0xff)).append('.')
                .append((ipInt >> 16) & 0xff).append('.').append(
                        (ipInt >> 8) & 0xff).append('.').append((ipInt & 0xff))
                .toString();
    }

    /**
     * ��192.168.1.1/24 ת��Ϊint���鷶Χ
     *
     * @param ipAndMask
     * @return int[]
     */
    public static int[] getIPIntScope(String ipAndMask) {

        String[] ipArr = ipAndMask.split("/");
        if (ipArr.length != 2) {
            throw new IllegalArgumentException("invalid ipAndMask with: "
                    + ipAndMask);
        }
        int netMask = Integer.valueOf(ipArr[1].trim());
        if (netMask < 0 || netMask > 31) {
            throw new IllegalArgumentException("invalid ipAndMask with: "
                    + ipAndMask);
        }
        int ipInt = IPv4Utils.ipToInt(ipArr[0]);
        int netIP = ipInt & (0xFFFFFFFF << (32 - netMask));
        int hostScope = (0xFFFFFFFF >>> netMask);
        return new int[]{netIP, netIP + hostScope};

    }

    /**
     * ��192.168.1.1/24 ת��ΪIP���鷶Χ
     *
     * @param ipAndMask
     * @return String[]
     */
    public static String[] getIPAddrScope(String ipAndMask) {
        int[] ipIntArr = IPv4Utils.getIPIntScope(ipAndMask);
        return new String[]{IPv4Utils.intToIp(ipIntArr[0]),
                IPv4Utils.intToIp(ipIntArr[0])};
    }

    /**
     * ����IP �������루192.168.1.1 255.255.255.0��ת��ΪIP��
     *
     * @param ipAddr ipAddr
     * @param mask   mask
     * @return int[]
     */
    public static int[] getIPIntScope(String ipAddr, String mask) {

        int ipInt;
        int netMaskInt = 0, ipcount = 0;
        try {
            ipInt = IPv4Utils.ipToInt(ipAddr);
            if (null == mask || "".equals(mask)) {
                return new int[]{ipInt, ipInt};
            }
            netMaskInt = IPv4Utils.ipToInt(mask);
            ipcount = IPv4Utils.ipToInt("255.255.255.255") - netMaskInt;
            int netIP = ipInt & netMaskInt;
            int hostScope = netIP + ipcount;
            return new int[]{netIP, hostScope};
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid ip scope express  ip:"
                    + ipAddr + "  mask:" + mask);
        }

    }

    /**
     * ����IP �������루192.168.1.1 255.255.255.0��ת��ΪIP��
     *
     * @param ipAddr ipAddr
     * @param mask   mask
     * @return String[]
     */
    public static String[] getIPStrScope(String ipAddr, String mask) {
        int[] ipIntArr = IPv4Utils.getIPIntScope(ipAddr, mask);
        return new String[]{IPv4Utils.intToIp(ipIntArr[0]),
                IPv4Utils.intToIp(ipIntArr[0])};
    }
}
