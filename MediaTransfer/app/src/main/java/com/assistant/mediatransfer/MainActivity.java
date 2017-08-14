package com.assistant.mediatransfer;

import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import yalantis.com.sidemenu.interfaces.Resourceble;
import yalantis.com.sidemenu.model.SlideMenuItem;

import mediatransfer.assistant.com.mediatransfer.R;
import yalantis.com.sidemenu.util.ViewAnimator;

public class MainActivity extends AppCompatActivity {

    private List<SlideMenuItem> mMenuItemList = new ArrayList<>();
    private ViewAnimator mViewAnimator;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayout mLinearLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mLinearLayout = (LinearLayout) findViewById(R.id.left_drawer);

        mViewAnimator = new ViewAnimator<>(this, mMenuItemList, mDrawerLayout, new ViewAnimator.ViewAnimatorListener() {
            @Override
            public void onSwitch(Resourceble slideMenuItem, int position) {

            }

            @Override
            public void disableHomeButton() {

            }

            @Override
            public void enableHomeButton() {

            }

            @Override
            public void addViewToContainer(View view) {

            }
        });

        createMenuList();
        setActionBar();
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
                mLinearLayout.removeAllViews();
                mLinearLayout.invalidate();
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, slideOffset);
                if (slideOffset > 0.6 && mLinearLayout.getChildCount() == 0)
                    mViewAnimator.showMenuContent();
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }
}
