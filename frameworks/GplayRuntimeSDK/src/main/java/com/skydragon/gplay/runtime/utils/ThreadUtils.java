package com.skydragon.gplay.runtime.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ThreadUtils {

    private static final String TAG = "ThreadUtils";

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 10;

    private static final Executor THREAD_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS,  new SynchronousQueue<Runnable>());

    private static final Handler sUIThreadHandler = new Handler(Looper.getMainLooper());

    public static void runOnUIThread(Runnable runnable) {
        sUIThreadHandler.postDelayed(runnable, 0);
    }

    public static void runOnUIThread(Runnable r, long delayMillis) {
        sUIThreadHandler.postDelayed(r, delayMillis);
    }

    public static Handler getUIHandler() {
        return sUIThreadHandler;
    }

    public static void runAsyncThread(Runnable runnable) {
        THREAD_POOL_EXECUTOR.execute(runnable);
    }

    public static void printCurrentThreadName(String msg) {
        LogWrapper.d(TAG, msg + ", ThreadName: " + Thread.currentThread().getName());
    }
}
