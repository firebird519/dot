package com.assistant.bytestring;

/**
 * Created by alex on 17-8-6.
 */

public class ByteString {
    // use socket default buf size as string default size.
    public static final int DEFAULT_STRING_SIZE = 64*1024;

    private int mSize;

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
        if (bytes.length <= mSize) {
            System.arraycopy(bytes, 0, data, 0, bytes.length);
            mLen = bytes.length;
            return mLen;
        }

        return 0;
    }

    public boolean isInUsing() {
        return mRefCount > 0;
    }

    public int getBufByteSize() {
        return mSize;
    }

    public void incRef() {
        mRefCount ++;
    }

    public void release() {
        mRefCount --;
    }
}
