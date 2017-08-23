package com.assistant.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;


public class CircleIndicatorView extends RelativeLayout {
    private static final String TAG = "CircleIndicatorView";

    private int mColorCircleBk;
    private int mColorText;
    private String mIndicatorText;

    public CircleIndicatorView(Context context) {
        super(context);
    }

    public CircleIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleIndicatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mColorCircleBk = Color.RED;
        mColorText = Color.WHITE;

        mIndicatorText = "1";

        Log.d(TAG, "init");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(mColorCircleBk);

        int width = getWidth();
        int height = getHeight();

        int radius = width > height ? height/2 : width/2;

        Log.d(TAG, "width:" + width + ", height:" + height + ", radius:" + radius);
        canvas.drawCircle(width/2, height/2, radius, paint);

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(mColorText);

        Rect rect = new Rect();

        // calculate text smallest rect
        textPaint.getTextBounds(mIndicatorText, 0, 0, rect);

        float fTextWidth = rect.width();
        float fTextHeight = rect.height();

        Log.d(TAG, "fTextWidth:" + fTextWidth + ", fTextHeight:" + fTextHeight
                + ", mIndicatorText:" + mIndicatorText);

        canvas.drawText(mIndicatorText,
                (width - fTextWidth)/2,
                (height - fTextHeight)/2,
                textPaint);

        super.onDraw(canvas);
    }
}
