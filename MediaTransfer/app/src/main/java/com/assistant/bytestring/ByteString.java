package com.assistant.bytestring;

/**
 * Created by alex on 17-8-6.
 */

public class ByteString {
    // use socket default buf size as string default size.
    private static final int DEFAULT_STRING_SIZE = 64*1024;

    public byte[] data = new byte[DEFAULT_STRING_SIZE];

    private boolean mIsInUsing = false;


    public boolean isInUsing() {
        return mIsInUsing;
    }

    public int getBufByteSize() {
        return DEFAULT_STRING_SIZE;
    }

    // TODO: redesign this bytestring
    public void incRef() {
        mIsInUsing = true;
    }

    public void release() {
        mIsInUsing = false;
    }
}
