package com.assistant.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.assistant.utils.Log;
import com.assistant.utils.Utils;

import com.assistant.R;

public class CircleIndicatorView extends View {
    private static final String TAG = "CircleIndicatorView";

    private Paint mCirclePaint;
    private int mCircleBkColor = Color.RED;

    private TextPaint mTextPaint;
    private int mTextColor = Color.WHITE;
    private int mTextSize = 30;
    private String mIndicatorText = "";

    private int mTextRectWidth;
    private int mTextRectHeight;

    public CircleIndicatorView(Context context) {
        super(context);
    }

    public CircleIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleIndicatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CircleIndicatorView, defStyle, 0);
        int n = a.getIndexCount();
        for (int i = 0; i < n; i++)
        {
            int attr = a.getIndex(i);
            switch (attr)
            {
                case R.styleable.CircleIndicatorView_text:
                    mIndicatorText = a.getString(attr);
                    break;
                case R.styleable.CircleIndicatorView_bkColor:
                    mCircleBkColor = a.getColor(attr, Color.RED);
                    break;
                case R.styleable.CircleIndicatorView_textColor:
                    mTextColor = a.getColor(attr, Color.WHITE);
                    break;
                case R.styleable.CircleIndicatorView_textSize:
                    // 默认设置为30sp，TypeValue也可以把sp转化为px
                    mTextSize = a.getDimensionPixelSize(attr, (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP, 30, getResources().getDisplayMetrics()));
                    break;
            }
        }
        a.recycle();

        init();
    }

    private void init() {
        mCirclePaint = new Paint();
        mCirclePaint.setColor(mCircleBkColor);

        mTextPaint = new TextPaint();
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTextSize(mTextSize);

        if (Utils.DEBUG_CONNECTION) {
            setText("1");
        }
    }

    private void CalTextSize() {
        mTextRectWidth = (int) mTextPaint.measureText(mIndicatorText);

        mTextRectHeight = (int)(mTextPaint.descent() + mTextPaint.ascent());
    }

    public void setText(String text) {
        mIndicatorText = text;

        CalTextSize();

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        CalTextSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        int radius = width > height ? height/2 : width/2;

        mCirclePaint.setAlpha((int)(255*getAlpha()));

        Log.d(TAG, "width:" + width + ", height:" + height + ", radius:" + radius);
        canvas.drawCircle(width/2, height/2, radius, mCirclePaint);

        Log.d(TAG, "mTextRectWidth:" + mTextRectWidth + ", mTextRectHeight:" + mTextRectHeight
                + ", mIndicatorText:" + mIndicatorText);

        if (!TextUtils.isEmpty(mIndicatorText)) {
            mTextPaint.setAlpha((int)(255*getAlpha()));
            canvas.drawText(mIndicatorText,
                    (width - mTextRectWidth) / 2,
                    (height - mTextRectHeight) / 2,
                    mTextPaint);
        }

        super.onDraw(canvas);
    }
}
