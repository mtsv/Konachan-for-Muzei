package com.taka.muzei.imgboard;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "logger";
    private static String logFile = null;
    private static boolean loggedFileWriteError = false;
    private static final long maxLofFileSize = 1024*1024;
    private static final SimpleDateFormat sFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US);

    private enum LOG_LEVEL{
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static void setLogFile(String file) {
        logFile = file;
        loggedFileWriteError = false;
        i(TAG, "Log file set to " + (null == logFile ? "NULL" : logFile));
    }

    private String tag;

    public Logger(String tag) {
        this.tag = tag;
    }

    public <T> Logger(Class<T> c) {
        this.tag = c.getSimpleName();
    }

    private static void write(LOG_LEVEL logLevel, String tag, String msg, Throwable tr) {
        final String f = logFile;
        if(null == f)
            return;

        String message = sFormatter.format(new Date()) +
                " [" + logLevel.toString() + "] " +
                (null == tag ? "" : ("[" + tag + "] ")) +
                (null == msg ? "" : msg) +
                (null == tr ? "" : (tr.getMessage() + "\n" + Utils.stacktraceToString(tr))) + "\n";

        writeToFile(f, message);
    }

    private static synchronized void writeToFile(String fileName, String message) {
        File file = new File(fileName);
        final long fileSize = file.length();

        try {
            FileWriter fw = new FileWriter(fileName, (fileSize + message.length()) <= maxLofFileSize);
            try(BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(message);
            }
        } catch (IOException e) {
           if(!loggedFileWriteError) {
               loggedFileWriteError = true;
               Log.e(TAG, "Write to log file " + fileName + " failed", e);
           }
        }
    }

    public void v(String msg) {
        v(tag, msg);
    }

    public void v(String msg, Throwable tr) {
        v(tag, msg, tr);
    }

    public void d(String msg) {
        d(tag, msg);
    }

    public void d(String msg, Throwable tr) {
        d(tag, msg, tr);
    }

    public void i(String msg) {
        i(tag, msg);
    }

    public void i(String msg, Throwable tr) {
        i(tag, msg, tr);
    }

    public void w(String msg) {
        Log.w(tag, msg);
    }

    public void w(Throwable tr) {
        w(tag, tr);
    }

    public void e(String msg) {
        e(tag, msg);
    }

    public void e(String msg, Throwable tr) {
        e(tag, msg, tr);
    }

    public static int v(String tag, String msg) {
        final int r = Log.v(tag, msg);
        write(LOG_LEVEL.VERBOSE, tag, msg, null);
        return r;
    }

    public static int v(String tag, String msg, Throwable tr) {
        final int r = Log.v(tag, msg, tr);
        write(LOG_LEVEL.VERBOSE, tag, msg, tr);
        return r;
    }

    public static int d(String tag, String msg) {
        final int r = Log.d(tag, msg);
        write(LOG_LEVEL.DEBUG, tag, msg, null);
        return r;
    }

    public static int d(String tag, String msg, Throwable tr) {
        final int r = Log.d(tag, msg, tr);
        write(LOG_LEVEL.DEBUG, tag, msg, tr);
        return r;
    }

    public static int i(String tag, String msg) {
        final int r = Log.i(tag, msg);
        write(LOG_LEVEL.INFO, tag, msg, null);
        return r;
    }

    public static int i(String tag, String msg, Throwable tr) {
        final int r = Log.i(tag, msg, tr);
        write(LOG_LEVEL.INFO, tag, msg, tr);
        return r;
    }

    public static int w(String tag, String msg) {
        final int r = Log.w(tag, msg);
        write(LOG_LEVEL.WARN, tag, msg, null);
        return r;
    }

    public static int w(String tag, String msg, Throwable tr) {
        final int r = Log.w(tag, msg, tr);
        write(LOG_LEVEL.WARN, tag, msg, tr);
        return r;
    }

    public static int w(String tag, Throwable tr) {
        final int r = Log.w(tag, tr);
        write(LOG_LEVEL.WARN, tag, null, tr);
        return r;
    }

    public static int e(String tag, String msg) {
        final int r = Log.e(tag, msg);
        write(LOG_LEVEL.ERROR, tag, msg, null);
        return r;
    }

    public static int e(String tag, String msg, Throwable tr) {
        final int r = Log.e(tag, msg, tr);
        write(LOG_LEVEL.ERROR, tag, msg, tr);
        return r;
    }
}
