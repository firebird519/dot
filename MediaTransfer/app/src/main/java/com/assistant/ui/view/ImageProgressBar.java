package com.assistant.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;

import com.assistant.R;

public class ImageProgressBar extends AppCompatImageView {
    private static final int COVER_COLOR_ALPHA = 120;
    private static final int PROGRESS_TEXT_FONT = 80;
    private static final int FLICKER_MAX_TIMES = 5;
    private static final long FLICKER_TIME_STAMP = 500;
    private int mCoverColor;
    private Paint mCoverPaint;
    private Paint mTextPaint;
    private Rect mPaintRect;
    private int mProgress;
    private int mMax;
    private Context mContext;

    // end animation variables
    private ProgressState mProgressState;
    private int mFlickerTimes;
    private boolean mFlickerDisplay;

    private enum ProgressState {
        IDLE, FLICKER, END
    };

    public ImageProgressBar(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
        mContext = context;
        initProgressParams();
    }

    public ImageProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        mContext = context;
        initProgressParams();

        parserSelfAttribute(context, attrs);
    }

    public ImageProgressBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
        mContext = context;
        initProgressParams();

        parserSelfAttribute(context, attrs);
    }

    private void initProgressParams() {
        mProgress = 0;
        mMax = 100;

        mCoverColor = Color.GRAY;
        mPaintRect = new Rect();

        mCoverPaint = new Paint();

        mCoverPaint.setColor(Color.GRAY);
        mCoverPaint.setAlpha(COVER_COLOR_ALPHA);
        mCoverPaint.setXfermode(new PorterDuffXfermode(Mode.DARKEN));

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(PROGRESS_TEXT_FONT);
        mTextPaint.setXfermode(new PorterDuffXfermode(Mode.LIGHTEN));
    }


    private void initProgressFlicker() {
        mProgressState = ProgressState.IDLE;
        mFlickerTimes = 0;
        mFlickerDisplay = false;
    }

    private void parserSelfAttribute(Context context, AttributeSet attrs) {

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ImageProgressBar);

        mProgress = array.getInteger(R.styleable.ImageProgressBar_progress, 0);
        mMax = array.getInteger(R.styleable.ImageProgressBar_max, 100);
        mCoverColor = array.getColor(R.styleable.ImageProgressBar_coverColor, Color.GRAY);
    }

    public void setMax(int max) {
        mMax = max;

        updateProgressParams();
    }

    public void setProgress(int progress) {
        mProgress = progress;

        updateProgressParams();
    }

    public void setCoverColor(int color) {
        mCoverColor = color;

        updateProgressParams();
    }

    private void updateProgressParams() {
        if (mCoverPaint != null && (mCoverPaint.getColor() != mCoverColor)) {
            mCoverPaint.setColor(mCoverColor);
            mCoverPaint.setAlpha(COVER_COLOR_ALPHA);
            mCoverPaint.setXfermode(new PorterDuffXfermode(Mode.DARKEN));
        }

        initProgressFlicker();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);

        if (mCoverPaint != null) {
            int width = getWidth();
            int height = getHeight();

            Log.d("test", "onDraw width:" + width + ", height:" + height);

            int progress = (int) (((height * mProgress) * 1.0f) / mMax);

            mPaintRect.left = 0;
            mPaintRect.right = width;
            mPaintRect.bottom = height;
            mPaintRect.top = progress;

            // canvas.drawARGB(100, 100, 100, 100);
            //Log.d("test", "onDraw mProgress:" + mProgress + ", progress:" + progress);

            //canvas.save();
            canvas.drawRect(mPaintRect, mCoverPaint);

            // draw progress text
            if ((mProgress != 0) && (mProgressState != ProgressState.END)) {
                String sProgress = String.format("%d", mProgress) + "%";
                int tWidth = (int) mTextPaint.measureText(sProgress);
                int x = (width - tWidth) / 2;
                int y = (height + PROGRESS_TEXT_FONT) / 2;

                if (!mFlickerDisplay) {
                    canvas.drawText(sProgress, x, y, mTextPaint);
                }

                if ((mProgress == mMax)
                        && (mProgressState == ProgressState.IDLE)) {
                    mProgressState = ProgressState.FLICKER;
                }

                if (mProgressState == ProgressState.FLICKER) {
                    postInvalidateDelayed(FLICKER_TIME_STAMP);

                    mFlickerDisplay = !mFlickerDisplay;

                    if (mFlickerDisplay) {
                        mFlickerTimes++;

                        if (mFlickerTimes > FLICKER_MAX_TIMES) {
                            mProgressState = ProgressState.END;
                            mFlickerTimes = 0;
                            mFlickerDisplay = false;
                        }
                    }
                }
            }
        }

		/*
		View view = new View(mContext);
		
		LayoutParams params = view.getLayoutParams();
		
		params.height = height;
		params.width = width;
		
		view.setLayoutParams(params);
		
		view.setBackgroundColor(Color.GRAY);
		view.setAlpha(0.5f);
		
		view.draw(canvas);
		
		Bitmap bm = Bitmap.createBitmap(width, height - 100, null);
		
		Paint paint = new Paint();
		paint.setColor(Color.GRAY);
		
		canvas.drawRect(0, 0, width, height - 100, paint);
		*/
    }

}
