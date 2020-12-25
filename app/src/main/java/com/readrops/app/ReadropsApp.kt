package com.readrops.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.readrops.api.apiModule
import com.readrops.app.utils.SharedPreferencesManager
import com.readrops.db.dbModule
import io.reactivex.plugins.RxJavaPlugins
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.io.File
import java.io.IOError
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

open class ReadropsApp : Application() {

    var activityVisible = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(p0: Activity) {
                Log.d("ReadropsApp", "onActivityPaused, setting activityVisible to false")
                activityVisible = false
            }

            override fun onActivityStarted(p0: Activity) {
            }

            override fun onActivityDestroyed(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            }

            override fun onActivityResumed(p0: Activity) {
                Log.d("ReadropsApp", "onActivityResumed, setting activityVisible to true")
                activityVisible = true
            }

        })
        RxJavaPlugins.setErrorHandler { e: Throwable? -> }

        createNotificationChannels()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@ReadropsApp)

            modules(apiModule, dbModule, appModule)
        }

        if (SharedPreferencesManager.readString(SharedPreferencesManager.SharedPrefKey.DARK_THEME).toBoolean())
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val logFile = File(getExternalFilesDir("logs"),
                "logcat_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".log"
        )

        try {
            val process = Runtime.getRuntime().exec("logcat -c")
            Runtime.getRuntime().exec("logcat -f " + logFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val feedsColorsChannel = NotificationChannel(FEEDS_COLORS_CHANNEL_ID,
                    getString(R.string.feeds_colors), NotificationManager.IMPORTANCE_DEFAULT)
            feedsColorsChannel.description = getString(R.string.get_feeds_colors)

            val opmlExportChannel = NotificationChannel(OPML_EXPORT_CHANNEL_ID,
                    getString(R.string.opml_export), NotificationManager.IMPORTANCE_DEFAULT)
            opmlExportChannel.description = getString(R.string.opml_export_description)

            val syncChannel = NotificationChannel(SYNC_CHANNEL_ID,
                    getString(R.string.auto_synchro), NotificationManager.IMPORTANCE_LOW)
            syncChannel.description = getString(R.string.account_synchro)

            val manager = getSystemService(NotificationManager::class.java)!!

            manager.createNotificationChannel(feedsColorsChannel)
            manager.createNotificationChannel(opmlExportChannel)
            manager.createNotificationChannel(syncChannel)
        }
    }

    companion object {
        const val FEEDS_COLORS_CHANNEL_ID = "feedsColorsChannel"
        const val OPML_EXPORT_CHANNEL_ID = "opmlExportChannel"
        const val SYNC_CHANNEL_ID = "syncChannel"
    }

}