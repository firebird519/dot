package com.assistant.bytestring;


public class ByteStringPool {
    private static ByteStringPool sByteStringPool;

    private static final int MAX_SIZE = 100;

    private int mMaxSize;
    private int mSize;


    ByteString[] pool = new ByteString[MAX_SIZE];

    public static ByteStringPool getInstance() {
        if (sByteStringPool == null) {
            sByteStringPool = new ByteStringPool(5, 100);
        }

        return sByteStringPool;
    }

    private ByteStringPool(int initSize, int maxSize) {
        mSize = initSize;
        mMaxSize = maxSize;

        if (mMaxSize > MAX_SIZE) {
            mMaxSize = MAX_SIZE;
        }

        for (int i = 0; i < mSize; i ++) {
            pool[i] = new ByteString();
        }
    }

    // TODO: to be changed later
    public ByteString getByteString() {
        for (int i = 0; i < MAX_SIZE; i ++) {
            if (pool[i] != null && !pool[i].isInUsing()) {

                pool[i].incRef();
                return pool[i];
            } else if (pool[i] == null) {
                pool[i] = new ByteString();

                pool[i].incRef();
                return pool[i];
            }
        }

        return null;
    }
}
