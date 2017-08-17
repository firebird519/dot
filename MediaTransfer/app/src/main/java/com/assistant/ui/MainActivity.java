package com.assistant.ui;

import android.app.FragmentManager;
import android.content.res.Configuration;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.LinearLayout;

import com.assistant.ui.fragment.ClientListFragment;
import com.assistant.utils.Log;

import java.util.ArrayList;
import java.util.List;

import yalantis.com.sidemenu.interfaces.Resourceble;
import yalantis.com.sidemenu.model.SlideMenuItem;

import mediatransfer.assistant.com.mediatransfer.R;
import yalantis.com.sidemenu.util.SideMenu;

public class MainActivity extends AppCompatActivity {

    private List<SlideMenuItem> mMenuItemList = new ArrayList<>();
    private SideMenu mSideMenu;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayout mLinearLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initSideMenu();

        setActionBar();

        showClientListFragment();
    }

    private void showClientListFragment() {
        ClientListFragment fragment = new ClientListFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
        // update selected item and title, then close the drawer

        if (mSideMenu != null) {
            mSideMenu.close();
        }
    }

    private void initSideMenu() {
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

    private void setActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                toolbar,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);

                mSideMenu.close();

                Log.d(this, "onDrawerClosed...");
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                Log.d(this, "onDrawerSlide...");

                super.onDrawerSlide(drawerView, slideOffset);
                if (slideOffset > 0.6) {
                    Log.d(this, "onDrawerSlide, start animation!");
                    mSideMenu.show();
                }
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                Log.d(this, "onDrawerOpened...");
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
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
    }

}
