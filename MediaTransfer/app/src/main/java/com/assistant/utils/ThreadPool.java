package com.assistant.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * To manager threads 
 */
public class ThreadPool {
    private static final String TAG = "ThreadPool";
    private static final int MIN_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 10;

    private int poolSize;

    private ExecutorService mExecutor;

    private boolean mIsShutdown;

    public ThreadPool(int size) {
        if (size < MIN_POOL_SIZE) size = MIN_POOL_SIZE;
        if (size > MAX_POOL_SIZE) size = MAX_POOL_SIZE;
        poolSize = size;

        mExecutor = Executors.newFixedThreadPool(poolSize);
        mIsShutdown = false;
    }

    /**
     * add one task to thread pool.
     *
     * @param task
     */
    public synchronized void addTask(Runnable task) {
        mExecutor.submit(task);
    }

    /**
     * stop the thread pool. Because thread pool maybe pending for shutdown.
     * Create one thread to handling this.
     */
    public synchronized void stop() {
        if (mIsShutdown) {
            return;
        }

        mIsShutdown = true;
        new Thread(new Runnable() {

            @Override
            public void run() {
                shutdownAndAwaitTermination();
            }
        }).start();
    }

    private void shutdownAndAwaitTermination() {
        Log.log(TAG, "shutdownAndAwaitTermination:" + System.currentTimeMillis());
        synchronized (this) {
            mExecutor.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!mExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    mExecutor.shutdownNow(); // Cancel currently executing
                    // tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!mExecutor.awaitTermination(5, TimeUnit.SECONDS))
                        mExecutor.shutdownNow();
                    System.err.println("Pool did not terminate?");
                }

            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                mExecutor.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }

            mIsShutdown = false;
        }

        Log.log(TAG, "shutdownAndAwaitTermination end:" + System.currentTimeMillis());
    }
}