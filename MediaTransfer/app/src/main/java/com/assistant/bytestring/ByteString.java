package com.assistant.bytestring;

import com.assistant.utils.Log;

public class ByteString {
    private static final String TAG = "ByteString";

    private final Object mObjLock = new Object();
    // use socket default buf size as string default size.
    public static final int DEFAULT_STRING_SIZE = 4*1024;

    private int mSize;

    // To make sure to do lock by mObjLock if necessary.
    public byte[] data;
    private int mLen;

    private int mRefCount = 0;

    public ByteString() {
        this(DEFAULT_STRING_SIZE);
    }

    public ByteString(int initialCapital) {
        mSize = initialCapital;

        data = new byte[mSize];
        mLen = 0;
    }

    public int putData(byte[] bytes) {
        synchronized (mObjLock) {
            if (bytes.length <= mSize) {
                System.arraycopy(bytes, 0, data, 0, bytes.length);
                mLen = bytes.length;
                return mLen;
            }
        }

        return 0;
    }

    public boolean isInUsing() {
        return mRefCount > 0;
    }

    public int getBufSize() {
        return mSize;
    }

    public void incRef() {
        mRefCount ++;
    }

    public void release() {
        mRefCount --;

        if (mRefCount < 0) {
            mRefCount = 0;
        } else if (mRefCount == 0) {
            mLen = 0;
        }
    }

    public Object getLockObject() {
        return mObjLock;
    }

    public void setDataLen(int len) {
        mLen = len;
    }

    /**
     * To one UTF-8 string
     * @return
     */
    public synchronized String toString() {
        try {
            synchronized (mObjLock) {
                return new String(data, 0, mLen, "UTF-8");
            }
        } catch (Exception e) {
            Log.d(TAG,"toString: Exception happened!" + e.getMessage());
            return "";
        }
    }
}
