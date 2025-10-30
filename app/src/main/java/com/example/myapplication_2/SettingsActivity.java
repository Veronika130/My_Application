package com.example.myapplication_2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_TREE_URI    = "pref_tree_uri";
    public static final String KEY_MAX_MINUTES = "pref_max_minutes";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private ActivityResultLauncher<Uri> pickDirLauncher;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            EditTextPreference max = findPreference(KEY_MAX_MINUTES);
            if (max != null) {
                if (TextUtils.isEmpty(max.getText())) {
                    max.setText("10");
                }
                max.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int mins = Integer.parseInt(String.valueOf(newValue));
                        return mins >= 1 && mins <= 240;
                    } catch (Exception e) {
                        return false;
                    }
                });
            }


            pickDirLauncher = registerForActivityResult(
                    new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri != null) {
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                            getPreferenceManager().getSharedPreferences()
                                    .edit()
                                    .putString(KEY_TREE_URI, uri.toString())
                                    .apply();
                            Preference pick = findPreference("pref_pick_dir");
                            if (pick != null) pick.setSummary(uri.toString());
                        }
                    }
            );
            Preference pick = findPreference("pref_pick_dir");
            if (pick != null) {
                String savedUri = getPreferenceManager().getSharedPreferences()
                        .getString(KEY_TREE_URI, null);
                if (savedUri != null) {
                    pick.setSummary(savedUri);
                }

                pick.setOnPreferenceClickListener(p -> {
                    pickDirLauncher.launch(null);
                    return true;
                });
            }
        }
    }
}
