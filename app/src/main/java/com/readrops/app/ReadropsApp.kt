package com.readrops.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.bosphere.filelogger.FL
import com.bosphere.filelogger.FLConfig
import com.bosphere.filelogger.FLConst
import com.readrops.api.apiModule
import com.readrops.app.notifications.sync.SyncWorker
import com.readrops.app.settings.SettingsFragment
import com.readrops.app.utils.SharedPreferencesManager
import com.readrops.db.dbModule
import com.readrops.db.logwrapper.Log
import io.reactivex.plugins.RxJavaPlugins
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class ReadropsLogFormatter : FLConfig.DefaultFormatter() {
    private val mDate: ThreadLocal<Date> = object : ThreadLocal<Date>() {
        override fun initialValue(): Date {
            return Date()
        }
    }
    private val mReadropsFilenameDate: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
    }

    override fun formatFileName(timeInMillis: Long): String {
        mDate.get()!!.time = timeInMillis
        return "filelogger_" + mReadropsFilenameDate.get()!!.format(mDate.get()!!) + ".log"
    }
}

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
                Log.d("ReadropsApp", "onActivityResumed, setting activityVisible to true and reschedule work")
                activityVisible = true
                SettingsFragment.rescheduleSynchroWork(p0, null)
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

        FL.init(FLConfig.Builder(this)
                .minLevel(FLConst.Level.V)
                .logToFile(true)
                .dir(getExternalFilesDir("logs"))
                .retentionPolicy(FLConst.RetentionPolicy.NONE)
                .formatter(ReadropsLogFormatter())
                .build()
        )
        FL.setEnabled(true)

        WorkManager.initialize(
                this,
                Configuration.Builder()
                        .setExecutor(Executors.newSingleThreadExecutor())
                        .build()
        )

        val workManager = WorkManager.getInstance(this)
        val workTag = SyncWorker.TAG
        FL.i("ReadropsApp", "workTag: $workTag")
        val workInfos = workManager.getWorkInfosByTag(workTag).get()
        FL.i("ReadropsApp", "workInfos: $workInfos")
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