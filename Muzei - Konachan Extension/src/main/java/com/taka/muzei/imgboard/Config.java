package com.taka.muzei.imgboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Config {
    private static final Logger logger = new Logger(Config.class);
    private final SharedPreferences prefs;
    private String imagesDir;
    private String wallpaperDir;

    public Config () {
        this(MuzeiBooruApplication.getAppContext());
    }

    public Config (Context context) {
        imagesDir = context.getString(R.string.app_name);
        wallpaperDir = context.getString(R.string.app_name) + " Wallpapers";
        prefs  = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public long getMD5Clear(){return Long.parseLong(prefs.getString("pref_clear_md5","60"));}

    public long getMD5ClearMillis(){return getMD5Clear() * 60 * 1000;}

    public String getBooru(){return prefs.getString("pref_booru","konachan.com");}

    public Boolean prettyflyforaWifi() { return prefs.getBoolean("pref_wifi", false); }

    public String getTags() {return prefs.getString("tags", "");}

    public String getSortType() { return prefs.getString("pref_sort_order","score"); }

    public Boolean getRestrictContentFlag() { return prefs.getBoolean("pref_restrict_content", true); }

    public Uri proxyUrl() {
        final String configProxyString = prefs.getString("proxy", "").trim();
        if(configProxyString.isEmpty()) {
            return null;
        }
        return Uri.parse(configProxyString);
    }

    public int getPostLimit() { return 200; }

    public String getImageStoreDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + imagesDir;
    }

    public String getWallpapersDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + wallpaperDir;
    }

    public String getDefaultLogDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public String constructLogFilePath(String logFileName) {
        if(null == logFileName)
            return null;
        logFileName = logFileName.trim();
        if(logFileName.isEmpty())
            return null;

        if(logFileName.startsWith("/")) // user provided absolute path, use it
            return logFileName;

        return getDefaultLogDirectory() + "/" + logFileName;
    }

    public String getLogFile() {
        final String fileName = prefs.getString("log_file", "").trim();
        if(fileName.isEmpty())
            return null;

        final String logFile = constructLogFilePath(fileName);

        try {
            Utils.checkWriteAccessToFile(logFile);
        } catch (IOException e) {
            return null;
        }

        return logFile;
    }

    public String fixLogFileInPreferences() {
        final String fileName = getLogFile();

        logger.d("Fixed log file path: " + (null == fileName ? "NULL" : fileName));

        prefs.edit().putString("log_file", fileName).apply();
        return fileName;
    }

    public void setLastLoadStatus(boolean ok) {
        String s = "Status: " + (ok ? "OK": "Fail") + "\n" +
                "Source: " + getBooru() + "\n" +
                "Date: " + new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(new Date()) + "\n" +
                "Tags: " + getTags();

        prefs.edit().putString("last_load_status", s).apply();
    }

    public boolean showUserInfo() {
        return BuildConfig.DEBUG;
    }

    public boolean useLocalWallpaper() {
        return false;
    }

    public int getHttpRetryCount() {
        return 3;
    }
}
