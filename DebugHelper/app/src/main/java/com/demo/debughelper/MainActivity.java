package com.demo.debughelper;

import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.demo.cpuinfo.CpuManager;
import com.demo.view.CpuRateView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    TextView mCpuName;
    TextView mCpuMaxFreq;
    TextView mCpuMinFreq;
    TextView mCpuCurFreq;

    CpuRateView mCpuRateView;

    private static final int EVENT_UPDATE_CPU_INFO = 0;
    private static final int TIMESTAMP_UPDATE_CPU_FREQ = 1500; // 1.5s

    private boolean mIsDestroyed = false;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_CPU_INFO:
                    if (!mIsDestroyed) {
                        updateCpuFreq();
                        sendEmptyMessageDelayed(EVENT_UPDATE_CPU_INFO, TIMESTAMP_UPDATE_CPU_FREQ);
                    }
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCpuName = (TextView) findViewById(R.id.cpu_name);
        mCpuMaxFreq = (TextView)findViewById(R.id.cpu_max_freq);
        mCpuMinFreq = (TextView)findViewById(R.id.cpu_min_freq);
        mCpuCurFreq = (TextView)findViewById(R.id.cpu_cur_freq);

        mCpuRateView = (CpuRateView)findViewById(R.id.cpu_rate_view);

        mCpuCurFreq.setTextColor(Color.GREEN);

        updateCpuInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHandler.removeMessages(EVENT_UPDATE_CPU_INFO);
        mHandler.sendEmptyMessage(EVENT_UPDATE_CPU_INFO);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mIsDestroyed = true;
    }

    void updateCpuInfo() {
        mCpuName.setText(CpuManager.getCpuName());

        mCpuRateView.setCpuCount(CpuManager.getCpuCoreNum());

        updateCpuFreq();
    }

    void updateCpuFreq() {
        String maxCpuFreq = CpuManager.getMaxCpuFreq(0);
        String minCpuFreq = CpuManager.getMinCpuFreq(0);


        long maxCpuF = Long.decode(maxCpuFreq);


        mCpuMaxFreq.setText(maxCpuFreq);
        mCpuMinFreq.setText(minCpuFreq);

        int count = CpuManager.getCpuCoreNum();

        if (count <= 0) {
            count = 1;
        }

        String cpuCurFreq = "";
        for(int index = 0; index < count; index++) {
            String curCpuFreq = CpuManager.getCurCpuFreq(index);
            long curCpuF = 0;
            try {
                curCpuF = Long.decode(curCpuFreq);
            } catch (java.lang.NumberFormatException e) {
                Log.d(TAG, "cpu rate value is not valid:" + curCpuFreq);
                curCpuF = 0;
            }

            int cpu_rate = (int) ((curCpuF * 100) / maxCpuF);

            mCpuRateView.addCpuRate(index, cpu_rate);

            cpuCurFreq += "CPU" + index + ": " + cpu_rate + "% - " + curCpuFreq + "\n";
        }

        mCpuCurFreq.setText(cpuCurFreq);
    }
}
