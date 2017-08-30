package com.assistant.ui;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.assistant.connection.Connection;
import com.assistant.ui.fragment.AlertDialogFragment;
import com.assistant.ui.fragment.ClientListFragment;

import java.util.ArrayList;
import java.util.List;

import yalantis.com.sidemenu.model.SlideMenuItem;

import com.assistant.R;
import com.assistant.ui.fragment.IpAddressInputFragment;

import yalantis.com.sidemenu.util.SideMenu;

public class MainActivity extends AppCompatActivity implements AlertDialogFragment.AlertDialogCBInterface {
    private static final int DIALOG_ONOFF_ALERT = 0;
    private static final int DIALOG_IP_INOUT = 1;

    private Switch mOnOffSwitchBtn;
    /*
    private List<SlideMenuItem> mMenuItemList = new ArrayList<>();
    private SideMenu mSideMenu;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayout mLinearLayout;
    */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initSideMenu();

        initActionBar();

        showClientListFragment();
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
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_connectto) {
            showNetworkOffAlertDialog(DIALOG_IP_INOUT,
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

    private void showNetworkOffAlertDialog(int dialogId, int textId, int iconId, int layoutId) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(dialogId,
                textId,
                iconId, layoutId);
        newFragment.show(getFragmentManager(), "alertdialog");
    }

    @Override
    public void onDialogViewCreated(int dialogId, View view) {
        if (dialogId == DIALOG_IP_INOUT) {

        }
    }

    @Override
    public void onDialogPositiveBtnClick(int dialogId, View view) {
        // stop connection searching and listening
    }

    @Override
    public void onDialogNegativeBtnClick(int dialogId) {
        mOnOffSwitchBtn.setChecked(true);
    }

    private void initActionBar() {
        mOnOffSwitchBtn = (Switch) findViewById(R.id.toolbar_switch_onoff);
        mOnOffSwitchBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    showNetworkOffAlertDialog(DIALOG_ONOFF_ALERT,
                            R.string.media_transfer_off_dialog_title,
                            0,
                            0);
                } else {
                    // open connection listening and host search
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
                    }
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

}
