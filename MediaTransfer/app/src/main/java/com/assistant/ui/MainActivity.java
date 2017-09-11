package com.assistant.ui;

import android.Manifest;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.assistant.MediaTransferApplication;
import com.assistant.MediaTransferService;
import com.assistant.connection.ConnectionCreationCallback;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.mediatransfer.NetworkInfoManager;
import com.assistant.ui.fragment.AlertDialogFragment;
import com.assistant.ui.fragment.ClientListFragment;


import com.assistant.R;
import com.assistant.ui.permissiongrant.PermissionHelper;
import com.assistant.ui.permissiongrant.PermissionsActivity;

import com.assistant.utils.Log;
import com.assistant.utils.Utils;

public class MainActivity extends AppCompatActivity implements AlertDialogFragment.AlertDialogClickListener {
    private static final int DIALOG_ONOFF_ALERT = 0;
    private static final int DIALOG_IP_INOUT = 1;

    // permission variable
    private PermissionHelper mPermissionHelper;
    private static final int PERMISSION_REQUEST_CODE = 0;

    private Switch mOnOffSwitchBtn;

    private ProgressDialog mProgressDialog;

    private MediaTransferManager mMediaTransferManager;

    private SharePreferencesHelper mSharePreferencesHelper;

    /*
    private List<SlideMenuItem> mMenuItemList = new ArrayList<>();
    private SideMenu mSideMenu;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayout mLinearLayout;
    */

    private static final int EVENT_IP_CONNECT_RESULT = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_IP_CONNECT_RESULT:
                    // TODO: need consider more cases. like reconnecting, existed etc.
                    int strId = (msg.arg1 == 1) ? R.string.ip_connect_success : R.string.ip_connect_failed;
                    stopProgressBar();
                    showToastMessage(getApplicationContext().getString(strId, (String)msg.obj));
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(this, "onCreate start");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initSideMenu();

        mSharePreferencesHelper = SharePreferencesHelper.getInstance(getApplicationContext());
        mMediaTransferManager = MediaTransferManager.getInstance(getApplicationContext());

        initActionBar();

        showClientListFragment();
        Log.d(this, "onCreate end");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void showClientListFragment() {
        ClientListFragment fragment = new ClientListFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
        // update selected item and title, then close the drawer
    }

    @Override
    protected void onResume() {
        Log.d(this, "onResume start");
        super.onResume();

        // permission grant
        if (mPermissionHelper == null) {
            mPermissionHelper = new PermissionHelper(getApplicationContext());
        }

        // permission grant
        if (!mPermissionHelper.isAllPermissionGranted()) {
            mPermissionHelper.startPermissionsActivity(this, PERMISSION_REQUEST_CODE);
        }

        Log.d(this, "onResume end");
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopProgressBar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_connectto) {
            showAlertDialog(DIALOG_IP_INOUT,
                    0,
                    0,
                    R.layout.ip_input_dialog_layout);
        } else if (itemId == R.id.action_settings) {
            showSettingActivity();
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSettingActivity() {
        Intent intent = new Intent();
        intent.setClass(this, SettingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
    }

    private void showAlertDialog(int dialogId, int textId, int iconId, int layoutId) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(dialogId,
                textId,
                iconId, layoutId);
        newFragment.show(getFragmentManager(), "alertdialog");
    }

    @Override
    public void onDialogViewCreated(int dialogId, View view) {
        switch (dialogId) {
            case DIALOG_ONOFF_ALERT:
                break;
            case DIALOG_IP_INOUT:
                handleIpInputViewCreated(view);
                break;
            default:
                break;
        }
    }

    private void handleIpInputViewCreated(View view) {
        if (view == null) {
            Log.d(this, "handleIpInputViewCreated, view is null.");
            return;
        }
        EditText portEditText = (EditText)view.findViewById(R.id.port_input_edittext);
        portEditText.setText(String.valueOf(mMediaTransferManager.getPort()));

        EditText ipEditText = (EditText)view.findViewById(R.id.ip_address_input_edittext);
        NetworkInfoManager networkInfoManager = NetworkInfoManager.getInstance(this);
        String ip = networkInfoManager.getWifiIpAddressString();
        ipEditText.setText(ip);
        ipEditText.setFilters(new InputFilter[] {
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                                               Spanned dest, int dstart, int dend) {
                        String result = String.valueOf(dest.subSequence(0, dstart))
                                + source.subSequence(start, end)
                                + dest.subSequence(dend, dest.length());

                        if (!Utils.isIpPattern(result)) {
                            Log.d(this, "input string not match ip pattern, result:" + result +
                                    ", source:" + source +
                                    ", start:" + start +
                                    ", end:" + end +
                                    ", dest:" + dest.toString() +
                                    ", dstart:" + dstart +
                                    ", dend:" + dend);

                            return "";
                        }

                        return null;
                    }
                }

        });
    }

    class ConnectionCreationListener extends ConnectionCreationCallback {
        public ConnectionCreationListener(String ipAddress) {
            ip = ipAddress;
        }

        @Override
        public void onResult(boolean ret, int reason) {
            Log.d(this, "onResult:" + ret);

            Message msg = mHandler.obtainMessage(EVENT_IP_CONNECT_RESULT, ret ? 1 : 0, reason);
            msg.obj = ip;

            msg.sendToTarget();
        }
    }

    private void handleIpInput(View view) {

        EditText ipEditText = (EditText)view.findViewById(R.id.ip_address_input_edittext);
        EditText portEditText = (EditText)view.findViewById(R.id.port_input_edittext);

        if (ipEditText != null && portEditText != null) {
            showProgressBar();

            String ip = ipEditText.getText().toString();
            String port = portEditText.getText().toString();
            Log.d(this, "handleIpInput, ip:" + ip);

            if (!TextUtils.isEmpty(ip) && !TextUtils.isEmpty(port)) {
                mMediaTransferManager.connectTo(ip, Integer.valueOf(port),new ConnectionCreationListener(ip));
            } else {
                showToastMessage(R.string.ip_input_value_error);
            }
        } else {
            showToastMessage(R.string.ip_input_value_error);
        }
    }

    private void showProgressBar() {
        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage(getResources().getString(R.string.connecting));
        mProgressDialog.show();
    }

    private void stopProgressBar() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void showToastMessage(int msgId) {
        Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show();
    }

    private void showToastMessage(String msg) {
        Log.d(this, "msg:" + msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPositiveBtnClicked(int dialogId, View view) {
        switch (dialogId) {
            case DIALOG_ONOFF_ALERT:
                mSharePreferencesHelper.save(SharePreferencesHelper.SP_KEY_NETWORK_ON, 0);
                mMediaTransferManager.stopAllConnections();
                break;
            case DIALOG_IP_INOUT:
                handleIpInput(view);
                break;
            default:
                break;
        }
    }

    @Override
    public void onNegativeBtnClicked(int dialogId) {
        switch (dialogId) {
            case DIALOG_ONOFF_ALERT:
                mOnOffSwitchBtn.setChecked(true);
                break;
            case DIALOG_IP_INOUT:
                break;
            default:
                break;
        }
    }

    private void initActionBar() {
        mOnOffSwitchBtn = (Switch) findViewById(R.id.toolbar_switch_onoff);

        if (mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_NETWORK_ON, 1) == 1) {
            mOnOffSwitchBtn.setChecked(true);
        } else {
            mOnOffSwitchBtn.setChecked(false);
        }

        mOnOffSwitchBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    showAlertDialog(DIALOG_ONOFF_ALERT,
                            R.string.media_transfer_off_dialog_title,
                            0,
                            0);
                } else {
                    // open connection listening and host search
                    mSharePreferencesHelper.save(SharePreferencesHelper.SP_KEY_NETWORK_ON, 1);

                    // do network listen and searching in service.
                    MediaTransferService.startService(getApplicationContext(), true);
                }
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //getSupportActionBar().setHomeButtonEnabled(true);
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        mDrawerToggle = new ActionBarDrawerToggle(
//                this,                  /* host Activity */
//                mDrawerLayout,         /* DrawerLayout object */
//                toolbar,  /* nav drawer icon to replace 'Up' caret */
//                R.string.drawer_open,  /* "open drawer" description */
//                R.string.drawer_close  /* "close drawer" description */
//        ) {
//            /** Called when a drawer has settled in a completely closed state. */
//            public void onDrawerClosed(View view) {
//                super.onDrawerClosed(view);
//
//                mSideMenu.close();
//
//                Log.d(this, "onDrawerClosed...");
//            }
//
//            @Override
//            public void onDrawerSlide(View drawerView, float slideOffset) {
//                Log.d(this, "onDrawerSlide...");
//
//                super.onDrawerSlide(drawerView, slideOffset);
//                if (slideOffset > 0.6) {
//                    Log.d(this, "onDrawerSlide, start animation!");
//                    mSideMenu.show();
//                }
//            }
//
//            /** Called when a drawer has settled in a completely open state. */
//            public void onDrawerOpened(View drawerView) {
//                super.onDrawerOpened(drawerView);
//
//                Log.d(this, "onDrawerOpened...");
//            }
//        };
//        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

/*    private void initSideMenu() {
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mLinearLayout = (LinearLayout) findViewById(R.id.left_drawer);
        mLinearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDrawerLayout.closeDrawers();
            }
        });

        createMenuList();

        mSideMenu = new SideMenu<>(this,
                mMenuItemList,
                mDrawerLayout,
                mLinearLayout,
                new SideMenu.ViewAnimatorListener() {
                    @Override
                    public void onItemSelected(Resourceble slideMenuItem, int position) {
                        Log.d(this, "item:" + position + ":" + slideMenuItem.getName() + ", selected!");
                    }

                    @Override
                    public void disableHomeButton() {
                        getSupportActionBar().setHomeButtonEnabled(false);
                    }

                    @Override
                    public void enableHomeButton() {
                        getSupportActionBar().setHomeButtonEnabled(true);
                        mDrawerLayout.closeDrawers();
                    }mPermissionHelper
                });
    }

    private void createMenuList() {
        SlideMenuItem menuItem0 = new SlideMenuItem("close", R.drawable.icn_close);
        mMenuItemList.add(menuItem0);
        SlideMenuItem menuItem = new SlideMenuItem("menu item 1", R.drawable.icn_1);
        mMenuItemList.add(menuItem);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }*/

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // finish application if no permission granted.
        if (requestCode == PERMISSION_REQUEST_CODE &&
                resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
            finish();
            return;
        }

        MediaTransferApplication.writeExternalStoragePermissionGranted(getApplicationContext());
    }
}
