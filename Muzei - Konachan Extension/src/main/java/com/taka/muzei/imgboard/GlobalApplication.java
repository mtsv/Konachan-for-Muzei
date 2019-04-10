package com.taka.muzei.imgboard;

import android.app.Application;
import android.content.Context;

// class to gain access to context from anywhere
public class GlobalApplication extends Application {
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context getAppContext() {
        return appContext;
    }

    public static void setUpLogging() {
        final Config config = new Config(GlobalApplication.getAppContext());
        final String logFilePath = config.getLogFile();
        Logger.setLogFile(logFilePath);
    }
}
