package com.assistant.ui;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.assistant.R;
import com.assistant.datastorage.SharePreferencesHelper;

public class SettingActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {
    private static final String CLINET_NAME_KEY = "clientName";

    private Toolbar mActionBar;

    private EditTextPreference mClientNamePrefence;

    private SharePreferencesHelper mSharePreferencesHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        mActionBar.setTitle(R.string.settings_title);

        mClientNamePrefence = (EditTextPreference)findPreference(CLINET_NAME_KEY);
        mClientNamePrefence.setOnPreferenceChangeListener(this);

        mSharePreferencesHelper = new SharePreferencesHelper(this);
    }

    @Override
    protected void onResume() {
        updatePreferences();

        super.onResume();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (CLINET_NAME_KEY.equals(key)) {
            mClientNamePrefence.setSummary((String) newValue);
            mSharePreferencesHelper.save(SharePreferencesHelper.SP_KEY_CLIENT_NAME,
                    (String) newValue);
        }

        return true;
    }

    private void updatePreferences() {
        String clientName = mSharePreferencesHelper.getString(SharePreferencesHelper.SP_KEY_CLIENT_NAME);
        mClientNamePrefence.setSummary(clientName);
        mClientNamePrefence.setText(clientName);
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.settings_layout, new LinearLayout(this), false);

        mActionBar = (Toolbar) contentView.findViewById(R.id.action_bar);
        mActionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ViewGroup contentWrapper = (ViewGroup) contentView.findViewById(R.id.content_wrapper);
        LayoutInflater.from(this).inflate(layoutResID, contentWrapper, true);

        getWindow().setContentView(contentView);
    }
}
