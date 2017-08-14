package com.assistant.bytestring;

/**
 * Created by alex on 17-8-6.
 */

public class ByteString {
    // use socket default buf size as string default size.
    private static final int DEFAULT_STRING_SIZE = 64*1024;

    private int mSize;

    public byte[] data;

    private int mRefCount = 0;

    public ByteString() {
        this(DEFAULT_STRING_SIZE);
    }

    public ByteString(int initialCapital) {
        mSize = initialCapital;

        data = new byte[mSize];
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
