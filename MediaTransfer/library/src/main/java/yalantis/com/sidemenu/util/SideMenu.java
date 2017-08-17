package yalantis.com.sidemenu.util;

import android.app.Activity;
import android.os.Handler;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import yalantis.com.sidemenu.R;
import yalantis.com.sidemenu.animation.FlipAnimation;
import yalantis.com.sidemenu.interfaces.Resourceble;

/**
 * Created by Konstantin on 12.01.2015.
 */
public class SideMenu<T extends Resourceble> {
    private static final String TAG = "SideMenu";

    private final int ANIMATION_DURATION = 175;
    public static final int CIRCULAR_REVEAL_ANIMATION_DURATION = 500;

    private Activity mOwnActivity;
  
    private List<T> list;

    private static final int TAG_POSITION_INDEX = 0;

    private List<View> viewList = new ArrayList<>();
    //private ScreenShotable screenShotable;
    private DrawerLayout drawerLayout;
    private ViewAnimatorListener animatorListener;

    private LinearLayout mContainer;

    public SideMenu(Activity activity,
                    List<T> items,
                    //ScreenShotable screenShotable,
                    final DrawerLayout drawerLayout,
                    LinearLayout container,
                    ViewAnimatorListener animatorListener) {
        mOwnActivity = activity;

        list = items;
        this.drawerLayout = drawerLayout;
        this.animatorListener = animatorListener;
        mContainer = container;
    }

    /*
     * To clear container when side menu hidden.
     */
    public void close() {
        mContainer.removeAllViews();
        mContainer.invalidate();
    }

    public void show() {
        if (mContainer.getChildCount() != 0) {
            Log.d(TAG, "show, child of container view is not null." +
                    " side menu should be displaying...");
            return;
        }

        setViewsClickable(false);
        viewList.clear();
        int size = list.size();
        Log.d(TAG, "show, size:" + size);
        for (int i = 0; i < size; i++) {
            // TODO: there is no need to inflater when show every time.
            View viewMenu = mOwnActivity.getLayoutInflater().inflate(R.layout.menu_list_item, null);
            viewMenu.setTag(Integer.valueOf(i));

            viewMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = (Integer)v.getTag();
                    onMenuSelected(list.get(position), position);
                }
            });

            ((ImageView) viewMenu.findViewById(R.id.menu_item_image)).setImageResource(list.get(i).getImageRes());
            viewMenu.setVisibility(View.GONE);
            viewMenu.setEnabled(false);
            viewList.add(viewMenu);
            //animatorListener.addViewToContainer(viewMenu);
            mContainer.addView(viewMenu);
            final int position = i;
            final long delay = 3 * ANIMATION_DURATION * (position / size);
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    Log.d(TAG, "show, position:" + position + ", viewList size:" + viewList.size());
                    if (position < viewList.size()) {
                        animateView((int) position);
                    }
                    if (position == viewList.size() - 1) {
                        //screenShotable.takeScreenShot();
                        setViewsClickable(true);
                    }
                }
            }, delay);
        }
    }

    private void hideMenuContent() {
        setViewsClickable(false);
        double size = list.size();
        for (int i = list.size(); i >= 0; i--) {
            final double position = i;
            final double delay = 3 * ANIMATION_DURATION * (position / size);
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    if (position < viewList.size()) {
                        animateHideView((int) position);
                    }
                }
            }, (long) delay);
        }

    }

    private void setViewsClickable(boolean clickable) {
        animatorListener.disableHomeButton();
        for (View view : viewList) {
            view.setEnabled(clickable);
        }
    }

    private void animateView(final int position) {
        Log.d(TAG, "animateView:" + position);
        final View view = viewList.get(position);
        view.setVisibility(View.VISIBLE);
        FlipAnimation rotation =
                new FlipAnimation(90, 0, 0.0f, view.getHeight() / 2.0f);
        rotation.setDuration(ANIMATION_DURATION);
        rotation.setFillAfter(true);
        rotation.setInterpolator(new AccelerateInterpolator());
        rotation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                Log.d(TAG, "onAnimationStart:" + position);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.clearAnimation();
                Log.d(TAG, "onAnimationEnd:" + position);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                Log.d(TAG, "onAnimationRepeat:" + position);
            }
        });

        view.startAnimation(rotation);
    }

    private void animateHideView(final int position) {
        final View view = viewList.get(position);
        FlipAnimation rotation =
                new FlipAnimation(0, 90, 0.0f, view.getHeight() / 2.0f);
        rotation.setDuration(ANIMATION_DURATION);
        rotation.setFillAfter(true);
        rotation.setInterpolator(new AccelerateInterpolator());
        rotation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.clearAnimation();
                view.setVisibility(View.INVISIBLE);
                if (position == viewList.size() - 1) {
                    animatorListener.enableHomeButton();
                    drawerLayout.closeDrawers();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        view.startAnimation(rotation);
    }

    private void onMenuSelected(Resourceble slideMenuItem, int topPosition) {
        animatorListener.onItemSelected(slideMenuItem, topPosition);
        hideMenuContent();
    }

    public interface ViewAnimatorListener {
        public void onItemSelected(Resourceble slideMenuItem, int position);

        public void disableHomeButton();

        public void enableHomeButton();
    }
}
