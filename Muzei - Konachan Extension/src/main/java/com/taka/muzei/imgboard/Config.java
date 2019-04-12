package com.taka.muzei.imgboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Config {
    private final SharedPreferences prefs;
    private String imagesDir = "Muzei - Konachan";
    private Context context;

    public Config (Context context) {
        this.context = context;
        prefs  = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public int getTimeSet(){
        return Integer.parseInt(prefs.getString("pref_refresh_time", "90"));
    }

    public long getMD5Clear(){return Long.parseLong(prefs.getString("pref_clear_md5","60"));}

    public long getMD5ClearMillis(){return getMD5Clear() * 60 * 1000;}

    public String getBooru(){return prefs.getString("pref_booru","konachan.com");}

    public Boolean prettyflyforaWifi() { return prefs.getBoolean("pref_wifi", false); }

    public String getTags() {return prefs.getString("tags", "");}

    public String getSortType() { return prefs.getString("pref_sort_order","score"); }

    public Boolean getRestrictContentFlag() { return prefs.getBoolean("pref_restrict_content", true); }

    public int getRotateTimeMillis() {
        int configTime = getTimeSet();
        return configTime * 60 * 1000;
    }

    public Uri proxyUrl() {
        String configProxyString = prefs.getString("proxy", "").trim();
        if(configProxyString.isEmpty()) {
            return null;
        }
        return Uri.parse(configProxyString);
    }

    public int getPostLimit() { return 100; }

    public String getImageStoreDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + imagesDir;
    }

    public String getLogFile() {
        final String fileName = prefs.getString("log_file", "").trim();
        if(fileName.isEmpty())
            return null;

        //return GlobalApplication.getAppContext().getFilesDir().getAbsolutePath() + "/" +  Utils.cleanFileName(fileName);
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + imagesDir + "/" + Utils.cleanFileName(fileName);
    }

    public void setLastLoadStatus(boolean ok) {
        String s = "Status: " + (ok ? "OK": "Fail") +
                "; Source: " + getBooru() +
                "; Date: " + new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(new Date()) +
                "; Tags: " + getTags();

        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("last_load_status", s).apply();
    }
}
