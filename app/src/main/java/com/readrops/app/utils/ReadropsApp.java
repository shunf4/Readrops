package com.readrops.app.utils;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.facebook.stetho.Stetho;
import com.readrops.app.BuildConfig;
import com.readrops.app.R;

import io.reactivex.plugins.RxJavaPlugins;

public class ReadropsApp extends Application {

    public static final String FEEDS_COLORS_CHANNEL_ID = "feedsColorsChannel";

    @Override
    public void onCreate() {
        super.onCreate();

        RxJavaPlugins.setErrorHandler(e -> { });

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel feedsColorsChannel = new NotificationChannel(FEEDS_COLORS_CHANNEL_ID,
                    getString(R.string.feeds_colors), NotificationManager.IMPORTANCE_DEFAULT);
            feedsColorsChannel.setDescription(getString(R.string.get_feeds_colors));

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(feedsColorsChannel);
        }
    }
}
