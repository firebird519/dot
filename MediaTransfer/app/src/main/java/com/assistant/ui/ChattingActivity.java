package com.assistant.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.assistant.R;
import com.assistant.utils.Log;

/**
 * Created by liyong on 17-8-16.
 */

public class ChattingActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chatting);

        Log.d(this, "onCreate");
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
