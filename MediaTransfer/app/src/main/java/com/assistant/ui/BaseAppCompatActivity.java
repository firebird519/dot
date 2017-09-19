package com.assistant.ui;

import android.support.v7.app.AppCompatActivity;

import com.assistant.MediaTransferApplication;

public class BaseAppCompatActivity extends AppCompatActivity {
    @Override
    protected void onResume() {
        MediaTransferApplication.getInstance().onActivityResumed(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        MediaTransferApplication.getInstance().onActivityPaused(this);
        super.onPause();
    }
}
