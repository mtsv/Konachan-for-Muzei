package com.taka.muzei.imgboard;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity {
    private static final Logger logger = new Logger(SettingsActivity.class);
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_general);
        bindPreferenceSummaryToValue(findPreference("tags"));
        bindPreferenceSummaryToValue(findPreference("proxy"));
        bindPreferenceSummaryToValue(findPreference("pref_sort_order"));
        bindPreferenceSummaryToValue(findPreference("pref_refresh_time"));
        bindPreferenceSummaryToValue(findPreference("pref_booru"));
        bindPreferenceSummaryToValue(findPreference("pref_clear_md5"));
        bindPreferenceSummaryToValue(findPreference("log_file"));
    }
    @Override
    public void onPause(){
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if(prefs.getBoolean("pref_destroy_database", false)){
            Database.DatabaseHelper dbHelper = new Database.DatabaseHelper(this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM images");
            db.close();
            dbHelper.close();
            prefs.edit().putBoolean("pref_destroy_database",false).apply();
        }
        GlobalApplication.setUpLogging();
        super.onPause();
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        String stringValue = value.toString();

        logger.w(preference.getKey());
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);

        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }
}
