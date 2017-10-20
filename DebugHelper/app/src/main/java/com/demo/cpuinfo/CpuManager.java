package com.demo.cpuinfo;

import com.demo.utils.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;


/*
 * got form http://blog.csdn.net/chuxing/article/details/7571547
 */
public class CpuManager {

    private static final String TAG = "CpuManager";

    // 获取CPU最大频率（单位KHZ）

    // "/system/bin/cat" 命令行

    // "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq" 存储最大频率的文件的路径
    private static final String CPU_INFO_PATH_PRE = "/sys/devices/system/cpu/cpu";
    private static final String CPU_FREQ_PATH = "cpufreq";
    private static final String CPU_MAX_FREQ_FILE = "cpuinfo_max_freq";
    private static final String CPU_MIN_FREQ_FILE = "cpuinfo_min_freq";
    private static final String CPU_CUR_FREQ_FILE = "scaling_cur_freq";

    public static String getMaxCpuFreq(int index) {
        String result = "";
        ProcessBuilder cmd;
        String cpuFreqPath = CPU_INFO_PATH_PRE + index + "/" + CPU_FREQ_PATH + "/" + CPU_MAX_FREQ_FILE;

        Log.d(TAG, "MAX freq path:" + cpuFreqPath);
        try {
            String[] args = {"/system/bin/cat",
                    cpuFreqPath};
            cmd = new ProcessBuilder(args);
            Process process = cmd.start();
            InputStream in = process.getInputStream();
            byte[] re = new byte[24];
            while (in.read(re) != -1) {
                result = result + new String(re);
            }
            in.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            result = "N/A";
        }
        return result.trim();
    }

    // 获取CPU最小频率（单位KHZ）
    public static String getMinCpuFreq(int index) {
        String result = "";
        ProcessBuilder cmd;
        String cpuFreqPath = CPU_INFO_PATH_PRE + index + "/" + CPU_FREQ_PATH + "/" + CPU_MIN_FREQ_FILE;

        Log.d(TAG, "MIN freq path:" + cpuFreqPath);
        try {
            String[] args = {"/system/bin/cat",
                    cpuFreqPath};
            cmd = new ProcessBuilder(args);
            Process process = cmd.start();
            InputStream in = process.getInputStream();
            byte[] re = new byte[24];
            while (in.read(re) != -1) {
                result = result + new String(re);
            }
            in.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            result = "N/A";
        }
        return result.trim();
    }

    // 实时获取CPU当前频率（单位KHZ）
    public static String getCurCpuFreq(int index) {
        String result = "N/A";
        String cpuFreqPath = CPU_INFO_PATH_PRE + index + "/" + CPU_FREQ_PATH + "/" + CPU_CUR_FREQ_FILE;

        Log.d(TAG, "MIN freq path:" + cpuFreqPath);

        try {
            FileReader fr = new FileReader(
                    cpuFreqPath);
            BufferedReader br = new BufferedReader(fr);
            String text = br.readLine();
            result = text.trim();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException:" + cpuFreqPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    // 获取CPU名字
    public static String getCpuName() {
        try {
            FileReader fr = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fr);
            String text = br.readLine();
            String[] array = text.split(":\\s+", 2);
            for (int i = 0; i < array.length; i++) {
            }
            return array[1];
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getCpuCoreNum() {
        int count = 0;
        try {
            FileReader fr = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fr);

            int maxLine = 1000;
            for(;;) {
                String line = br.readLine();

                if (line == null) {
                    break;
                }

                Log.d(TAG, line);

                if (line.contains("processor\t:")) {
                    count ++;
                }

                maxLine --;

                if (maxLine == 0) {
                    Log.d(TAG, "max line check ended...");
                    break;
                }
            }

            return count;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }
}