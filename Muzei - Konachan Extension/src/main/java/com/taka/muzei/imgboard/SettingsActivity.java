package com.taka.muzei.imgboard;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;

import java.io.IOException;

public class SettingsActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private static final Logger logger = new Logger(SettingsActivity.class);
    private Config config;
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        config = new Config(this);

        config.fixLogFileInPreferences();

        addPreferencesFromResource(R.xml.pref_general);
        bindPreferenceSummaryToValue(findPreference("tags"));
        bindPreferenceSummaryToValue(findPreference("proxy"));
        bindPreferenceSummaryToValue(findPreference("pref_sort_order"));
        bindPreferenceSummaryToValue(findPreference("pref_refresh_time"));
        bindPreferenceSummaryToValue(findPreference("pref_booru"));
        bindPreferenceSummaryToValue(findPreference("pref_clear_md5"));
        bindPreferenceSummaryToValue(findPreference("log_file"));

        updateLastLoadStatus();
    }

    private void updateLastLoadStatus() {
        final String lastLoadStatus = android.preference.PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString("last_load_status", "");
        if(!lastLoadStatus.isEmpty()) {
            Preference statusPref = findPreference("last_load_status");
            statusPref.setSummary(lastLoadStatus);
        }
    }

    @Override
    public void onPause(){
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if(prefs.getBoolean("pref_destroy_database", false)){
            try(Database.DatabaseHelper dbHelper = new Database.DatabaseHelper(this);
                SQLiteDatabase db = dbHelper.getWritableDatabase()){
                Database.truncateStoredImages(db);
            }
            prefs.edit().putBoolean("pref_destroy_database",false).apply();
        }
        GlobalApplication.setUpLogging();
        super.onPause();
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);

        // Trigger the listener immediately with the preference's
        // current value.
        this.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String stringValue = newValue.toString();

        final String preferenceKey = preference.getKey();

        logger.i(preferenceKey);
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);

        } else {
            if(preferenceKey.equals("log_file")) {
                try {
                    final String fullPath = config.constructLogFilePath(stringValue);
                    if(null != fullPath) {
                        Utils.checkWriteAccessToFile(fullPath);
                        stringValue = fullPath;
                    }
                } catch (IOException e) {
                    logger.e("Log file access", e);
                    new AlertDialog.Builder(this)
                            .setTitle("Log file access error")
                            .setMessage("Can not set log file to " + stringValue + "\n" + e.getMessage())
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                    return false;
                }
            }
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    }
}
