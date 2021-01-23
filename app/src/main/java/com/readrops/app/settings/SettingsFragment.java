package com.readrops.app.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.readrops.app.R;
import com.readrops.app.notifications.sync.SyncWorker;
import com.readrops.app.utils.SharedPreferencesManager;
import com.readrops.app.utils.feedscolors.FeedsColorsIntentService;
import com.readrops.db.Database;
import com.readrops.db.logwrapper.Log;

import org.koin.java.KoinJavaComponent;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;

import static com.readrops.app.utils.ReadropsKeys.FEEDS;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);

        Preference feedsColorsPreference = findPreference("reload_feeds_colors");
        Preference themePreference = findPreference("dark_theme");
        Preference synchroPreference = findPreference("auto_synchro");


        AtomicBoolean serviceStarted = new AtomicBoolean(false);
        feedsColorsPreference.setOnPreferenceClickListener(preference -> {
            Database database = KoinJavaComponent.get(Database.class);

            database.feedDao().getAllFeeds().observe(getActivity(), feeds -> {
                if (!serviceStarted.get()) {
                    Intent intent = new Intent(getContext(), FeedsColorsIntentService.class);
                    intent.putParcelableArrayListExtra(FEEDS, new ArrayList<>(feeds));

                    getContext().startService(intent);
                    serviceStarted.set(true);
                }
            });

            return true;
        });

        themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean darkTheme = Boolean.parseBoolean(newValue.toString());

            if (darkTheme) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }

            return true;
        });

        synchroPreference.setOnPreferenceChangeListener(((preference, newValue) -> {
            rescheduleSynchroWork(getContext(), (String) newValue);

            return true;
        }));
    }

    public static void rescheduleSynchroWork(Context context, String optionalIntervalString) {
        String intervalString;
        if (optionalIntervalString == null) {
            intervalString = SharedPreferencesManager.readString(SharedPreferencesManager.SharedPrefKey.AUTO_SYNCHRO);
        } else {
            intervalString = optionalIntervalString;
        }

        Pair<Integer, TimeUnit> interval = getWorkerInterval(intervalString);
        WorkManager workManager = WorkManager.getInstance(context);
        if (interval != null) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, interval.first, interval.second)
                    .addTag(SyncWorker.Companion.getTAG())
                    .setConstraints(constraints)
                    .setInitialDelay(interval.first, interval.second)
                    .build();

            workManager.enqueueUniquePeriodicWork(SyncWorker.Companion.getTAG(), ExistingPeriodicWorkPolicy.REPLACE, request);
            Log.d(SyncWorker.Companion.getTAG(), "reschedule work to " + interval.first + interval.second.toString() + " later");
        } else {
            workManager.cancelAllWorkByTag(SyncWorker.Companion.getTAG());
        }
    }

    @Nullable
    private static Pair<Integer, TimeUnit> getWorkerInterval(String newValue) {
        int interval;
        TimeUnit timeUnit;

        switch (newValue) {
            case "0.30":
                interval = 30;
                timeUnit = TimeUnit.MINUTES;
                break;
            case "1":
                interval = 1;
                timeUnit = TimeUnit.HOURS;
                break;
            case "2":
                interval = 2;
                timeUnit = TimeUnit.HOURS;
                break;
            case "3":
                interval = 3;
                timeUnit = TimeUnit.HOURS;
                break;
            case "6":
                interval = 6;
                timeUnit = TimeUnit.HOURS;
                break;
            case "12":
                interval = 12;
                timeUnit = TimeUnit.HOURS;
                break;
            case "24":
                interval = 1;
                timeUnit = TimeUnit.DAYS;
                break;
            default:
                return null;
        }

        return new Pair<>(interval, timeUnit);
    }

}
