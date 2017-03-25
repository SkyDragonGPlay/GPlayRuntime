package com.skydragon.gplay.runtime.utils;

import android.util.Log;

import com.skydragon.gplay.runtime.RuntimeConstants;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class LogWrapper {
    public static final boolean SHOW_LOG = true;
    private static final String LOG_PREFIX = "===> ";

    /**
     * Set an info log message.
     *
     * @param tag Tag for the log message.
     * @param msg Log message to output to the console.
     */
    public static void i(String tag, String msg) {
        if (SHOW_LOG || RuntimeConstants.isDebugRuntimeEnabled()) {
            Log.i(tag, LOG_PREFIX + msg);
        }
    }

    /**
     * Set an error log message.
     *
     * @param tag Tag for the log message.
     * @param msg Log message to output to the console.
     */
    public static void e(String tag, String msg) {
        Log.e(tag, LOG_PREFIX + msg);
    }

    /**
     * Set an error log message.
     *
     * @param tag Tag for the log message.
     * @param e   An exception to log
     */
    public static void e(String tag, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw, true));
        Log.e(tag, LOG_PREFIX + sw.toString());
    }

    /**
     * Set a warning log message.
     *
     * @param tag Tag for the log message.
     * @param msg Log message to output to the console.
     */
    public static void w(String tag, String msg) {
        if (SHOW_LOG || RuntimeConstants.isDebugRuntimeEnabled()) {
            Log.w(tag, LOG_PREFIX + msg);
        }
    }

    /**
     * Set a debug log message.
     *
     * @param tag Tag for the log message.
     * @param msg Log message to output to the console.
     */
    public static void d(String tag, String msg) {
        if (SHOW_LOG || RuntimeConstants.isDebugRuntimeEnabled()) {
            Log.d(tag, LOG_PREFIX + msg);
        }
    }

    /**
     * Set a verbose log message.
     *
     * @param tag Tag for the log message.
     * @param msg Log message to output to the console.
     */
    public static void v(String tag, String msg) {
        if (SHOW_LOG || RuntimeConstants.isDebugRuntimeEnabled()) {
            Log.v(tag, LOG_PREFIX + msg);
        }
    }
}
