package com.demo.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.demo.utils.Log;

/**
 * Created by liyong on 17-7-13.
 */

public class CpuRateView extends View {
    private static final String TAG = CpuRateView.class.getName();
    private static final boolean DEBUG = false;

    private Paint mRectPaint;
    private Paint mCoordinatePaint;

    private int mWidth, mHeight;

    private Path mRectPath;
    private Path mCoordinatePath;

    private Paint[] mRatePaint;
    private Path[] mRatePath;

    private int mCpuCount;
    private int[][] mCpuRates;
    private int[] mCpuCurRatePos;
    private int mDisplayRateCount = 0;

    private static final int RATE_SPACE = 10;

    private static final int MAX_COLOR_NUM = 18;
    private int[] mColors = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.BLACK,
            Color.DKGRAY,
            Color.CYAN,
            Color.YELLOW,
            Color.LTGRAY,
            0xff00ff00,
            0x00ffff00,
            0x88000000,
            0x00880000,
            0x00008800,
            0x88880000,
            0x88008800,
            0x00888800,
            0x88888800,
            0x44000000,
            0x00440000,
            0x00004400,
            0x44444400
    };

    public CpuRateView(Context context, AttributeSet attrs) {
        super(context, attrs);

        initPaint();
    }

    private void initPaint() {
        mRectPaint = new Paint();
        mRectPaint.setColor(Color.GRAY);
        mRectPaint.setStrokeWidth(4.0f);
        mRectPaint.setStyle(Paint.Style.STROKE);

        mCoordinatePaint = new Paint();
        mCoordinatePaint.setColor(Color.GRAY);
        mCoordinatePaint.setStrokeWidth(2.0f);
        mCoordinatePaint.setStyle(Paint.Style.STROKE);

        mCoordinatePaint.setPathEffect ( new DashPathEffect( new float [ ] { 3, 2 }, 0 ) );
    }

    private void initCoordinateData() {
        if (mRectPath == null) {
            mRectPath = new Path();
        }

        mRectPath.reset();
        // upright
        mRectPath.moveTo(1,1);
        mRectPath.lineTo(1, mHeight);
        mRectPath.lineTo(mWidth, mHeight);

        // coordinate
        if (mCoordinatePath == null) {
            mCoordinatePath = new Path();
        }

        mCoordinatePath.reset();
        // transverse
        mCoordinatePath.moveTo(1,1);
        mCoordinatePath.lineTo(mWidth, 1);

        mCoordinatePath.rMoveTo(0, mHeight/2);
        mCoordinatePath.lineTo(1, mHeight/2);


        // upright
        mCoordinatePath.rMoveTo(mWidth/4, mHeight);
        mCoordinatePath.lineTo(mWidth/4, 1);

        mCoordinatePath.rMoveTo(mWidth/4, 0);
        mCoordinatePath.lineTo(mWidth/2, mHeight);

        mCoordinatePath.rMoveTo(mWidth/4, 0);
        mCoordinatePath.lineTo(mWidth*3/4,1);
    }

    public void setCpuCount(int count) {
        mCpuCount = count;
        mRatePaint = new Paint[count];
        mRatePath = new Path[count];
        mCpuRates = new int[count][];
        mCpuCurRatePos = new int[count];

        for (int i = 0 ; i < count ; i ++) {
            mRatePaint[i] = new Paint();
            mRatePaint[i].setStrokeWidth(2.0f);
            mRatePaint[i].setStyle(Paint.Style.STROKE);

            if (i < MAX_COLOR_NUM) {
                mRatePaint[i].setColor(mColors[i]);
            }

            mRatePath[i] = new Path();
        }

        initCpuRateInfo();
    }

    private void initCpuRateInfo() {
        int rateCount = mWidth / RATE_SPACE + 1;

        if (mDisplayRateCount >= rateCount) {
            return;
        }

        for (int i = 0; i < mCpuCount; i++) {
            int[] preRate = mCpuRates[i];
            mCpuRates[i] = new int[rateCount];

            // init cycle rate array
            for (int r = 0; r < rateCount; r++) {
                mCpuRates[i][r] = 0;
            }

            if (preRate != null) {
                for(int j = 0, index = mCpuCurRatePos[i] + 1; j < mDisplayRateCount; j ++, index ++) {
                    if (index >= mDisplayRateCount) {
                        index = 0;
                    }
                    mCpuRates[i][j] = preRate[index];
                }
            }

            mCpuCurRatePos[i] = 0;

            refreshCpuRatePath(i);
        }

        mDisplayRateCount = rateCount;
    }

    public synchronized void addCpuRate(int index, int rate) {
        if (index >= mCpuCount) {
            Log.d(TAG, "addCpuRate, cpu index is invalid. cpu count:" +
                    mCpuCount + ", rate:" + rate);
            return;
        }

        if (mCpuRates == null || mCpuRates[index] == null || mCpuCurRatePos == null) {
            Log.d(TAG, "addCpuRate, cpu rates data is not init?");
            return;
        }

        int pos = mCpuCurRatePos[index];

        mCpuRates[index][pos] = rate;

        log("path for cpu" + index + ", " + "pos:" + pos + ", rate:" + mCpuRates[index][pos]);

        pos ++;

        if (pos >= mDisplayRateCount) {
            pos = 0;
        }
        mCpuCurRatePos[index] = pos;

        refreshCpuRatePath(index);

        invalidate();
    }

    private synchronized void refreshCpuRatePath(int index) {
        if (mRatePath == null) {
            Log.d(TAG, "refreshCpuRatePath, cpu rates data is not init?");
            return;
        }
        Path path = mRatePath[index];

        path.reset();

        int pos = mCpuCurRatePos[index];

        int prevX = 0;
        int preY = mHeight - (mCpuRates[index][pos] * mHeight)/100;

        StringBuilder log = new StringBuilder();

        int cpuPosOffset = index;
        int cX = 0;
        int cY = preY;
        path.moveTo(cX, cY);


        for(int i = 1; i < mDisplayRateCount; i ++) {
            pos ++;
            if (pos >= mDisplayRateCount) {
                pos = 0;
            }

            cX = i * RATE_SPACE;
            cY = mHeight - (mCpuRates[index][pos]*mHeight)/100;

            int offset = 5;

            if (cY < preY) {
                offset = -5;
            }

            int tX1 = prevX + RATE_SPACE/5 + offset;
            int tY1 = (cY - preY) / 5 + preY + offset;

            int tX2 = prevX + (RATE_SPACE * 4)/5 + cpuPosOffset;
            int tY2 = ((cY - preY) * 4)/ 5 + preY + offset;

            if (cY == preY) {
                tX1 = tX2 = cX;
                tY1 = tY2 = cY;
            }

            path.quadTo((prevX + cX)/2 + offset, (cY + preY)/2 - offset, cX + cpuPosOffset, cY - cpuPosOffset);
            //path.lineTo(cX, cY);
            //path.cubicTo(tX1, tY1, tX2, tY2, cX, cY);

            prevX = cX;
            preY = cY;

            log.append(cX);
            log.append(",");
            log.append(cY);
            log.append("-");
            log.append(mCpuRates[index][pos]);
            log.append(" ");
        }

        log("refreshCpuRatePath, cpu" + index + ": " + log.toString());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();

        initCoordinateData();
        initCpuRateInfo();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // rect
        canvas.drawPath(mRectPath, mRectPaint);

        // coordinate
        canvas.drawPath(mCoordinatePath, mCoordinatePaint);

        if (mCpuCount > 0) {
            log("draw rate");
            for(int i = 0 ; i < mCpuCount; i ++) {
                canvas.drawPath(mRatePath[i], mRatePaint[i]);
            }
        }
    }

    private void log(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }
}
