package com.taka.muzei.imgboard;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.io.File;
import java.io.IOException;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    private static final Logger logger = new Logger(SettingsFragment.class);

    private Config config;

    @Override
    public void onPause(){
        MuzeiBooruApplication.setUpLogging();
        super.onPause();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        config = new Config(getActivity());
        config.fixLogFileInPreferences();

        setPreferencesFromResource(R.xml.pref_general, rootKey);

        setUpClearDbPreference(findPreference("pref_destroy_database"));

        bindPreferenceSummaryToValue(findPreference("tags"));
        bindPreferenceSummaryToValue(findPreference("proxy"));
        bindPreferenceSummaryToValue(findPreference("pref_sort_order"));
        bindPreferenceSummaryToValue(findPreference("pref_refresh_time"));
        bindPreferenceSummaryToValue(findPreference("pref_booru"));
        bindPreferenceSummaryToValue(findPreference("pref_clear_md5"));
        if(BuildConfig.DEBUG) {
            bindPreferenceSummaryToValue(findPreference("log_file"));
            bindPreferenceSummaryToValue(findPreference("last_load_status"));

            setUpLogPreference(findPreference("log_file"));
        } else {
            hidePreference("develop");
        }
    }

    private void setUpClearDbPreference(Preference p) {
        if(null == p)
            return;
        p.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(preference.getContext())
                    .setTitle("Clear Database")
                    .setMessage("Are you sure you want to clear the database?")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        try(Database.DatabaseHelper dbHelper = new Database.DatabaseHelper(preference.getContext());
                            SQLiteDatabase db = dbHelper.getWritableDatabase()){
                            Database.truncateStoredImages(db);
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
            return true;
        });
    }

    private void setUpLogPreference(Preference p) {
        if(null == p)
            return;
        if(p instanceof EditTextLongClickPreference) {
            ((EditTextLongClickPreference)p).setOnLongClickListener(v->{
                logger.i("HERE!");
                final String filename = config.fixLogFileInPreferences();
                if(null != filename) {
                    File file = new File(filename);
                    Uri uri = FileProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID + ".provider", file);

                    Intent intent = new Intent()
                            .setAction(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "text/plain")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                }
                return true;
            });
        }
    }

    private void hidePreference(String key) {
        Preference p = findPreference(key);
        if(null == p)
            return;

        PreferenceScreen screen = getPreferenceScreen();
        screen.removePreference(p);
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        if(null == preference)
            return;
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

        logger.i("Changed preference " + preferenceKey);
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
                        FileUtils.checkWriteAccessToFile(fullPath);
                        stringValue = fullPath;
                    }
                } catch (IOException e) {
                    logger.e("Log file access", e);
                    new AlertDialog.Builder(preference.getContext())
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
